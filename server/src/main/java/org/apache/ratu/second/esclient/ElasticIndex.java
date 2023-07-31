package org.apache.ratu.second.esclient;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.Version;
import org.elasticsearch.client.*;
import org.elasticsearch.cluster.ClusterModule;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.rest.RestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.annotation.Nonnull;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.*;

import static java.util.Collections.unmodifiableList;
import static org.elasticsearch.env.Environment.PATH_HOME_SETTING;


public class ElasticIndex {

    private static final Logger logger = LoggerFactory.getLogger(ElasticIndex.class);

    private final List<String> partitionKeysNames;
    private final List<String> clusteringColumnsNames;
    private final boolean hasClusteringColumns;

    private static final NamedXContentRegistry DEFAULT_NAMED_X_CONTENT_REGISTRY =
        new NamedXContentRegistry(ClusterModule.getNamedXWriteables());

    private static final String ES_SOURCE = "_source";

    private static final Settings SETTINGS = Settings.builder()
        .put(PATH_HOME_SETTING.getKey(), "dummy")
        .build();


    private static List<HttpHost> clusterHosts;

    private static RestClient client;

    public static final String TRUSTSTORE_PATH = "truststore.path";
    public static final String TRUSTSTORE_PASSWORD = "truststore.password";
    public static final String CLIENT_SOCKET_TIMEOUT = "client.socket.timeout";
    public static final String CLIENT_PATH_PREFIX = "client.path.prefix";

    private static TreeSet<Version> nodeVersions;

    private static Boolean hasXPack;


