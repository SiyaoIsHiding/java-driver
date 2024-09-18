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

package com.datastax.driver.examples.astra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import java.io.File;

/**
 * Connects to a DataStax Astra cluster and extracts basic information from it.
 *
 * <p>Preconditions:
 *
 * <ul>
 *   <li>A DataStax Astra cluster is running and accessible.
 *   <li>A DataStax Astra secure connect bundle for the running cluster.
 * </ul>
 *
 * <p>Side effects: none.
 *
 * @see <a href="https://docs.astra.datastax.com/docs/creating-your-astra-database">Creating an
 *     Astra Database</a>
 * @see <a
 *     href="https://docs.astra.datastax.com/docs/obtaining-database-credentials#sharing-your-secure-connect-bundle">
 *     Providing access to Astra databases</a>
 * @see <a
 *     href="https://docs.astra.datastax.com/docs/obtaining-database-credentials#getting-your-secure-connect-bundle">
 *     Obtaining Astra secure connect bundle</a>
 * @see <a href="http://datastax.github.io/java-driver/manual/">Java Driver online manual</a>
 */
public class AstraReadCassandraVersion {

  public static void main(String[] args) {

    Cluster cluster = null;
    try {
      // The Cluster object is the main entry point of the driver.
      // It holds the known state of the actual Cassandra cluster (notably the Metadata).
      // This class is thread-safe, you should create a single instance (per target Cassandra
      // cluster), and share it throughout your application.
      // Change the path here to the secure connect bundle location (see javadocs above)
      cluster =
          Cluster.builder()
              // Change the path here to the secure connect bundle location (see javadocs above)
              .withCloudSecureConnectBundle(new File("/path/to/secure-connect-database_name.zip"))
              // Change the user_name and password here for the Astra instance
              .withCredentials("user_name", "password")
              // Uncomment the next line to use a specific keyspace
              // .withKeyspace("keyspace_name")
              .build();

      // The Session is what you use to execute queries. Likewise, it is thread-safe and should be
      // reused.
      Session session = cluster.connect();

      // We use execute to send a query to Cassandra. This returns a ResultSet, which is essentially
      // a collection of Row objects.
      ResultSet rs = session.execute("select release_version from system.local");
      // Extract the first row (which is the only one in this case).
      Row row = rs.one();

      // Extract the value of the first (and only) column from the row.
      String releaseVersion = row.getString("release_version");
      System.out.printf("Cassandra version is: %s%n", releaseVersion);

    } finally {
      // Close the cluster after we’re done with it. This will also close any session that was
      // created from this cluster.
      // This step is important because it frees underlying resources (TCP connections, thread
      // pools...). In a real application, you would typically do this at shutdown (for example,
      // when undeploying your webapp).
      if (cluster != null) cluster.close();
    }
  }
}