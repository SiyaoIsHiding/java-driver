#!groovy

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


def executeTests() {
    def testJavaHome = sh(label: 'Get TEST_JAVA_HOME', script: "jabba which ${TEST_JAVA_VERSION}", returnStdout: true).trim()
    def testJavaVersion = (TEST_JAVA_VERSION =~ /.*@1\.(\d+)/)[0][1]
    def ccmIsDse = 'false'
    def serverVersion = env.SERVER_VERSION
    if (env.SERVER_VERSION.split('-')[0] == 'dse') {
        ccmIsDse = 'true'
        serverVersion = env.SERVER_VERSION.split('-')[1]
    }
    sh '''
	export JAVA8_HOME=$(jabba which zulu@1.8)
  	export JAVA11_HOME=$(jabba which openjdk@1.11.0)
	export JAVA17_HOME=$(jabba which openjdk@1.17.0)
  	export JAVA_HOME=$JAVA8_HOME
   	printenv | sort
  	mvn -B -V install -DskipTests -Dmaven.javadoc.skip=true
	mvn -B -V verify -T 1 -Ptest-jdk-''' + testJavaVersion +
            ''' -DtestJavaHome=''' + testJavaHome +
            ''' -Dccm.version=''' + serverVersion + ''' -Dccm.dse=''' + ccmIsDse + ''' -Dmaven.test.failure.ignore=true -Dmaven.javadoc.skip=true'''
}

pipeline {
    agent {
	label 'cassandra-amd64-large'
    }


    stages {
        stage('Matrix') {
            matrix {
                axes {
                    axis {
                        name 'TEST_JAVA_VERSION'
                        values 'zulu@1.8', 'openjdk@1.11.0', 'openjdk@1.17.0'
                    }
                    axis {
                        name 'SERVER_VERSION'
                        values '3.11',      // Latest stable Apache CassandraⓇ
                                '4.1',       // Development Apache CassandraⓇ
                                'dse-6.8.30', // Current DataStax Enterprise
                                '5.0-beta1' // Beta Apache CassandraⓇ
                    }
                }
                stages {
                    stage('Tests') {
			agent {
				docker {
            				image 'janehe/cassandra-java-driver-dev-env'
        			}
			}
                        steps {
                            script {
                                executeTests()
                                junit testResults: '**/target/surefire-reports/TEST-*.xml', allowEmptyResults: true
                                junit testResults: '**/target/failsafe-reports/TEST-*.xml', allowEmptyResults: true
                            }
                        }
                    }
                }
            }
        }
    }
}
