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
package com.twitter.aurora.scheduler.http;

import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import com.twitter.aurora.gen.HostAttributes;
import com.twitter.aurora.gen.MaintenanceMode;
import com.twitter.aurora.scheduler.base.Query;
import com.twitter.aurora.scheduler.base.Tasks;
import com.twitter.aurora.scheduler.storage.Storage;
import com.twitter.aurora.scheduler.storage.Storage.StoreProvider;
import com.twitter.aurora.scheduler.storage.Storage.Work;
import com.twitter.aurora.scheduler.storage.entities.IScheduledTask;

import static com.twitter.aurora.gen.MaintenanceMode.DRAINED;
import static com.twitter.aurora.gen.MaintenanceMode.DRAINING;
import static com.twitter.aurora.gen.MaintenanceMode.SCHEDULED;

/**
 * Servlet that exposes the maintenance state of hosts.
 */
@Path("/maintenance")
public class Maintenance {
  private final Storage storage;

  @Inject
  Maintenance(Storage storage) {
    this.storage = Preconditions.checkNotNull(storage);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response getHosts() {
    return storage.weaklyConsistentRead(new Work.Quiet<Response>() {
      @Override public Response apply(StoreProvider storeProvider) {
        Multimap<MaintenanceMode, String> hostsByMode =
            Multimaps.transformValues(
              Multimaps.index(storeProvider.getAttributeStore().getHostAttributes(), GET_MODE),
              HOST_NAME);

        Map<MaintenanceMode, Object> hosts = Maps.newHashMap();
        hosts.put(DRAINED, ImmutableSet.copyOf(hostsByMode.get(DRAINED)));
        hosts.put(SCHEDULED, ImmutableSet.copyOf(hostsByMode.get(SCHEDULED)));
        hosts.put(DRAINING, getTasksByHosts(storeProvider, hostsByMode.get(DRAINING)).asMap());
        return Response.ok(hosts).build();
      }
    });
  }

  private Multimap<String, String> getTasksByHosts(StoreProvider provider, Iterable<String> hosts) {
    ImmutableSet.Builder<IScheduledTask> drainingTasks = ImmutableSet.builder();
    for (String host : hosts) {
      drainingTasks.addAll(provider.getTaskStore().fetchTasks(Query.slaveScoped(host).active()));
    }
    return Multimaps.transformValues(
        Multimaps.index(drainingTasks.build(), TASK_TO_HOST),
        Tasks.SCHEDULED_TO_ID);
  }

  private static final Function<IScheduledTask, String> TASK_TO_HOST =
      new Function<IScheduledTask, String>() {
        @Override public String apply(IScheduledTask task) {
          return task.getAssignedTask().getSlaveHost();
        }
      };

  private static final Function<HostAttributes, String> HOST_NAME =
      new Function<HostAttributes, String>() {
        @Override public String apply(HostAttributes attributes) {
          return attributes.getHost();
        }
      };

  private static final Function<HostAttributes, MaintenanceMode> GET_MODE =
      new Function<HostAttributes, MaintenanceMode>() {
        @Override public MaintenanceMode apply(HostAttributes attrs) {
          return attrs.getMode();
        }
      };
}
