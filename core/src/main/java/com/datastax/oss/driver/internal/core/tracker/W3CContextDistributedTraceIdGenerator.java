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

import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.session.Request;
import com.datastax.oss.driver.api.core.tracker.DistributedTraceIdGenerator;
import com.datastax.oss.driver.shaded.guava.common.io.BaseEncoding;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.SecureRandom;
import java.util.Random;

public class W3CContextDistributedTraceIdGenerator implements DistributedTraceIdGenerator {
  Random random = new SecureRandom();
  BaseEncoding baseEncoding = BaseEncoding.base16().lowerCase();

  public W3CContextDistributedTraceIdGenerator(DriverContext context) {}

  @Override
  public String getSessionRequestId(
      @NonNull Request statement, @NonNull String sessionName, int hashCode) {
    byte[] bytes = new byte[16];
    random.nextBytes(bytes);
    return baseEncoding.encode(bytes);
  }

  @Override
  public String getNodeRequestId(
      @NonNull Request statement, @NonNull String sessionRequestId, int executionCount) {
    byte[] bytes = new byte[8];
    random.nextBytes(bytes);
    return String.format("00-%s-%s-00", sessionRequestId, baseEncoding.encode(bytes));
  }
}
