- Feature Name: Add OpenTelemetry Traces
- Start Date: 2024-12-05

# Summary
[summary]: #summary

[OpenTelemetry](https://opentelemetry.io/docs/what-is-opentelemetry/) is a collection of APIs, SDKs, and tools used to instrument, generate, collect, and export telemetry data (metrics, logs, and traces) to help the analysis of software’s performance and behavior.
This document describes the necessary steps to include OpenTelemetry tracing in Apache Cassandra Java driver.

# Motivation
[motivation]: #motivation

OpenTelemetry is the industry standard regarding telemetry data that aggregates logs, metrics, and traces. Specifically regarding traces, it allows the developers to understand the full "path" a request takes in the application and navigate through the service(s).

[OpenTelemetry Instrumentation for Java](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/cassandra/cassandra-4.4/library) already supports auto instrumentation of Apache Cassandra Java Driver when run as a Java agent. It supports metrics, logs, and basic traces. This proposal to support tracing in the native Apache Cassandra Java Driver will save the users from the need to run the Java agent and will expose more detailed information including Cassandra calls.

# Guide-level explanation
[guide-level-explanation]: #guide-level-explanation

## [Traces](https://opentelemetry.io/docs/concepts/signals/traces/)

As mentioned in [*Motivation*](#motivation), traces allows the developers to understand the full "path" a request takes in the application and navigate through a service. Traces include [Spans](https://opentelemetry.io/docs/concepts/signals/traces/#spans) which are unit of works or operation in the ecosystem that include the following information:

- Name
- Parent span ID (empty for root spans)
- Start and End Timestamps
- [Span Context](https://opentelemetry.io/docs/concepts/signals/traces/#span-context)
- [Attributes](https://opentelemetry.io/docs/concepts/signals/traces/#attributes)
- [Span Events](https://opentelemetry.io/docs/concepts/signals/traces/#span-events)
- [Span Links](https://opentelemetry.io/docs/concepts/signals/traces/#span-links)
- [Span Status](https://opentelemetry.io/docs/concepts/signals/traces/#span-status)

The spans can be correlated with each other and assembled into a trace using [context propagation](https://opentelemetry.io/docs/concepts/signals/traces/#context-propagation).

### Example of a trace in a microservice architecture

![grafana](./grafana.png)

## OpenTelemetry Semantic Conventions
[opentelemetry-semantic-conventions]: #opentelemetry-semantic-conventions

### Span name

[OpenTelemetry Trace Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/general/trace/) (at the time of this writing, it's on version 1.29.0) defines multipurpose semantic conventions regarding tracing for different components and protocols (e.g.: Database, HTTP, Messaging, etc.)

For Apache Cassandra Java driver, the focus is the [semantic conventions for database client calls](https://opentelemetry.io/docs/specs/semconv/database/database-spans/) for the generic database attributes, and the [semantic conventions for Cassandra](https://opentelemetry.io/docs/specs/semconv/database/cassandra/) for the specific Cassandra attributes.

According to the specification, the span name "SHOULD be set to a low cardinality value representing the statement executed on the database. It MAY be a stored procedure name (without arguments), DB statement without variable arguments, operation name, etc.".\
The specification also (and only) specifies the span name for SQL databases:\
"Since SQL statements may have very high cardinality even without arguments, SQL spans SHOULD be named the following way, unless the statement is known to be of low cardinality:\
`<db.operation> <db.name>.<db.sql.table>`, provided that `db.operation` and `db.sql.table` are available. If `db.sql.table` is not available due to its semantics, the span SHOULD be named `<db.operation> <db.name>`. It is not recommended to attempt any client-side parsing of `db.statement` just to get these properties,
they should only be used if the library being instrumented already provides them. When it's otherwise impossible to get any meaningful span name, `db.name` or the tech-specific database name MAY be used."

To avoid parsing the statement, the **span name** in this implementation will be `<db.operation> <db.name>` if the **keyspace name** is available. Otherwise, it will be `<db.operation>`.

### Span attributes

This implementation will include, by default, the **required** attributes for Database, and Cassandra spans.\
`server.address` and `server.port`, despite only **recommended**, are included to give information regarding the client connection.\
`db.statement` is optional given that this attribute may contain sensitive information.

| Attribute         | Description  | Type | Level | Required | Supported Values                           |
|-------------------|---|---|---|---|--------------------------------------------|
| db.system         | An identifier for the database management system (DBMS) product being used. | string | Connection | true | cassandra                                  |
| db.namespace      | The keyspace name in Cassandra. | string | Call | conditionally true [1] | *keyspace in use*                          |
| db.operation.name | The name of the operation being executed. | string | Call | true if `db.statement` is not applicable. [2] | _Session Request_ or _Node Request_        |
| db.query.text       | The database statement being executed. | string | Call | false | *database statement in use* [3]            |
| server.address    | Name of the database host. | string | Connection | true | e.g.: example.com; 10.1.2.80; /tmp/my.sock |
| server.port       | Server port number. Used in case the port being used is not the default. | int | Connection | false | e.g.: 9445                                 |

**[1]:** There are cases where the driver doesn't know about the Keyspace name. If the developer doesn't specify a default Keyspace in the builder, or doesn't run a USE Keyspace statement manually, then the driver won't know about the Keyspace because it doesn't parse statements. If the Keyspace name is not known, the `db.name` attribute is not included.

**[2]:** Despite not being required, this implementation sets the `db.operation` attribute even if `db.statement` is included.

**[3]:** The statement value is the query string and does not include any query values. As an example, having a query that as the string `SELECT * FROM table WHERE x = ?` with `x` parameter of `123`, the attribute value of `db.statement` will be `SELECT * FROM table WHERE x = ?` and not `SELECT * FROM table WHERE x = 123`.

## Usage

### Package installation

The OpenTelemetry implementation will be included in the artifact `java-driver-open-telemetry` with the group id `org.apache.cassandra`.

### Exporting Cassandra activity
[exporting-cassandra-activity]: #exporting-cassandra-activity

The extension method `.withOpenTelemetry()` will be available in the `CqlSession` builder, so the activity can be exported for database operations:

```java
CqlSession session = CqlSession.builder()
    .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
    .withLocalDatacenter("datacenter1")
    .withOpenTelemetry(initOpenTelemetry())
    .build();
```

# Reference-level explanation
[reference-level-explanation]: #reference-level-explanation

## java-driver-open-telemetry module

### Dependencies

Similar to the existing query builder feature, this functionality will include a module named `java-driver-open-telemetry` that will handle the spans' generation.\
`java-driver-open-telemetry` will have dependencies from the following packages:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>${project.groupId}</groupId>
            <artifactId>java-driver-bom</artifactId>
            <version>${project.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-bom</artifactId>
            <version>${version.opentelemetry}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
<properties>
    <version.opentelemetry>0.15.0</version.opentelemetry>
</properties>
<dependencies>
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-api</artifactId>
    </dependency>
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-sdk</artifactId>
    </dependency>
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-exporter-jaeger</artifactId>
    </dependency>
</dependencies>
```

### Extension methods

The project will include a `Builder` extension method named `withOpenTelemetry` that will take an `OpenTelemetry` instance as parameter.
This `OpenTelemetry` instance will contain configuration for the tracing and the exporter.

```java
static OpenTelemetry initOpenTelemetry() {

    ManagedChannel jaegerChannel =
            ManagedChannelBuilder.forAddress("localhost", 14250).usePlaintext().build();

    JaegerGrpcSpanExporter jaegerExporter =
            JaegerGrpcSpanExporter.builder()
                    .setChannel(jaegerChannel)
                    .setTimeout(30, TimeUnit.SECONDS)
                    .build();

    Resource serviceNameResource =
            Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "Demo App"));

    SdkTracerProvider tracerProvider =
            SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(jaegerExporter))
                    .setResource(Resource.getDefault().merge(serviceNameResource))
                    .build();
    OpenTelemetrySdk openTelemetry =
            OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();

    return openTelemetry;
}
```

The `CqlSession` built with this method will have `OtelRequestTracker` registered as a request tracker. This class will implement the `RequestTracker` interface and will be responsible for creating spans for each request.

After [PR-1949](https://github.com/apache/cassandra-java-driver/pull/1949) is merged, the `RequestTracker` interface will include:
```java
void onSessionReady(@NonNull Session session) {}
        
void onRequestCreated(
    @NonNull Request request,
    @NonNull DriverExecutionProfile executionProfile,
    @NonNull String requestLogPrefix) {}

void onRequestCreatedForNode(
    @NonNull Request request,
    @NonNull DriverExecutionProfile executionProfile,
    @NonNull Node node,
    @NonNull String requestLogPrefix) {}

void onSuccess(
    long latencyNanos, @NonNull ExecutionInfo executionInfo, @NonNull String requestLogPrefix) {}

void onError(
    long latencyNanos, @NonNull ExecutionInfo executionInfo, @NonNull String requestLogPrefix) {}

void onNodeSuccess(
    long latencyNanos, @NonNull ExecutionInfo executionInfo, @NonNull String requestLogPrefix) {}

void onNodeError(
    long latencyNanos, @NonNull ExecutionInfo executionInfo, @NonNull String requestLogPrefix) {}
```

`OtelRequestTracker` will utilize the above methods.

`OtelRequestTracker` will initialize OpenTelemetry exporter on `onSessionReady`. 
It will create a parent span of operation name `Session Request` on `onRequestCreated`, and create a child span of operation name `Node Request` on `onRequestCreatedForNode`. It will end the spans on `onSuccess`, `onError`, `onNodeSuccess`, or `onNodeError`.

# Rationale and alternatives
[rationale-and-alternatives]: #rationale-and-alternatives

## Not using `opentelemetry-semconv` package

The [semantic conventions](https://opentelemetry.io/docs/specs/semconv/) are a fast evolving reference that "define a common set of (semantic) attributes which provide meaning to data when collecting, producing and consuming it.".\
As its changes can be hard to follow, OpenTelemetry provides a package named [`opentelemetry-semconv`](https://github.com/open-telemetry/semantic-conventions-java) that generate Java code for semantic conventions. Using this package will allow the Apache Cassandra project to have its tracing attributes up-to-date to the conventions with less maintenance, however, as it's still marked as non-stable (current version is `1.29.0-alpha`), it is not included in this proposal.

# Prior art
[prior-art]: #prior-art

OpenTelemetry has [instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/cassandra/cassandra-4.4/library) for Apache Cassandra Java Driver, but it requires the user to run the Java agent.


There are other DBMS implementations regarding the export of telemetry data in client-side calls in the java ecosystem:

- [MongoDB](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/mongo) 
- [Elasticsearch](https://github.com/elastic/elastic-otel-java) 

Apache Cassandra also has client-side implementations in other languages in the form of contribution projects, as listed below:

- [NodeJS](https://github.com/open-telemetry/opentelemetry-js-contrib/tree/main/plugins/node/opentelemetry-instrumentation-cassandra) 
- [Python](https://github.com/open-telemetry/opentelemetry-python-contrib/tree/main/instrumentation/opentelemetry-instrumentation-cassandra) 

# Future possibilities
[future-possibilities]: #future-possibilities

## Traces

### Include configuration options

The OpenTelemetry implementation can include configuration options by the standard typesafe library to allow the user to customize the exporter, the sampling, and the attributes included in the spans.
For example, we can include the following configuration options:

```
datastax-java-driver {
  advanced {
      open-telemetry {
          attributes {
              db.statement = false
          }
      }
  }
}

```

### Adopt `opentelemetry-semconv` and include missing recommended attributes

When `opentelemetry-semconv` becomes stable, the project can adopt it to generate the semantic conventions for the Cassandra driver.

As referred in [*semantic conventions* section](#opentelemetry-semantic-conventions), there are recommended attributes that are not included in this proposal that may be useful for the users of Cassandra telemetry and can be something to look at in the future iterations of this feature:

- [Cassandra Call-level attributes](https://opentelemetry.io/docs/specs/semconv/database/cassandra/#call-level-attributes)
- [Database Call-level attributes](https://opentelemetry.io/docs/specs/semconv/database/database-spans/#call-level-attributes)
- [Database Connection-level attributes](https://opentelemetry.io/docs/specs/semconv/database/database-spans/#connection-level-attributes)

## Metrics and logs

As the industry is moving to adopt OpenTelemetry, the export of metrics and logs using this standard may be something useful for the users of Apache Cassandra Java Driver. Although the [semantic conventions for database metrics](https://opentelemetry.io/docs/specs/semconv/database/database-metrics/) are still in experimental status, it can be something to look at in the future.