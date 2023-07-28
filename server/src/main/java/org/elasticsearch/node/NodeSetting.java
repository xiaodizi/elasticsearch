/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.elasticsearch.node;

import com.alibaba.fastjson2.JSON;
import org.elasticsearch.common.settings.Settings;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class NodeSetting {


    private static String getSeedsConfig(String filePath) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(new File(filePath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Yaml yaml = new Yaml();
        Map<String, List<Map<String, List<Map<String, Object>>>>> data = yaml.load(inputStream);
        String seedProvider = data.get("seed_provider").get(0).get("parameters").get(0).get("seeds").toString();
        String[] split = seedProvider.split(",");
        String[] ipArr=new String[split.length];
        for (int i = 0; i < split.length; i++) {
            String substring = split[i].substring(0, split[i].indexOf(":"));
            ipArr[i]=substring;
        }
        return JSON.toJSONString(ipArr).replace("[","").replace("]","").replace("\"","").trim();
    }

    private static String getSeedsConfigPort(String filePath) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(new File(filePath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Yaml yaml = new Yaml();
        Map<String, List<Map<String, List<Map<String, Object>>>>> data = yaml.load(inputStream);
        String seedProvider = data.get("seed_provider").get(0).get("parameters").get(0).get("seeds").toString();
        String[] split = seedProvider.split(",");
        String[] ipArr=new String[split.length];
        for (int i = 0; i < split.length; i++) {
            String substring = split[i].substring(0, split[i].indexOf(":"));
            ipArr[i]=substring+":9300";
        }
        return JSON.toJSONString(ipArr).replace("[","").replace("]","").replace("\"","").trim();
    }


    public static String getCassandraYamlByKey(String key, String path) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(new File(path));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(inputStream);
        if (data.get(key) == null){
            return null;
        }
        return data.get(key).toString();
    }

    public static Settings nodeSettings(String dataPath, Settings settings, String path, SnitchProperties snitchProperties) {

        System.setProperty("es.data.path",dataPath);
        if (getSeedsConfig(path).equals("127.0.0.1") || getSeedsConfig(path).equals("localhost")) {
            return Settings.builder()
                .put("network.host", getCassandraYamlByKey("rpc_address", path))
                .put("http.port",9200)
                .put("transport.port",9300)
                .put("node.name", getCassandraYamlByKey("rpc_address", path))
                .put("cluster.name", getCassandraYamlByKey("cluster_name", path))
                .put("path.home", settings.get("path.home"))
                .put("path.data", dataPath+"/search")
                .put("node.attr.rack_id",snitchProperties.get("dc").trim()+"-"+snitchProperties.get("rack").trim())
                .build();
        }
        return Settings.builder()
            .put("network.host", getCassandraYamlByKey("rpc_address", path))
            .put("http.port",9200)
            .put("transport.port",9300)
            .put("node.name", getCassandraYamlByKey("rpc_address", path))
            .put("discovery.seed_hosts", getSeedsConfigPort(path))
            .put("cluster.initial_master_nodes",getSeedsConfig(path))
            .put("cluster.name", getCassandraYamlByKey("cluster_name", path))
            .put("path.home", settings.get("path.home"))
            .put("path.data", dataPath+"/search")
            .put("node.attr.rack_id",snitchProperties.get("dc").trim()+"-"+snitchProperties.get("rack").trim())
            .build();
    }
}
