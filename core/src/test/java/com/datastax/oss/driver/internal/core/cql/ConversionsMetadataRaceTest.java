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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.DefaultProtocolVersion;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.time.TimestampGenerator;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.datastax.oss.driver.internal.core.DefaultConsistencyLevelRegistry;
import com.datastax.oss.driver.internal.core.ProtocolFeature;
import com.datastax.oss.driver.internal.core.ProtocolVersionRegistry;
import com.datastax.oss.driver.internal.core.context.InternalDriverContext;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableList;
import com.datastax.oss.protocol.internal.Message;
import com.datastax.oss.protocol.internal.ProtocolConstants;
import com.datastax.oss.protocol.internal.response.result.ColumnSpec;
import com.datastax.oss.protocol.internal.response.result.DefaultRows;
import com.datastax.oss.protocol.internal.response.result.RawType;
import com.datastax.oss.protocol.internal.response.result.Rows;
import com.datastax.oss.protocol.internal.response.result.RowsMetadata;
import com.datastax.oss.protocol.internal.util.Bytes;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Deterministic regression tests for the SKIP_METADATA / CASSANDRA-10786 race: a bound statement is
 * encoded into message 1 while the prepared statement's result metadata is OLD_DEFS, then a
 * concurrent execution's response replaces that metadata with NEW_DEFS before message 1's own
 * SKIP_METADATA response is decoded.
 *
 * <p>NEW_DEFS models a real {@code ALTER TABLE ... ADD extra_0 bigint}: Cassandra returns non-
 * primary-key columns in alphabetical order, so "extra_0" is inserted at index 0, shifting the
 * existing columns one slot to the right. Decoding message 1's old-layout row bytes against
 * NEW_DEFS would corrupt them.
 *
 * <p>The fix keeps the definitions that each message was encoded with on the message itself (see
 * {@link BoundExecute}), and decodes the response against those.
 */
public class ConversionsMetadataRaceTest {

  private static final ByteBuffer OLD_RESULT_METADATA_ID = Bytes.fromHexString("0x01");
  private static final ByteBuffer NEW_RESULT_METADATA_ID = Bytes.fromHexString("0x02");

  private InternalDriverContext context;
  private DriverExecutionProfile config;
  private ColumnDefinitions oldDefs;
  private ColumnDefinitions newDefs;

  @Before
  public void setup() {
    context = Mockito.mock(InternalDriverContext.class);
    Mockito.when(context.getCodecRegistry()).thenReturn(CodecRegistry.DEFAULT);
    Mockito.when(context.getProtocolVersion()).thenReturn(DefaultProtocolVersion.V4);
    Mockito.when(context.getConsistencyLevelRegistry())
        .thenReturn(new DefaultConsistencyLevelRegistry());
    Mockito.when(context.getTimestampGenerator())
        .thenReturn(Mockito.mock(TimestampGenerator.class));
    ProtocolVersionRegistry protocolVersionRegistry = Mockito.mock(ProtocolVersionRegistry.class);
    Mockito.when(
            protocolVersionRegistry.supports(
                any(ProtocolVersion.class), any(ProtocolFeature.class)))
        .thenReturn(true);
    Mockito.when(context.getProtocolVersionRegistry()).thenReturn(protocolVersionRegistry);

    config = Mockito.mock(DriverExecutionProfile.class);
    Mockito.when(config.getString(DefaultDriverOption.REQUEST_CONSISTENCY))
        .thenReturn(DefaultConsistencyLevel.LOCAL_ONE.name());
    Mockito.when(config.getInt(DefaultDriverOption.REQUEST_PAGE_SIZE)).thenReturn(5000);
    Mockito.when(config.getString(DefaultDriverOption.REQUEST_SERIAL_CONSISTENCY))
        .thenReturn(DefaultConsistencyLevel.SERIAL.name());

    oldDefs =
        Conversions.toColumnDefinitions(
            new RowsMetadata(
                ImmutableList.of(
                    columnSpec("group_id", 0, ProtocolConstants.DataType.VARCHAR),
                    columnSpec("is_deleted", 1, ProtocolConstants.DataType.BOOLEAN),
                    columnSpec("last_modified", 2, ProtocolConstants.DataType.BIGINT)),
                null,
                new int[0],
                null),
            context);
    newDefs =
        Conversions.toColumnDefinitions(
            new RowsMetadata(
                ImmutableList.of(
                    columnSpec("extra_0", 0, ProtocolConstants.DataType.BIGINT),
                    columnSpec("group_id", 1, ProtocolConstants.DataType.VARCHAR),
                    columnSpec("is_deleted", 2, ProtocolConstants.DataType.BOOLEAN),
                    columnSpec("last_modified", 3, ProtocolConstants.DataType.BIGINT)),
                null,
                new int[0],
                null),
            context);
  }

