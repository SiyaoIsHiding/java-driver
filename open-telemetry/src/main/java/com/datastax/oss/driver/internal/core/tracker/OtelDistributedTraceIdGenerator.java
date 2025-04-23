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
import edu.umd.cs.findbugs.annotations.NonNull;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OtelDistributedTraceIdGenerator implements DistributedTraceIdGenerator {
  private final Logger LOG =
      LoggerFactory.getLogger(OtelDistributedTraceIdGenerator.class.getName());
  private final DistributedTraceIdGenerator defaultDelegate;

  public OtelDistributedTraceIdGenerator(DriverContext context) {
    this.defaultDelegate = new DefaultDistributedTraceIdGenerator(context);
  }

  @Override
  public String getSessionRequestId(
      @NonNull Request statement, @NonNull String sessionName, int hashCode) {
    String id = getTraceParent();
    if (id == null) {
      return defaultDelegate.getSessionRequestId(statement, sessionName, hashCode);
    }
    return id.substring(3, 35);
  }

  @Override
  public String getNodeRequestId(
      @NonNull Request statement, @NonNull String sessionRequestId, int executionCount) {
    String id = getTraceParent();
    if (id == null) {
      return defaultDelegate.getNodeRequestId(statement, sessionRequestId, executionCount);
    }
    return getTraceParent();
  }

  private String getTraceParent() {
    AtomicReference<String> id = new AtomicReference<>();
    TextMapSetter<AtomicReference<String>> contextSetter =
        (carrier, key, value) -> {
          LOG.info("Carrier {} key {} value {}", carrier, key, value);
          if (key.equals("traceparent")) {
            id.set(value);
          }
        };
    Span.current();
    W3CTraceContextPropagator.getInstance().inject(Context.current(), id, contextSetter);
    return id.get();
  }
}
