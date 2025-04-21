/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.driver.internal.core.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.internal.core.context.InternalDriverContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Strict.class)
public class DistributedTraceIdGeneratorTest {
  @Mock private InternalDriverContext context;
  @Mock private Statement<?> statement;

  @Test
  public void default_generator_should_generate() {
    // given
    DefaultDistributedTraceIdGenerator generator = new DefaultDistributedTraceIdGenerator(context);
    // when
    String sessionRequestId = generator.getSessionRequestId(statement, "sessionName", 123);
    String nodeRequestId = generator.getNodeRequestId(statement, sessionRequestId, 1);
    // then
    assertThat(sessionRequestId).isEqualTo("sessionName|123");
    assertThat(nodeRequestId).isEqualTo("sessionName|123|1");
  }

  @Test
  public void uuid_generator_should_generate() {
    // given
    UuidDistributedTraceIdGenerator generator = new UuidDistributedTraceIdGenerator(context);
    // when
    String sessionRequestId = generator.getSessionRequestId(statement, "sessionName", 123);
    String nodeRequestId = generator.getNodeRequestId(statement, sessionRequestId, 1);
    // then
    assertThat(sessionRequestId.length()).isEqualTo(36);
    assertThat(nodeRequestId.length()).isEqualTo(73);
  }

  @Test
  public void w3c_generator_should_generate() {
    // given
    W3CContextDistributedTraceIdGenerator generator =
        new W3CContextDistributedTraceIdGenerator(context);
    // when
    String sessionRequestId = generator.getSessionRequestId(statement, "sessionName", 123);
    String nodeRequestId = generator.getNodeRequestId(statement, sessionRequestId, 1);
    // then
    assertThat(sessionRequestId.length()).isEqualTo(32);
    assertThat(nodeRequestId.length()).isEqualTo(55);
  }
}
