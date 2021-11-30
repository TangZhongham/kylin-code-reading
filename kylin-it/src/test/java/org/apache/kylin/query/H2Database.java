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

package org.apache.kylin.query;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.kylin.shaded.com.google.common.base.Charsets;
import org.apache.kylin.shaded.com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.metadata.TableMetadataManager;
import org.apache.kylin.metadata.model.ColumnDesc;
import org.apache.kylin.metadata.model.TableDesc;
import org.apache.kylin.source.datagen.ColumnGenConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.kylin.shaded.com.google.common.collect.Lists;

public class H2Database {
    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(H2Database.class);

    private static final String[] ALL_TABLES = new String[] { //
            "edw.test_cal_dt", //
            "default.test_category_groupings", //
            "default.test_kylin_fact", //
            "default.test_order", //
            "edw.test_seller_type_dim", //
            "edw.test_sites", //
            "default.test_account", //
            "default.test_country", //
            "default.streaming_table", //
            "default.streaming_category",
            "default.streaming_v2_user_info_table"};
    private static final Map<String, String> javaToH2DataTypeMapping = new HashMap<String, String>();

    static {
        javaToH2DataTypeMapping.put("short", "smallint");
        javaToH2DataTypeMapping.put("long", "bigint");
        javaToH2DataTypeMapping.put("byte", "tinyint");
        javaToH2DataTypeMapping.put("string", "varchar");
    }

    private final Connection h2Connection;
    private final KylinConfig config;
    private final String project;

    public H2Database(Connection h2Connection, KylinConfig config, String prj) {
        this.h2Connection = h2Connection;
        this.config = config;
        this.project = prj;
    }

    public void loadAllTables() throws SQLException {
        for (String tableName : ALL_TABLES) {
            loadH2Table(tableName);
        }
    }

    private void loadH2Table(String tableName) throws SQLException {
        TableMetadataManager metaMgr = TableMetadataManager.getInstance(config);
        TableDesc tableDesc = metaMgr.getTableDesc(tableName.toUpperCase(Locale.ROOT), project);
        File tempFile = null;

        try {
            tempFile = File.createTempFile("tmp_h2", ".csv");
            FileOutputStream tempFileStream = new FileOutputStream(tempFile);
            String path = path(tableDesc);
            InputStream csvStream = metaMgr.getStore().getResource(path).content();

            IOUtils.copy(csvStream, tempFileStream);

            csvStream.close();
            tempFileStream.close();

            String content = Files.toString(tempFile, Charsets.UTF_8);
            Files.write(convertNull(content, tableDesc, "ZXDNULL"), tempFile, Charsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String cvsFilePath = tempFile.getPath();
        Statement stmt = h2Connection.createStatement();

        String createDBSql = "CREATE SCHEMA IF NOT EXISTS DEFAULT;\nCREATE SCHEMA IF NOT EXISTS EDW;\nSET SCHEMA DEFAULT;\n";
        stmt.executeUpdate(createDBSql);

        String sql = generateCreateH2TableSql(tableDesc, cvsFilePath, "ZXDNULL");
        stmt.executeUpdate(sql);

        List<String> createIndexStatements = generateCreateH2IndexSql(tableDesc);
        for (String indexSql : createIndexStatements) {
            stmt.executeUpdate(indexSql);
        }

        if (tempFile != null)
            tempFile.delete();
    }

    private String path(TableDesc tableDesc) {
        if ("EDW.TEST_SELLER_TYPE_DIM".equals(tableDesc.getIdentity())) // it is a view of table below
            return "/data/" + "EDW.TEST_SELLER_TYPE_DIM_TABLE" + ".csv";
        else
            return "/data/" + tableDesc.getIdentity() + ".csv";
    }

    private String generateCreateH2TableSql(TableDesc tableDesc, String csvFilePath, String nullString) {
        StringBuilder ddl = new StringBuilder();
        StringBuilder csvColumns = new StringBuilder();

        ddl.append("CREATE TABLE " + tableDesc.getIdentity() + "\n");
        ddl.append("(" + "\n");

        for (int i = 0; i < tableDesc.getColumns().length; i++) {
            ColumnDesc col = tableDesc.getColumns()[i];
            if (i > 0) {
                ddl.append(",");
                csvColumns.append(",");
            }
            ddl.append(col.getName() + " " + getH2DataType((col.getDatatype())) + "\n");
            csvColumns.append(col.getName());
        }
        ddl.append(")" + "\n");
        ddl.append("AS SELECT * FROM CSVREAD('" + csvFilePath + "', '" + csvColumns + "', 'null=" + nullString
            + " charset=UTF-8 fieldSeparator=,');");

        return ddl.toString();
    }

    private List<String> generateCreateH2IndexSql(TableDesc tableDesc) {
        List<String> result = Lists.newArrayList();
        int x = 0;
        for (ColumnDesc col : tableDesc.getColumns()) {
            if ("T".equalsIgnoreCase(col.getIndex())) {
                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE INDEX IDX_" + tableDesc.getName() + "_" + x + " ON " + tableDesc.getIdentity() + "(" + col.getName() + ")");
                ddl.append("\n");
                result.add(ddl.toString());
                x++;
            }
        }

        return result;
    }

    private static String getH2DataType(String javaDataType) {
        String hiveDataType = javaToH2DataTypeMapping.get(javaDataType.toLowerCase(Locale.ROOT));
        if (hiveDataType == null) {
            hiveDataType = javaDataType;
        }
        return hiveDataType.toLowerCase(Locale.ROOT);
    }

    private String convertNull(String content, TableDesc tableDesc, String nullValue) {
        StringBuffer sb = new StringBuffer();

        String[] nullStrings = new String[tableDesc.getColumns().length];
        for (int i = 0; i < tableDesc.getColumns().length; ++i) {
            ColumnDesc columnDesc = tableDesc.getColumns()[i];
            if (ColumnGenConfig.isNullable(columnDesc)) {
                nullStrings[i] = ColumnGenConfig.getNullStr(columnDesc);
            } else {
                nullStrings[i] = null;
            }
        }

        String[] lines = content.split("\\n");
        for (int j = 0; j < lines.length; ++j) {
            String line = lines[j];
            String[] columnValues = line.split(",", -1);
            for (int i = 0; i < columnValues.length; ++i) {
                String columnValue = columnValues[i];
                if (nullStrings[i] != null && nullStrings[i].equals(columnValue)) {
                    columnValue = nullValue;
                }
                sb.append(columnValue);
                if (i < columnValues.length - 1) {
                    sb.append(",");
                }
            }
            if (j < lines.length - 1) {
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}