  @Test
  public void should_encode_id_and_definitions_from_the_same_metadata() {
    DefaultPreparedStatement preparedStatement = preparedStatement(oldDefs);
    BoundStatement statement = preparedStatement.bind();

    BoundExecute message1 = (BoundExecute) Conversions.toMessage(statement, config, context);
    assertThat(message1.resultMetadataId).isEqualTo(Bytes.getArray(OLD_RESULT_METADATA_ID));
    assertThat(message1.getResultSetDefinitions()).isEqualTo(oldDefs);
    assertThat(message1.options.skipMetadata).isTrue();

    preparedStatement.setResultMetadata(NEW_RESULT_METADATA_ID, newDefs);

    // The same statement encoded again picks up the new metadata, without affecting message 1
    BoundExecute message2 = (BoundExecute) Conversions.toMessage(statement, config, context);
    assertThat(message2.resultMetadataId).isEqualTo(Bytes.getArray(NEW_RESULT_METADATA_ID));
    assertThat(message2.getResultSetDefinitions()).isEqualTo(newDefs);
    assertThat(message1.getResultSetDefinitions()).isEqualTo(oldDefs);
  }

  @Test
  public void should_decode_skip_metadata_response_against_definitions_of_its_own_message() {
    DefaultPreparedStatement preparedStatement = preparedStatement(oldDefs);
    // One bound statement, encoded into two messages (e.g. a speculative execution, or the same
    // statement executed twice concurrently), both while OLD_DEFS is current
    BoundStatement statement = preparedStatement.bind();
    Message message1 = Conversions.toMessage(statement, config, context);
    Message message2 = Conversions.toMessage(statement, config, context);

    // Response 2 lands first: the server signals a schema change, and decoding it replaces the
    // prepared statement's metadata with NEW_DEFS
    Rows rows2 =
        new DefaultRows(
            new RowsMetadata(
                ImmutableList.of(
                    columnSpec("extra_0", 0, ProtocolConstants.DataType.BIGINT),
                    columnSpec("group_id", 1, ProtocolConstants.DataType.VARCHAR),
                    columnSpec("is_deleted", 2, ProtocolConstants.DataType.BOOLEAN),
                    columnSpec("last_modified", 3, ProtocolConstants.DataType.BIGINT)),
                null,
                new int[0],
                Bytes.getArray(NEW_RESULT_METADATA_ID)),
            oneRow(
                TypeCodecs.BIGINT.encode(0L, DefaultProtocolVersion.V4),
                TypeCodecs.TEXT.encode("g1", DefaultProtocolVersion.V4),
                TypeCodecs.BOOLEAN.encode(true, DefaultProtocolVersion.V4),
                TypeCodecs.BIGINT.encode(1_700_000_000_000L, DefaultProtocolVersion.V4)));
    ColumnDefinitions defs2 = Conversions.getResultDefinitions(rows2, message2, statement, context);
    assertThat(defs2.get(2).getName().asInternal()).isEqualTo("is_deleted");
    assertThat(preparedStatement.getResultMetadataId()).isEqualTo(NEW_RESULT_METADATA_ID);

    // Response 1 arrives now: SKIP_METADATA, row bytes laid out per OLD_DEFS
    Rows rows1 =
        new DefaultRows(
            new RowsMetadata(oldDefs.size(), null, new int[0], null),
            oneRow(
                TypeCodecs.TEXT.encode("g2", DefaultProtocolVersion.V4),
                TypeCodecs.BOOLEAN.encode(false, DefaultProtocolVersion.V4),
                TypeCodecs.BIGINT.encode(1_700_000_000_001L, DefaultProtocolVersion.V4)));
    ColumnDefinitions defs1 = Conversions.getResultDefinitions(rows1, message1, statement, context);

    // Decoded against what message 1 was encoded with, not the prepared statement's current copy
    assertThat(defs1).isEqualTo(oldDefs);
    Row row = new DefaultRow(defs1, rows1.getData().poll(), context);
    assertThat(row.getBoolean("is_deleted")).isFalse();
    assertThat(row.getLong("last_modified")).isEqualTo(1_700_000_000_001L);
  }

