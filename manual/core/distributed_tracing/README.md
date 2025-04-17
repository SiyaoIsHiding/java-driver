<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

## Distributed tracing

### Quick overview

Users can inject an identifier for each individual CQL request, and such ID can be written in to the custom payload to 
correlate a request across the driver and the Apache Cassandra server.

* Inject ID generator: set the desired `DistributedTraceIdGenerator` in `advanced.distributed-tracing.id-generator.class`. 
  The default implementation generates ID as `{session_name}|{hash_code}|{execution_count}`.
* Add ID to custom payload: disabled by default. Set the desired key in `advanced.distributed-tracing.custom-payload-with-key`, 
  then the driver will add the generated ID to the custom payload with the specified key.

### Distributed Trace Id Generator Configuration

Distributed trace ID generator can be declared in the [configuration](../configuration/) as follows:

```
datastax-java-driver.advanced.distributed-tracing.id-generator {
  class = com.example.app.MyGenerator
}
```

To register your own trackers, specify the name of a class
that implements `DistributedTraceIdGenerator`.

By default, the build-in implementation `DefaultDistributedTraceIdGenerator` is used. It generates the ID as
`{session_name}|{hash_code}|{execution_count}`. Note that this ID is not guaranteed to be unique.
Other built-in implementations include `UUIDDistributedTraceIdGenerator` and `W3CContextDistributedTraceIdGenerator`.

The generated ID will be added to the log message of `CqlRequestHandler`, and propagated to the request trackers.

### Custom Payload Configuration

Users can opt in to add the generated ID to the custom payload to achieve request end-to-end tracing.
Custom payload is a map of string to `ByteBuffer` pairs, and the driver will add the generated ID to the custom payload with the specified key.

```
datastax-java-driver.advanced.distributed-tracing{
  custom-payload-with-key = my-trace-key
}
```

Users can then run Apache Cassandra with customized query handler to extract the trace ID from the custom payload, to achieve end-to-end tracing.

When this key is set to an empty string (default), the driver will not add the ID to the custom payload.
