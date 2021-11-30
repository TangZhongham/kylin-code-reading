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
package org.apache.kylin.source.jdbc.metadata;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.kylin.common.SourceDialect;
import org.apache.kylin.source.hive.DBConnConf;
import org.apache.kylin.source.jdbc.SqlUtil;

public class MySQLJdbcMetadata extends DefaultJdbcMetadata {
    public MySQLJdbcMetadata(DBConnConf dbConnConf) {
        super(dbConnConf);
    }

    @Override
    public List<String> listDatabases() throws SQLException {
        List<String> ret = new ArrayList<>();
        try (Connection con = SqlUtil.getConnection(dbconf)) {
            ret.add(con.getCatalog());
        }
        return ret;
    }

    @Override
    public List<String> listTables(String catalog) throws SQLException {
        List<String> ret = new ArrayList<>();
        try (Connection con = SqlUtil.getConnection(dbconf);
                ResultSet res = con.getMetaData().getTables(catalog, null, null, null)) {
            String table;
            while (res.next()) {
                table = res.getString("TABLE_NAME");
                ret.add(table);
            }
        }
        return ret;
    }

    @Override
    public ResultSet listColumns(final DatabaseMetaData dbmd, String catalog, String table) throws SQLException {
        return dbmd.getColumns(catalog, null, table, null);
    }

    @Override
    public ResultSet getTable(final DatabaseMetaData dbmd, String catalog, String table) throws SQLException {
        return dbmd.getTables(catalog, null, table, null);
    }

    @Override
    public SourceDialect getDialect() {
        return SourceDialect.MYSQL;
    }
}
