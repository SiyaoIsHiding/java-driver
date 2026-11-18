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
package com.datastax.oss.driver.internal.core.cql;

import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.protocol.internal.request.Execute;
import com.datastax.oss.protocol.internal.request.query.QueryOptions;
import edu.umd.cs.findbugs.annotations.NonNull;
import net.jcip.annotations.Immutable;

/**
 * An EXECUTE request for a bound statement, that also remembers the result set definitions it was
 * encoded with (those that {@link #resultMetadataId} identifies).
 *
 * <p>If the request sets SKIP_METADATA, the response has no metadata, and must be decoded against
 * these definitions. They can't be read from the prepared statement at that point, because a
 * concurrent execution's response might have replaced its copy in the meantime (see
 * CASSANDRA-10786).
 *
 * <p>This is encoded exactly like a regular {@link Execute}: the extra field is client-side only.
 */
@Immutable
public class BoundExecute extends Execute {

  private final ColumnDefinitions resultSetDefinitions;

  public BoundExecute(
      byte[] queryId,
      byte[] resultMetadataId,
      QueryOptions options,
      @NonNull ColumnDefinitions resultSetDefinitions) {
    super(queryId, resultMetadataId, options);
    this.resultSetDefinitions = resultSetDefinitions;
  }

  @NonNull
  public ColumnDefinitions getResultSetDefinitions() {
    return resultSetDefinitions;
  }
}
