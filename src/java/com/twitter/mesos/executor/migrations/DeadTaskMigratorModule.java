package com.twitter.mesos.executor.migrations;

import com.google.inject.AbstractModule;

import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.mesos.executor.migrations.v0_v1.OwnerMigrator;

/**
 * Binds a dead task migrator that optionally does no work if the command line flag
 * {@code -no_executor_upgrade_dead_tasks} is specified.
 *
 * @author John Sirois
 */
public class DeadTaskMigratorModule extends AbstractModule {

  @CmdLine(name = "executor_upgrade_dead_tasks",
      help ="True to migrate dead task structs stored on disk.")
  private static final Arg<Boolean> upgradeDeadTasks = Arg.create(true);

  @Override protected void configure() {
    if (upgradeDeadTasks.get()) {
      bind(DeadTaskMigrator.class).to(OwnerMigrator.class);
    } else {
      bind(DeadTaskMigrator.class).toInstance(DeadTaskMigrator.NOOP);
    }
  }
}