package com.twitter.nexus.scheduler;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.nexus.gen.JobConfiguration;
import com.twitter.nexus.gen.ScheduleStatus;
import com.twitter.nexus.gen.TaskQuery;
import com.twitter.nexus.gen.TrackedTask;
import com.twitter.nexus.gen.TwitterTaskInfo;
import com.twitter.nexus.scheduler.configuration.ConfigurationManager;
import com.twitter.nexus.scheduler.persistence.NoPersistence;
import nexus.SlaveOffer;
import nexus.StringMap;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * Unit test for the SchedulerCore.
 *
 * @author wfarner
 */
public class SchedulerCoreTest {
  private SchedulerCore scheduler;

  private static final long ONE_GB = Amount.of(1L, Data.GB).getValue();

  private static final String JOB_NAME_A = "Test_Job_A";
  private static final String JOB_OWNER_A = "Test_Owner_A";
  private static final TwitterTaskInfo TASK_A = defaultTask();

  private static final String JOB_NAME_B = "Test_Job_B";
  private static final String JOB_OWNER_B = "Test_Owner_B";

  private static final int SLAVE_ID = 5;

  @Before
  public void setUp() {
    scheduler = new SchedulerCoreImpl(new CronJobManager(), new ImmediateJobManager(),
        new NoPersistence());
  }

  @Test
  public void testCreateJob() throws Exception {
    int numTasks = 10;
    JobConfiguration job = makeJob(JOB_OWNER_A, JOB_NAME_A, TASK_A, numTasks);
    scheduler.createJob(job);
    assertTaskCount(numTasks);

    Iterable<TrackedTask> tasks = scheduler.getTasks(
        new TaskQuery().setOwner(JOB_OWNER_A).setJobName(JOB_NAME_A));
    assertThat(Iterables.size(tasks), is(numTasks));
    for (TrackedTask task : tasks) {
      assertThat(task.getStatus(), is(ScheduleStatus.PENDING));
      assertThat(task.isSetTaskId(), is(true));
      assertThat(task.isSetSlaveId(), is(false));
      assertThat(task.getTask(), is(ConfigurationManager.populateFields(job, TASK_A)));
    }
  }

  @Test
  public void testCreateDuplicateJob() throws Exception {
    scheduler.createJob(makeJob(JOB_OWNER_A, JOB_NAME_A, TASK_A, 1));
    assertTaskCount(1);

    try {
      scheduler.createJob(makeJob(JOB_OWNER_A, JOB_NAME_A, TASK_A, 1));
      fail();
    } catch (ScheduleException e) {
      // Expected
    }

    assertTaskCount(1);
  }

  @Test
  public void testCreateDuplicateCronJob() throws Exception {
    // Cron jobs are scheduled on a delay, so this job's tasks will not be scheduled immediately,
    // but duplicate jobs should still be rejected.
    scheduler.createJob(makeJob(JOB_OWNER_A, JOB_NAME_A, TASK_A, 1)
        .setCronSchedule("* * * * *"));
    assertTaskCount(0);

    try {
      scheduler.createJob(makeJob(JOB_OWNER_A, JOB_NAME_A, TASK_A, 1));
      fail();
    } catch (ScheduleException e) {
      // Expected
    }

    assertTaskCount(0);
  }

  @Test
  public void testJobLifeCycle() throws Exception {
    int numTasks = 10;
    scheduler.createJob(makeJob(JOB_OWNER_A, JOB_NAME_A, TASK_A, numTasks));

    assertTaskCount(numTasks);

    /**
     * TODO(wfarner): Complete this once constructing a SlaveOffer object doesn't require swig.
    TaskDescription desc = scheduler.offer(
        makeOffer(SLAVE_ID, 1, ONE_GB.as(Data.BYTES)));
    assertThat(desc, is(not(null)));
    assertThat(desc.getSlaveId(), is(SLAVE_ID));

    TwitterTaskInfo taskInfo = new TwitterTaskInfo();
    new TDeserializer().deserialize(taskInfo, desc.getArg());
    assertThat(taskInfo, is(taskObj));
     */

    // TODO(wfarner): Complete.
  }