  @Test
  public void should_fail_if_skip_metadata_response_column_count_does_not_match() {
    DefaultPreparedStatement preparedStatement = preparedStatement(oldDefs);
    BoundStatement statement = preparedStatement.bind();
    Message message = Conversions.toMessage(statement, config, context);

    // The server encoded the rows with 4 columns (e.g. after an ALTER TABLE on protocol v4, which
    // has no result metadata id), but the message was encoded with 3
    Rows rows =
        new DefaultRows(
            new RowsMetadata(oldDefs.size() + 1, null, new int[0], null), new ArrayDeque<>());

    assertThatThrownBy(() -> Conversions.getResultDefinitions(rows, message, statement, context))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("4 columns")
        .hasMessageContaining("3 columns");
  }

  @Test
  public void should_keep_definitions_of_non_default_prepared_statement_on_message() {
    // A PreparedStatement implementation that isn't DefaultPreparedStatement, e.g. a wrapper
    PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
    Mockito.when(preparedStatement.getId()).thenReturn(Bytes.fromHexString("0xAAAA"));
    Mockito.when(preparedStatement.getResultMetadataId()).thenReturn(OLD_RESULT_METADATA_ID);
    Mockito.when(preparedStatement.getResultSetDefinitions()).thenReturn(oldDefs);
    BoundStatement statement = Mockito.mock(BoundStatement.class);
    Mockito.when(statement.getPreparedStatement()).thenReturn(preparedStatement);

    Message message = Conversions.toMessage(statement, config, context);

    Mockito.when(preparedStatement.getResultMetadataId()).thenReturn(NEW_RESULT_METADATA_ID);
    Mockito.when(preparedStatement.getResultSetDefinitions()).thenReturn(newDefs);

    Rows rows =
        new DefaultRows(
            new RowsMetadata(oldDefs.size(), null, new int[0], null), new ArrayDeque<>());
    assertThat(Conversions.getResultDefinitions(rows, message, statement, context))
        .isEqualTo(oldDefs);
  }

  @Test
  public void should_use_prepared_statement_definitions_if_request_is_unknown() {
    DefaultPreparedStatement preparedStatement = preparedStatement(oldDefs);
    BoundStatement statement = preparedStatement.bind();

    Rows rows =
        new DefaultRows(
            new RowsMetadata(oldDefs.size(), null, new int[0], null), new ArrayDeque<>());
    assertThat(Conversions.getResultDefinitions(rows, statement, context)).isEqualTo(oldDefs);
  }

  private static DefaultPreparedStatement preparedStatement(
      ColumnDefinitions resultSetDefinitions) {
    return new DefaultPreparedStatement(
        Bytes.fromHexString("0xAAAA"),
        "SELECT * FROM t2 WHERE k = ?",
        DefaultColumnDefinitions.valueOf(Collections.emptyList()),
        Collections.emptyList(),
        OLD_RESULT_METADATA_ID,
        resultSetDefinitions,
        null,
        Collections.emptyMap(),
        null,
        null,
        null,
        null,
        null,
        Collections.emptyMap(),
        null,
        null,
        null,
        5000,
        null,
        null,
        false,
        CodecRegistry.DEFAULT,
        DefaultProtocolVersion.V4);
  }

  private static ColumnSpec columnSpec(String name, int index, int protocolDataType) {
    return new ColumnSpec("race_test", "t2", name, index, RawType.PRIMITIVES.get(protocolDataType));
  }

  private static Queue<List<ByteBuffer>> oneRow(ByteBuffer... values) {
    Queue<List<ByteBuffer>> data = new ArrayDeque<>();
    data.add(ImmutableList.copyOf(values));
    return data;
  }
}
