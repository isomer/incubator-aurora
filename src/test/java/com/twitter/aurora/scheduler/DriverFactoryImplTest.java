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
package com.twitter.aurora.scheduler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Properties;

import javax.inject.Provider;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.base.Throwables;

import org.apache.mesos.Scheduler;
import org.junit.Before;
import org.junit.Test;

import com.twitter.aurora.scheduler.DriverFactory.DriverFactoryImpl;
import com.twitter.common.testing.easymock.EasyMockTest;

import static org.junit.Assert.assertEquals;

public class DriverFactoryImplTest extends EasyMockTest {

  @Test(expected=IllegalStateException.class)
  public void testMissingPropertiesParsing() {
    Properties testProperties = new Properties();
    testProperties.put(DriverFactoryImpl.PRINCIPAL_KEY, "aurora-scheduler");

    ByteArrayOutputStream propertiesStream = new ByteArrayOutputStream();
    try {
      testProperties.store(propertiesStream, "");
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    control.replay();
    DriverFactoryImpl.parseCredentials(new ByteArrayInputStream(propertiesStream.toByteArray()));
  }

  @Test
  public void testPropertiesParsing() {
    Properties testProperties = new Properties();
    testProperties.put(DriverFactoryImpl.PRINCIPAL_KEY, "aurora-scheduler");
    testProperties.put(DriverFactoryImpl.SECRET_KEY, "secret");

    ByteArrayOutputStream propertiesStream = new ByteArrayOutputStream();
    try {
      testProperties.store(propertiesStream, "");
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    control.replay();
    assertEquals(testProperties,
        DriverFactoryImpl.parseCredentials(
            new ByteArrayInputStream(propertiesStream.toByteArray())));
  }
}
