/*
 * Copyright (c) 2017 Strapdata (http://www.strapdata.com)
 * Contains some code from Elasticsearch (http://www.elastic.co)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ruitu.second;

import com.alibaba.fastjson2.JSONObject;
import com.google.common.base.Stopwatch;
import org.apache.cassandra.cql3.Operator;
import org.apache.cassandra.cql3.statements.schema.IndexTarget;
import org.apache.cassandra.db.*;
import org.apache.cassandra.db.filter.RowFilter;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.db.partitions.PartitionIterator;
import org.apache.cassandra.db.partitions.PartitionUpdate;
import org.apache.cassandra.db.partitions.UnfilteredPartitionIterator;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.index.Index;
import org.apache.cassandra.index.IndexRegistry;
import org.apache.cassandra.index.transactions.IndexTransaction;
import org.apache.cassandra.notifications.INotification;
import org.apache.cassandra.notifications.INotificationConsumer;
import org.apache.cassandra.schema.ColumnMetadata;
import org.apache.cassandra.schema.IndexMetadata;
import org.apache.cassandra.schema.TableMetadata;
import org.apache.ruitu.second.esclient.ElasticIndex;
import org.apache.ruitu.second.esclient.EsPartitionIterator;
import org.apache.ruitu.second.esclient.SearchResult;
import org.apache.ruitu.second.esclient.SearchResultRow;
import org.apache.ruitu.second.indexers.EsIndexer;
import org.apache.ruitu.second.indexers.NoOpPartitionIterator;
import org.elasticsearch.action.admin.indices.open.TransportOpenIndexAction;
import org.elasticsearch.action.index.TransportIndexAction;
import org.elasticsearch.bootstrap.Elasticsearch;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.CharacterCodingException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;


public class ElasticSecondaryIndex implements Index, INotificationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSecondaryIndex.class);


    public final ColumnFamilyStore baseCfs;
    public final IndexMetadata config;

    private static final String UPDATE = "#update#";
    private static final String GET_MAPPING = "#get_mapping#";
    private static final String PUT_MAPPING = "#put_mapping#";

    private final SecureRandom random = new SecureRandom();

    // 索引名称
    public final String index_name;

    public final TableMetadata metadata;
    // keyspace 名字
    public final String ksName;
    // 表名
    public final String cfName;
    // 还是个表名
    public final String idxName;


    public final Map<String, Map<String, String>> schema;

    private List<String> partitionKeysNames;
    private List<String> clusteringColumnsNames;
    private boolean hasClusteringColumns;
    public final String indexColumnName;

    public Integer refreshSecond = 1;


    private final ElasticIndex elasticIndex;


    public ElasticSecondaryIndex(ColumnFamilyStore baseCfs, IndexMetadata config) {
        this.baseCfs = baseCfs;
        this.config = config;
        this.index_name = baseCfs.keyspace.getName() + "." + baseCfs.name;

        this.metadata = this.baseCfs.metadata();

        this.ksName = metadata.keyspace;
        this.cfName = metadata.name;
        this.idxName = config.name;

        indexColumnName = unQuote(config.options.get(IndexTarget.TARGET_OPTION_NAME));

        try {
            partitionKeysNames = Collections.unmodifiableList(Utils.getPartitionKeyNames(baseCfs.metadata()));
            clusteringColumnsNames = Collections.unmodifiableList(Utils.getClusteringColumnsNames(baseCfs.metadata()));
        } catch (CharacterCodingException e) {
            e.printStackTrace();
        }

        hasClusteringColumns = !clusteringColumnsNames.isEmpty();

        elasticIndex = new ElasticIndex(partitionKeysNames, clusteringColumnsNames);

        Map<String, String> options = config.options;
        refreshSecond = options.get("refresh_seconds") == null ? 5 : Integer.valueOf(options.get("refresh_seconds"));
        String schema = options.get("schema");
        Map<String, Map<String, String>> filedes = (Map<String, Map<String, String>>) JSONObject.parseObject(Utils.pattern(schema), Map.class).get("fields");
        this.schema = filedes;
        // 判断是否有索引，如果没有就创建索引
        if (!elasticIndex.isExistsIndex(this.index_name)) {
            Settings.Builder settingsBuilder = Settings.builder();
            settingsBuilder.put("number_of_shards", elasticIndex.getClusterHealth());
            settingsBuilder.put("number_of_replicas", (elasticIndex.getClusterHealth() - 1));
            settingsBuilder.put("refresh_interval", refreshSecond + "s");
            elasticIndex.newIndex(this.index_name, settingsBuilder.build(), filedes);
        }
    }


    @Override
    public Callable<?> getInitializationTask() {
        return null;
    }

    @Override
    public IndexMetadata getIndexMetadata() {
        return config;
    }

    @Override
    public Callable<?> getMetadataReloadTask(IndexMetadata indexMetadata) {
        return null;
    }

    @Override
    public void register(IndexRegistry registry) {
        registry.registerIndex(this);
    }

    @Override
    public Optional<ColumnFamilyStore> getBackingTable() {
        return Optional.empty();
    }

    @Override
    public Callable<?> getBlockingFlushTask() {
        return null;
    }

    @Override
    public Callable<?> getInvalidateTask() {
        // 删除索引
        try {
            elasticIndex.dropIndex(this.index_name);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Callable<?> getTruncateTask(long truncatedAt) {
        return null;
    }

    @Override
    public boolean shouldBuildBlocking() {
        return false;
    }

    @Override
    public boolean dependsOn(ColumnMetadata column) {
        return false;
    }

    @Override
    public boolean supportsExpression(ColumnMetadata column, Operator operator) {
        return false;
    }

    @Override
    public AbstractType<?> customExpressionValueType() {
        return UTF8Type.instance;
    }

    @Override
    public RowFilter getPostIndexQueryFilter(RowFilter filter) {
        return RowFilter.NONE;
    }

    @Override
    public long getEstimatedResultRows() {
        return -Math.abs(random.nextLong());
    }

    @Override
    public void validate(PartitionUpdate update) throws InvalidRequestException {

    }

    @Override
    public void validate(ReadCommand command) throws InvalidRequestException {
        String queryString = Utils.queryString(command);
        if (!queryString.startsWith(UPDATE) && !queryString.startsWith(GET_MAPPING) && !queryString.startsWith(PUT_MAPPING)) {
            //验证表达式的正确与否
        }
    }

    @Override
    public Indexer indexerFor(DecoratedKey key, RegularAndStaticColumns columns, int nowInSec, WriteContext ctx, IndexTransaction.Type transactionType) {
        // 索引新数据
        return new EsIndexer(this, key, nowInSec, false);
    }

    @Override
    public BiFunction<PartitionIterator, ReadCommand, PartitionIterator> postProcessorFor(ReadCommand command) {
        return NoOpPartitionIterator.INSTANCE;
    }

    @Override
    public Searcher searcherFor(ReadCommand command) {
        // 查询 数据
        return executionController -> search(executionController, command);
    }

    @Override
    public void handleNotification(INotification notification, Object sender) {
    }


    @Nullable
    public void index(@Nonnull DecoratedKey decoratedKey, @Nonnull Row newRow, @Nullable Row oldRow, int nowInSec) {
        final Stopwatch time = Stopwatch.createStarted();
        String primaryKey = decoratedKey.getPrimaryKey(this.baseCfs.metadata());
        String primaryKeyValue = decoratedKey.getPrimaryKeyValue(this.baseCfs.metadata()).replace("'", "");
        try {
            Map<String, Object> maps = Utils.toMaps(newRow, this.schema);
            maps.put(primaryKey, primaryKeyValue);
            HashMap<String, Object> jsonMap = new HashMap<>();
            for (String key : this.schema.keySet()) {
                jsonMap.put(key, maps.get(key));
            }
            // 索引数据
            elasticIndex.indexData(jsonMap, index_name, primaryKeyValue);
        } catch (Exception e) {
            logger.error("Index " + index_name + " data Exception:", e);
        }

        logger.info("Index {} data  took {} ms", index_name, time.elapsed(TimeUnit.MILLISECONDS));
    }

    @Nullable
    public void delete(DecoratedKey decoratedKey) {
        final Stopwatch time = Stopwatch.createStarted();
        String primaryKeyValue = decoratedKey.getPrimaryKeyValue(baseCfs.metadata());
        try {
            //删除索引数据
            elasticIndex.delData(index_name, primaryKeyValue);
        } catch (Exception e) {
            logger.error("delete data Exception:", e);
        }
        logger.info("delete data 结果，primaryKeyValue：" + primaryKeyValue + " took {}ms", time.elapsed(TimeUnit.MILLISECONDS));
    }


    @Nonnull
    public UnfilteredPartitionIterator search(ReadExecutionController controller, ReadCommand command) {
        final Stopwatch time = Stopwatch.createStarted();
        final String queryString = Utils.queryString(command);
        Map map = JSONObject.parseObject(Utils.pattern(queryString), Map.class);

        System.out.println("bool查询条件:" + map);

        boolean refresh = map.get("refresh") == null ? false : Boolean.parseBoolean(map.get("refresh").toString());

        if (refresh) {
            try {
                boolean b = true;
                // 刷新索引
                 b=elasticIndex.refreshData(index_name);
                logger.info("refresh data 结果:" + b);
            } catch (Exception e) {
                logger.error("refresh data faild:", e);
            }
        }

        final String searchId = UUID.randomUUID().toString();

        SearchResult searchResult = null;
        SearchResultRow searchResultRow = null;
        try {
            // 搜索数据
            searchResult = elasticIndex.searchData(index_name, map);
            searchResultRow = searchResult.items.get((searchResult.items.size() - 1));
            //searchResult.items.remove((searchResult.items.size() - 1));
            fillPartitionAndClusteringKeys(searchResult.items);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("query data faild:", e);
        }
        logger.info("{} select data took {}ms", index_name, time.elapsed(TimeUnit.MILLISECONDS));
        return new EsPartitionIterator(this, searchResult, partitionKeysNames, command, searchId, searchResultRow.docMetadata);
    }


    public void fillPartitionAndClusteringKeys(List<SearchResultRow> searchResultRows) {
        for (SearchResultRow searchResultRow : searchResultRows) {
            String[] rawKey = searchResultRow.primaryKey;
            final String[] partitionKeys;
            final String[] clusteringKeys;

            if (hasClusteringColumns) {
                clusteringKeys = new String[clusteringColumnsNames.size()];
                partitionKeys = new String[partitionKeysNames.size()];

                int pkPos = 0;
                int ckPos = 0;
                for (String key : rawKey) {
                    if (pkPos < partitionKeysNames.size()) {
                        partitionKeys[pkPos] = key;
                    } else {
                        clusteringKeys[ckPos] = key;
                        ckPos++;
                    }
                    pkPos++;
                }
            } else {
                partitionKeys = rawKey;
                clusteringKeys = null;
            }

            searchResultRow.partitionKey = Utils.getPartitionKeys(partitionKeys, baseCfs.metadata());
            searchResultRow.clusteringKeys = clusteringKeys;
        }
    }

    @Nonnull
    static String unQuote(@Nonnull String string) {
        return string.replaceAll("\"", "");
    }


}
