/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ratu;


public class Cassandra {

    public Cassandra() {

    }

    public static void active() {
        System.setProperty("log4j2.debug", "true");
        String cassandraHome = System.getProperty("es.path.home");
        String cassandraConfig=System.getProperty("es.path.conf");
        System.setProperty("cassandra.config", "file://"+cassandraConfig+"/cassandra.yaml");
        System.setProperty("cassandra.storagedir", System.getProperty("es.data.path"));
        System.setProperty("cassandra.home",cassandraHome);
        System.setProperty("cassandra.logdir",System.getProperty("es.logs.base_path"));

        try {
            org.apache.cassandra.service.CassandraDaemon daemon = new org.apache.cassandra.service.CassandraDaemon();
            daemon.activate();
        } catch (Exception e) {
            System.out.println("打印个错误吧！");
            e.printStackTrace();
        }
    }


}
