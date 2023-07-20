package org.apache.ruitu.second.esclient;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.ruitu.second.reset.FakeRestChannel;
import org.apache.ruitu.second.reset.FakeRestRequest;
import org.elasticsearch.cluster.ClusterModule;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.admin.indices.RestCreateIndexAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;


public class ElasticIndex {

    private static final Logger logger = LoggerFactory.getLogger(ElasticIndex.class);

    private final List<String> partitionKeysNames;
    private final List<String> clusteringColumnsNames;
    private final boolean hasClusteringColumns;

    private static final NamedXContentRegistry DEFAULT_NAMED_X_CONTENT_REGISTRY =
        new NamedXContentRegistry(ClusterModule.getNamedXWriteables());

    private static final String ES_SOURCE = "_source";

    private RestController controller;

    public ElasticIndex(@Nonnull List<String> partitionKeysNames, @Nonnull List<String> clusteringColumnsNames) {
        this.partitionKeysNames = partitionKeysNames;
        this.clusteringColumnsNames = clusteringColumnsNames;
        this.hasClusteringColumns = !clusteringColumnsNames.isEmpty();
    }

    public Boolean newIndex(String indexName, Map<String, Map<String, String>> fields, Integer refreshSecond) {
        // 创建索引
        return false;
    }


    public Boolean isExistsIndex(String indexName) {
        // 判断索引是否存在
        return false;
    }

    public Boolean indexData(Map<String, Object> maps, String indexName, String primaryKeyValue) {
        // 索引数据

        return false;
    }

    public Boolean bulkData(Map<String, Object> maps, String indexName, String primaryKeyValue) {
        // bulk 数据
        return false;
    }


    public Boolean delData(String indexName, String primaryKeyValue) throws IOException {
        // 删除数据
        return false;
    }

    public void dropIndex(String indexName) {
        // 删除索引
    }


    public boolean refreshData(String indexName) {
        // refresh 索引
        return false;
    }

    public SearchResult searchData(String indexName, Map<String, Object> mappings) {
        // 普通搜索
        List<SearchResultRow> rowList = new ArrayList<>();
        return new SearchResult(rowList);
    }

    private String parseAggs(Map<String, Object> mappings) {
        return mappings.get("aggs").toString();
    }


    // 普通查询
    private static String parseEsQuery(Map<String, Object> queryMps) {
        Map<String, Object> aggsMaps = new HashMap<>();
        Map<String, Object> fieldMaps = new HashMap<>();
        Map<String, Object> whereMaps = new HashMap<>();
        for (String m : queryMps.keySet()) {
            if (!m.equals("type") && !m.equals("field")) {
                whereMaps.put(m, queryMps.get(m));
            }
        }
        Object value = queryMps.get("value");
        if (value == null) {
            fieldMaps.put(queryMps.get("field").toString(), whereMaps);
        } else {
            fieldMaps.put(queryMps.get("field").toString(), value);
        }
        aggsMaps.put(queryMps.get("type").toString(), fieldMaps);
        return JSON.toJSONString(aggsMaps);
    }

    // bool 查询
    private static String parseEsBool(Map<String, Object> boolMps) {
        Map<String, Object> aggsMaps = new HashMap<>();
        Map<String, List<Map<String, Object>>> filter = (Map<String, List<Map<String, Object>>>) boolMps.get("bool");
        Map<String, Object> boolWhere = new HashMap<>();
        for (String key : filter.keySet()) {
            List<Object> list = new ArrayList<>();
            List<Map<String, Object>> mapList = filter.get(key);
            mapList.stream().forEach(mps -> {
                String s = parseEsQuery(mps);
                Map map = JSONObject.parseObject(s, Map.class);
                list.add(map);
            });
            boolWhere.put(key, list);
        }
        aggsMaps.put("bool", boolWhere);
        return JSON.toJSONString(aggsMaps);
    }


    private void dispatchRequest(RestRequest request) {
        FakeRestChannel channel = new FakeRestChannel(request, false, 1);
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        controller.dispatchRequest(request, channel, threadContext);
    }


}
