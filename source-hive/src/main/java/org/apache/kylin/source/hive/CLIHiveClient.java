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

package org.apache.kylin.source.hive;

import org.apache.kylin.shaded.com.google.common.collect.Lists;
import org.apache.hadoop.hive.common.StatsSetupConst;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.MetaStoreUtils;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.common.util.HiveCmdBuilder;
import org.apache.kylin.common.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hive meta API client for Kylin
 * @author shaoshi
 *
 */
public class CLIHiveClient implements IHiveClient {
    protected HiveConf hiveConf = null;
    protected IMetaStoreClient metaStoreClient = null;

    public CLIHiveClient() {
        hiveConf = new HiveConf(CLIHiveClient.class);
    }

    /**
     * only used by Deploy Util
     * @throws IOException 
     */
    @Override
    public void executeHQL(String hql) throws IOException {
        final HiveCmdBuilder hiveCmdBuilder = new HiveCmdBuilder();
        hiveCmdBuilder.addStatement(hql);
        Pair<Integer, String> response = KylinConfig.getInstanceFromEnv().getCliCommandExecutor()
                .execute(hiveCmdBuilder.toString());
        if (response.getFirst() != 0) {
            throw new IllegalArgumentException("Failed to execute hql [" + hql + "], error message is: " + response.getSecond());
        }

    }

    /**
     * only used by Deploy Util
     */
    @Override
    public void executeHQL(String[] hqls) throws IOException {
        for (String sql : hqls)
            executeHQL(sql);
    }

    @Override
    public HiveTableMeta getHiveTableMeta(String database, String tableName) throws Exception {
        HiveTableMetaBuilder builder = new HiveTableMetaBuilder();
        Table table = getMetaStoreClient().getTable(database, tableName);

        List<FieldSchema> allFields = getMetaStoreClient().getFields(database, tableName);
        List<FieldSchema> partitionFields = table.getPartitionKeys();
        if (allFields == null) {
            allFields = Lists.newArrayList();
        }
        if (partitionFields != null && partitionFields.size() > 0) {
            allFields.addAll(partitionFields);
        }
        List<HiveTableMeta.HiveTableColumnMeta> allColumns = Lists.newArrayList();
        List<HiveTableMeta.HiveTableColumnMeta> partitionColumns = Lists.newArrayList();
        for (FieldSchema fieldSchema : allFields) {
            allColumns.add(new HiveTableMeta.HiveTableColumnMeta(fieldSchema.getName(), fieldSchema.getType(), fieldSchema.getComment()));
        }
        if (partitionFields != null && partitionFields.size() > 0) {
            for (FieldSchema fieldSchema : partitionFields) {
                partitionColumns.add(new HiveTableMeta.HiveTableColumnMeta(fieldSchema.getName(), fieldSchema.getType(), fieldSchema.getComment()));
            }
        }
        builder.setAllColumns(allColumns);
        builder.setPartitionColumns(partitionColumns);

        builder.setSdLocation(table.getSd().getLocation());
        builder.setFileSize(getBasicStatForTable(new org.apache.hadoop.hive.ql.metadata.Table(table), StatsSetupConst.TOTAL_SIZE));
        builder.setFileNum(getBasicStatForTable(new org.apache.hadoop.hive.ql.metadata.Table(table), StatsSetupConst.NUM_FILES));
        builder.setIsNative(!MetaStoreUtils.isNonNativeTable(table));
        builder.setTableName(tableName);
        builder.setSdInputFormat(table.getSd().getInputFormat());
        builder.setSdOutputFormat(table.getSd().getOutputFormat());
        builder.setOwner(table.getOwner());
        builder.setLastAccessTime(table.getLastAccessTime());
        builder.setTableType(table.getTableType());
        builder.setSkipHeaderLineCount(table.getParameters().get("skip.header.line.count"));

        return builder.createHiveTableMeta();
    }

    @Override
    public List<String> getHiveDbNames() throws Exception {
        return getMetaStoreClient().getAllDatabases();
    }

    @Override
    public List<String> getHiveTableNames(String database) throws Exception {
        return getMetaStoreClient().getAllTables(database);
    }

    @Override
    public long getHiveTableRows(String database, String tableName) throws Exception {
        Table table = getMetaStoreClient().getTable(database, tableName);
        return getBasicStatForTable(new org.apache.hadoop.hive.ql.metadata.Table(table), StatsSetupConst.ROW_COUNT);
    }

    @Override
    public List<Object[]> getHiveResult(String hql) throws Exception {
        List<Object[]> data = new ArrayList<>();

        final HiveCmdBuilder hiveCmdBuilder = new HiveCmdBuilder();
        hiveCmdBuilder.addStatement(hql);
        Pair<Integer, String> response = KylinConfig.getInstanceFromEnv().getCliCommandExecutor().execute(hiveCmdBuilder.toString());

        String[] respData = response.getSecond().split("\n");

        boolean isData = false;

        for (String item : respData) {
            if (item.trim().equalsIgnoreCase("OK")) {
                isData = true;
                continue;
            }
            if (item.trim().startsWith("Time taken")) {
                isData = false;
            }
            if (isData) {
                Object[] arr = item.split("\t");
                data.add(arr);
            }

        }

        return data;
    }

    private IMetaStoreClient getMetaStoreClient() throws Exception {
        if (metaStoreClient == null) {
            metaStoreClient = HiveMetaStoreClientFactory.getHiveMetaStoreClient(hiveConf);
        }
        return metaStoreClient;
    }

    /**
     * COPIED FROM org.apache.hadoop.hive.ql.stats.StatsUtil for backward compatibility
     * <p>
     * Get basic stats of table
     *
     * @param table    - table
     * @param statType - type of stats
     * @return value of stats
     */
    private long getBasicStatForTable(org.apache.hadoop.hive.ql.metadata.Table table, String statType) {
        Map<String, String> params = table.getParameters();
        long result = 0;

        if (params != null) {
            try {
                result = Long.parseLong(params.get(statType));
            } catch (NumberFormatException e) {
                result = 0;
            }
        }
        return result;
    }
}