  @Test
  public void testDaemonTasksRescheduled() throws Exception {
    // Schedule 5 daemon and 5 non-daemon tasks.
    scheduler.createJob(makeJob(JOB_OWNER_A, JOB_NAME_A, TASK_A, 5));
    TwitterTaskInfo task = TASK_A;
    task.setConfiguration(Maps.newHashMap(task.getConfiguration()));
    task.putToConfiguration("daemon", "true");
    scheduler.createJob(makeJob(JOB_OWNER_A, JOB_NAME_A + "daemon", TASK_A, 5));

    assertThat(Iterables.size(scheduler.getTasks(
        new TaskQuery().setStatuses(Sets.newHashSet(ScheduleStatus.PENDING)))),
        is(10));

    scheduler.setTaskStatus(new TaskQuery().setOwner(JOB_OWNER_A), ScheduleStatus.STARTING);
    assertThat(Iterables.size(scheduler.getTasks(
        new TaskQuery().setStatuses(Sets.newHashSet(ScheduleStatus.STARTING)))),
        is(10));

    scheduler.setTaskStatus(new TaskQuery().setOwner(JOB_OWNER_A), ScheduleStatus.RUNNING);
    assertThat(Iterables.size(scheduler.getTasks(
        new TaskQuery().setStatuses(Sets.newHashSet(ScheduleStatus.RUNNING)))),
        is(10));

    // Daemon tasks will move back into PENDING state after finishing.
    scheduler.setTaskStatus(new TaskQuery().setOwner(JOB_OWNER_A), ScheduleStatus.FINISHED);
    assertThat(Iterables.size(scheduler.getTasks(
        new TaskQuery().setStatuses(Sets.newHashSet(ScheduleStatus.PENDING)))),
        is(5));
    assertTaskCount(5);
  }

  @Test
  public void testCronJobLifeCycle() {
    // TODO(wfarner): Figure out how to test the lifecycle of a cron job.
  }

  @Test
  public void testKillJob() throws Exception {
    scheduler.createJob(makeJob(JOB_OWNER_A, JOB_NAME_A, TASK_A, 10));
    assertTaskCount(10);

    scheduler.killTasks(new TaskQuery().setOwner(JOB_OWNER_A).setJobName(JOB_NAME_A));
    assertTaskCount(0);
  }

  @Test
  public void testKillJob2() throws Exception {
    scheduler.createJob(makeJob(JOB_OWNER_A, JOB_NAME_A, TASK_A, 10));
    assertTaskCount(10);

    scheduler.createJob(makeJob(JOB_OWNER_A, JOB_NAME_A + "2", TASK_A, 10));
    assertTaskCount(20);

    scheduler.killTasks(new TaskQuery().setOwner(JOB_OWNER_A).setJobName(JOB_NAME_A + "2"));
    assertTaskCount(10);

    for (TrackedTask task : scheduler.getTasks(new TaskQuery())) {
      assertThat(task.getJobName(), is(JOB_NAME_A));
    }
  }

  private void assertTaskCount(int numTasks) {
    assertThat(Iterables.size(scheduler.getTasks(new TaskQuery())), is(numTasks));
  }

  private static JobConfiguration makeJob(String owner, String jobName, TwitterTaskInfo task,
      int numTasks) {
    JobConfiguration job = new JobConfiguration();
    job.setOwner(owner)
        .setName(jobName);
    for (int i = 0; i < numTasks; i++) {
      job.addToTaskConfigs(new TwitterTaskInfo(task));
    }

    return job;
  }

  private static TwitterTaskInfo defaultTask() {
    return new TwitterTaskInfo().setConfiguration(ImmutableMap.<String, String>builder()
        .put("cpus", "1.0")
        .put("ram_bytes", Long.toString(ONE_GB))
        .put("hdfs_path", "/fake/path")
        .build());
  }

  private static SlaveOffer makeOffer(int slaveId, int cpus, long ramBytes) {
    SlaveOffer offer = new SlaveOffer();
    offer.setSlaveId(slaveId);
    offer.setHost("Host_" + slaveId);
    StringMap params = new StringMap();
    params.set("cpus", String.valueOf(cpus));
    params.set("mem", String.valueOf(ramBytes));
    offer.setParams(params);
    return offer;
  }
}