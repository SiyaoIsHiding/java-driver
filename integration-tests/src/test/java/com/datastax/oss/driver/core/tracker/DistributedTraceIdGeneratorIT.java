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
package com.datastax.oss.driver.core.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.testinfra.ccm.CcmRule;
import com.datastax.oss.driver.api.testinfra.session.SessionUtils;
import com.datastax.oss.driver.categories.ParallelizableTests;
import java.nio.ByteBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

@Category(ParallelizableTests.class)
public class DistributedTraceIdGeneratorIT {
  private CcmRule ccmRule = CcmRule.getInstance();

  @Rule public TestRule chain = RuleChain.outerRule(ccmRule);

  @Test
  public void should_write_default_id_to_custom_payload_with_key() {
    DriverConfigLoader loader =
        SessionUtils.configLoaderBuilder()
            .withString(DefaultDriverOption.DISTRIBUTED_TRACE_ID_CUSTOM_PAYLOAD_KEY, "trace_key")
            .build();
    try (CqlSession session = SessionUtils.newSession(ccmRule, loader)) {
      String query = "SELECT * FROM system.local";
      ResultSet rs = session.execute(query);
      assertThat(rs.getExecutionInfo().getRequest().getCustomPayload().get("trace_key"))
          .isNotNull();
    }
  }

  @Test
  public void should_write_uuid_to_custom_payload_with_key() {
    DriverConfigLoader loader =
        SessionUtils.configLoaderBuilder()
            .withString(
                DefaultDriverOption.DISTRIBUTED_TRACE_ID_GENERATOR_CLASS,
                "UuidDistributedTraceIdGenerator")
            .withString(DefaultDriverOption.DISTRIBUTED_TRACE_ID_CUSTOM_PAYLOAD_KEY, "trace_key")
            .build();
    try (CqlSession session = SessionUtils.newSession(ccmRule, loader)) {
      String query = "SELECT * FROM system.local";
      ResultSet rs = session.execute(query);
      ByteBuffer id = rs.getExecutionInfo().getRequest().getCustomPayload().get("trace_key");
      assertThat(id.array().length).isEqualTo(73);
    }
  }

  @Test
  public void should_write_w3c_context_to_custom_payload_with_key() {
    DriverConfigLoader loader =
        SessionUtils.configLoaderBuilder()
            .withString(
                DefaultDriverOption.DISTRIBUTED_TRACE_ID_GENERATOR_CLASS,
                "W3CContextDistributedTraceIdGenerator")
            .withString(DefaultDriverOption.DISTRIBUTED_TRACE_ID_CUSTOM_PAYLOAD_KEY, "trace_key")
            .build();
    try (CqlSession session = SessionUtils.newSession(ccmRule, loader)) {
      String query = "SELECT * FROM system.local";
      ResultSet rs = session.execute(query);
      ByteBuffer id = rs.getExecutionInfo().getRequest().getCustomPayload().get("trace_key");
      assertThat(id.array().length).isEqualTo(55);
    }
  }

  @Test
  public void should_not_write_id_to_custom_payload_when_key_is_not_set() {
    DriverConfigLoader loader = SessionUtils.configLoaderBuilder().build();
    try (CqlSession session = SessionUtils.newSession(ccmRule, loader)) {
      String query = "SELECT * FROM system.local";
      ResultSet rs = session.execute(query);
      assertThat(rs.getExecutionInfo().getRequest().getCustomPayload().get("trace_key")).isNull();
    }
  }
}