    public ElasticIndex(@Nonnull List<String> partitionKeysNames, @Nonnull List<String> clusteringColumnsNames) {
        this.partitionKeysNames = partitionKeysNames;
        this.clusteringColumnsNames = clusteringColumnsNames;
        this.hasClusteringColumns = !clusteringColumnsNames.isEmpty();
        String cluster = getTestRestCluster();

        String[] stringUrls = cluster.split(",");
        List<HttpHost> hosts = new ArrayList<>(stringUrls.length);
        for (int i = 0; i < stringUrls.length; i++) {
            String[] split = stringUrls[i].split(":");
            hosts.add(new HttpHost(split[0], 9200, "http"));
        }
        clusterHosts = unmodifiableList(hosts);
        nodeVersions = new TreeSet<>();
        logger.info("initializing REST clients against {}", clusterHosts);
        try {
            if (client == null) {
                synchronized (ElasticIndex.class) {
                    if (client == null) {
                        client = buildClient(restClientSettings(), clusterHosts.toArray(new HttpHost[clusterHosts.size()]));
                        Map<?, ?> response = entityAsMap(client.performRequest(new Request("GET", "_nodes/plugins")));
                        Map<?, ?> nodes = (Map<?, ?>) response.get("nodes");
                        for (Map.Entry<?, ?> node : nodes.entrySet()) {
                            Map<?, ?> nodeInfo = (Map<?, ?>) node.getValue();
                            nodeVersions.add(Version.fromString(nodeInfo.get("version").toString()));
                            for (Object module : (List<?>) nodeInfo.get("modules")) {
                                Map<?, ?> moduleInfo = (Map<?, ?>) module;
                                if (moduleInfo.get("name").toString().startsWith("x-pack-")) {
                                    hasXPack = true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Boolean newIndex(String indexName, Settings setting, Map<String, Map<String, String>> fields) {
        Boolean result = false;
        try {
            result = createIndex(indexName, setting, parseEsCreateIndexMappings(fields));
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Index " + indexName + " Create Exception:", e);
        }
        return result;
    }


    public Boolean isExistsIndex(String indexName) {
        // 判断索引是否存在
        try {
            return indexExists(indexName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public Boolean indexData(Map<String, Object> maps, String indexName, String primaryKeyValue) {
        // 索引数据
        try {
            return addIndexData(indexName, JSON.toJSONString(maps), primaryKeyValue);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public Boolean bulkData(Map<String, Object> maps, String indexName, String primaryKeyValue) {
        // bulk 数据
        return false;
    }


    public Boolean delData(String indexName, String primaryKeyValue) throws IOException {
        // 删除数据
        return delIndexData(indexName, primaryKeyValue);
    }

    public void dropIndex(String indexName) {
        // 删除索引
        try {
            delIndexByName(indexName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public boolean refreshData(String indexName) {
        // refresh 索引
        try {
            return refreshIndex(indexName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public SearchResult searchData(String indexName, Map<String, Object> mappings) {
        // 普通搜索
        List<SearchResultRow> rowList = new ArrayList<>();
        Map<String, Object> query = (Map<String, Object>) mappings.get("query");
        String json = "";
        if (query != null) {
            if (query.get("bool") != null) {
                json = parseEsBool(query);
            } else {
                json = parseEsQuery(query);
            }
        }
        try {
            System.out.println("入参:" + json);
            Map<String, Object> mp = searchIndexData(indexName, json);

            List<String> primaryKeys;

            if (hasClusteringColumns) {
                primaryKeys = new ArrayList<>(partitionKeysNames.size() + clusteringColumnsNames.size());
                primaryKeys.addAll(partitionKeysNames);
                primaryKeys.addAll(clusteringColumnsNames);
            } else {
                primaryKeys = partitionKeysNames;
            }

            int pkSize = primaryKeys.size();

            Map<String,Object> hitsMap= (Map<String, Object>) mp.get("hits");

            List<Map<String,Object>> hits2List= (List<Map<String,Object>>) hitsMap.get("hits");


            for (int i=0;i<hits2List.size();i++){

                Map<String,Object> sourceMaps = (Map<String, Object>) hits2List.get(i).get("_source");
                String[] primaryKey = new String[pkSize];
                int keyNb = 0;

                for (String keyName : primaryKeys) {
                    String value =sourceMaps.get(keyName).toString();
                    if (value == null) {
                        continue;
                    } else {
                        primaryKey[keyNb] = value;
                    }
                    keyNb++;
                }

                SearchResultRow searchResultRow = new SearchResultRow(primaryKey, new JSONObject(sourceMaps));
                rowList.add(searchResultRow);
            }
//            Map<String, Aggregate> aggregations = search.aggregations();
//            if (aggregations.size() != 0) {
//                Map<Object, Object> map = AggsUtils.aggsData(aggregations.get("aggs"));
//                String[] primaryKey = new String[1];
//                primaryKey[0] = String.valueOf(search.hits().hits().size() + 1);
//                SearchResultRow row = new SearchResultRow(primaryKey, JSONObject.parseObject(JSON.toJSONString(map)));
//                rowList.add(row);
//            }
        } catch (IOException e) {
            e.printStackTrace();
        }

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
        Map<String, Object> query = new HashMap<>();
        query.put("query", aggsMaps);
        return JSON.toJSONString(query);
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
        Map<String, Object> query = new HashMap<>();
        query.put("query", aggsMaps);
        return JSON.toJSONString(query);
    }

    //创建索引格式mappings
    private static String parseEsCreateIndexMappings(Map<String, Map<String, String>> fields) {
        HashMap<String, Object> map1 = new HashMap<>();
        for (String key : fields.keySet()) {
            HashMap<String, Object> map2 = new HashMap<>();
            Map<String, String> filedmap = fields.get(key);
            for (String key2 : filedmap.keySet()) {
                String value2 = filedmap.get(key2);
                if (key2.equals("type") || key2.equals("analyzer")) {
                    map2.put(key2, value2);
                    if (value2.equals("text")) {
                        Map<String, Object> childMap = new HashMap<>();
                        childMap.put("type", "keyword");
                        childMap.put("ignore_above", 256);
                        Map<String, Object> keywordMap = new HashMap<>();
                        keywordMap.put("keyword", childMap);
                        map2.put("fields", keywordMap);
                    }

                }
            }
            map1.put(key, map2);
        }

        HashMap<String, Object> mappings = new HashMap<>();

        mappings.put("properties", map1);
        return JSON.toJSONString(mappings);
    }

    protected String getTestRestCluster() {
        return DatabaseDescriptor.getRpcAddress().getHostAddress();
    }

    protected RestClient buildClient(Settings settings, HttpHost[] hosts) throws IOException {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("elastic-admin", "elastic-password"));
        return RestClient.builder(hosts).setHttpClientConfigCallback((HttpAsyncClientBuilder httpAsyncClientBuilder) -> httpAsyncClientBuilder.setDefaultCredentialsProvider(credentialsProvider)).build();
    }

    protected static void configureClient(RestClientBuilder builder, Settings settings) throws IOException {
        String keystorePath = settings.get(TRUSTSTORE_PATH);
        if (keystorePath != null) {
            final String keystorePass = settings.get(TRUSTSTORE_PASSWORD);
            if (keystorePass == null) {
                throw new IllegalStateException(TRUSTSTORE_PATH + " is provided but not " + TRUSTSTORE_PASSWORD);
            }
            Path path = PathUtils.get(keystorePath);
            if (!Files.exists(path)) {
                throw new IllegalStateException(TRUSTSTORE_PATH + " is set but points to a non-existing file");
            }
            try {
                final String keyStoreType = keystorePath.endsWith(".p12") ? "PKCS12" : "jks";
                KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                try (InputStream is = Files.newInputStream(path)) {
                    keyStore.load(is, keystorePass.toCharArray());
                }
                SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial(keyStore, null).build();
                SSLIOSessionStrategy sessionStrategy = new SSLIOSessionStrategy(sslcontext);
                builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setSSLStrategy(sessionStrategy));
            } catch (KeyStoreException | NoSuchAlgorithmException | KeyManagementException | CertificateException e) {
                throw new RuntimeException("Error setting up ssl", e);
            }
        }
        Map<String, String> headers = ThreadContext.buildDefaultHeaders(settings);
        Header[] defaultHeaders = new Header[headers.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            defaultHeaders[i++] = new BasicHeader(entry.getKey(), entry.getValue());
        }
        builder.setDefaultHeaders(defaultHeaders);
        final String socketTimeoutString = settings.get(CLIENT_SOCKET_TIMEOUT);
        final TimeValue socketTimeout =
            TimeValue.parseTimeValue(socketTimeoutString == null ? "60s" : socketTimeoutString, CLIENT_SOCKET_TIMEOUT);
        builder.setRequestConfigCallback(conf -> conf.setSocketTimeout(Math.toIntExact(socketTimeout.getMillis())));
        if (settings.hasValue(CLIENT_PATH_PREFIX)) {
            builder.setPathPrefix(settings.get(CLIENT_PATH_PREFIX));
        }
    }

    protected Settings restClientSettings() {
        Settings.Builder builder = Settings.builder();
        builder.put("xpack.security.user", "elastic-admin:elastic-password");
        return builder.build();
    }

    protected static RestClient client() {
        return client;
    }

    protected static Boolean createIndex(String name, Settings settings) throws IOException {
        return createIndex(name, settings, null);
    }

    protected static Boolean createIndex(String name, Settings settings, String mapping) throws IOException {
        return createIndex(name, settings, mapping, null);
    }

    protected static Boolean createIndex(String name, Settings settings, String mapping, String aliases) throws IOException {
        Request request = new Request("PUT", "/" + name);
        String entity = "{\"settings\": " + Strings.toString(settings);
        if (mapping != null) {
            entity += ",\"mappings\" : " + mapping;
        }
        if (aliases != null) {
            entity += ",\"aliases\": {" + aliases + "}";
        }
        entity += "}";
        if (settings.getAsBoolean(IndexSettings.INDEX_SOFT_DELETES_SETTING.getKey(), true) == false) {
            expectSoftDeletesWarning(request, name);
        } else if (settings.hasValue(IndexSettings.INDEX_TRANSLOG_RETENTION_AGE_SETTING.getKey()) ||
            settings.hasValue(IndexSettings.INDEX_TRANSLOG_RETENTION_SIZE_SETTING.getKey())) {
            expectTranslogRetentionWarning(request);
        }
        request.setJsonEntity(entity);
        Response response = client().performRequest(request);
        Map<String, Object> map = entityAsMap(response);
        return Boolean.parseBoolean(map.get("acknowledged").toString());
    }


    private static Boolean addIndexData(String index, String json, String id) throws IOException {
        Request request = new Request("PUT", "/" + index + "/_doc/" + id);
        request.setJsonEntity(json);
        client.performRequestAsync(request, new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
                logger.debug("Insert data "+index+" res:"+response);
            }

            @Override
            public void onFailure(Exception exception) {
                logger.error("Insert data "+index+" Exception:",exception);
            }
        });
//        Map<String, Object> map = entityAsMap(response);
//        String result = map.get("result").toString();
//        if (result.equals("updated") || result.equals("created")) {
//            return true;
//        }
//        return false;
        return true;
    }


    private static Boolean delIndexData(String index, String id) throws IOException {
        Request request = new Request("DELETE", "/" + index + "/_doc/" + id);
        Response response = client().performRequest(request);
        Map<String, Object> map = entityAsMap(response);
        String result = map.get("result").toString();
        if (result.equals("deleted")) {
            return true;
        }
        return false;
    }


    private static Boolean delIndexByName(String index) throws IOException {
        Request request = new Request("DELETE", "/" + index);
        Response response = client().performRequest(request);
        Map<String, Object> map = entityAsMap(response);
        String result = map.get("acknowledged").toString();
        return Boolean.valueOf(result);
    }

    private static Boolean refreshIndex(String index) throws IOException {
        Request request = new Request("GET", "/" + index + "/_refresh");
        Response response = client().performRequest(request);
        Map<String, Object> map = entityAsMap(response);
        Map shardsMaps = (Map) map.get("_shards");
        Object successful = shardsMaps.get("successful");
        return Boolean.valueOf(successful.toString());
    }


    protected static boolean indexExists(String index) throws IOException {
        Response response = client().performRequest(new Request("HEAD", "/" + index));
        return RestStatus.OK.getStatus() == response.getStatusLine().getStatusCode();
    }

    public Integer getClusterHealth() {
        Request request = new Request("GET", "/_cluster/health");
        try {
            Response response = client.performRequest(request);
            Map<String, Object> map = entityAsMap(response);
            return Integer.valueOf(map.get("number_of_nodes").toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }


    private static Map<String, Object> searchIndexData(String index, String dslJson) throws IOException {
        Request request = new Request("GET", "/" + index + "/_search");
        if (!StringUtils.isBlank(dslJson)) {
            request.setJsonEntity(dslJson);
        }
        Response response = client().performRequest(request);
        return entityAsMap(response);
    }

    protected static void expectSoftDeletesWarning(Request request, String indexName) {
        final List<String> expectedWarnings = Collections.singletonList(
            "Creating indices with soft-deletes disabled is deprecated and will be removed in future Elasticsearch versions. " +
                "Please do not specify value for setting [index.soft_deletes.enabled] of index [" + indexName + "].");
        final RequestOptions.Builder requestOptions = RequestOptions.DEFAULT.toBuilder();
        if (nodeVersions.stream().allMatch(version -> version.onOrAfter(Version.V_7_6_0))) {
            requestOptions.setWarningsHandler(warnings -> warnings.equals(expectedWarnings) == false);
            request.setOptions(requestOptions);
        } else if (nodeVersions.stream().anyMatch(version -> version.onOrAfter(Version.V_7_6_0))) {
            requestOptions.setWarningsHandler(warnings -> warnings.isEmpty() == false && warnings.equals(expectedWarnings) == false);
            request.setOptions(requestOptions);
        }
    }


    protected static void expectTranslogRetentionWarning(Request request) {
        final List<String> expectedWarnings = Collections.singletonList(
            "Translog retention settings [index.translog.retention.age] "
                + "and [index.translog.retention.size] are deprecated and effectively ignored. They will be removed in a future version.");
        final RequestOptions.Builder requestOptions = RequestOptions.DEFAULT.toBuilder();
        if (nodeVersions.stream().allMatch(version -> version.onOrAfter(Version.V_7_7_0))) {
            requestOptions.setWarningsHandler(warnings -> warnings.equals(expectedWarnings) == false);
            request.setOptions(requestOptions);
        } else if (nodeVersions.stream().anyMatch(version -> version.onOrAfter(Version.V_7_7_0))) {
            requestOptions.setWarningsHandler(warnings -> warnings.isEmpty() == false && warnings.equals(expectedWarnings) == false);
            request.setOptions(requestOptions);
        }
    }


    /**
     * Convert the entity from a {@link Response} into a map of maps.
     */
    public static Map<String, Object> entityAsMap(Response response) throws IOException {
        XContentType xContentType = XContentType.fromMediaTypeOrFormat(response.getEntity().getContentType().getValue());
        // EMPTY and THROW are fine here because `.map` doesn't use named x content or deprecation
        try (XContentParser parser = xContentType.xContent().createParser(
            NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
            response.getEntity().getContent())) {
            return parser.map();
        }
    }
}
