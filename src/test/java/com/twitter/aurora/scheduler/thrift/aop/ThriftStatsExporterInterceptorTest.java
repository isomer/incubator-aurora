/*
 * Copyright 2013 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.aurora.scheduler.thrift.aop;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.matcher.Matchers;

import org.junit.Before;
import org.junit.Test;

import com.twitter.aurora.gen.AuroraAdmin;
import com.twitter.aurora.gen.GetJobsResult;
import com.twitter.aurora.gen.JobConfiguration;
import com.twitter.aurora.gen.Response;
import com.twitter.aurora.gen.Result;
import com.twitter.aurora.scheduler.thrift.auth.DecoratedThrift;
import com.twitter.common.stats.Stats;
import com.twitter.common.testing.easymock.EasyMockTest;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import static com.twitter.aurora.gen.ResponseCode.OK;

public class ThriftStatsExporterInterceptorTest extends EasyMockTest {

  private static final String ROLE = "bob";

  private AuroraAdmin.Iface realThrift;
  private AuroraAdmin.Iface decoratedThrift;
  private ThriftStatsExporterInterceptor statsInterceptor;

  @Before
  public void setUp() {
    statsInterceptor = new ThriftStatsExporterInterceptor();
    realThrift = createMock(AuroraAdmin.Iface.class);
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        MockDecoratedThrift.bindForwardedMock(binder(), realThrift);
        AopModule.bindThriftDecorator(
            binder(),
            Matchers.annotatedWith(DecoratedThrift.class),
            statsInterceptor);
      }
    });
    decoratedThrift = injector.getInstance(AuroraAdmin.Iface.class);
  }

  @Test
  public void testIncrementStat() throws Exception {
    Response response = new Response().setResponseCode(OK)
        .setResult(Result.getJobsResult(new GetJobsResult()
        .setConfigs(ImmutableSet.<JobConfiguration>of())));

    expect(realThrift.getJobs(ROLE)).andReturn(response);
    control.replay();

    assertSame(response, decoratedThrift.getJobs(ROLE));
    assertNotNull(Stats.getVariable("scheduler_thrift_getJobs_events"));
    assertNotNull(Stats.getVariable("scheduler_thrift_getJobs_events_per_sec"));
    assertNotNull(Stats.getVariable("scheduler_thrift_getJobs_nanos_per_event"));
    assertNotNull(Stats.getVariable("scheduler_thrift_getJobs_nanos_total"));
    assertNotNull(Stats.getVariable("scheduler_thrift_getJobs_nanos_total_per_sec"));
  }
}
