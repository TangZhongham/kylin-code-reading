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

package org.apache.kylin.query.adhoc;

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.kylin.common.KylinConfig;

public class JdbcPushDownConnectionManager {

    private volatile static JdbcPushDownConnectionManager manager = null;

    static JdbcPushDownConnectionManager getConnectionManager(String id) throws ClassNotFoundException {
        if (manager == null) {
            synchronized (JdbcPushDownConnectionManager.class) {
                if (manager == null) {
                    manager = new JdbcPushDownConnectionManager(KylinConfig.getInstanceFromEnv(),
                            id);
                }
            }
        }
        return manager;
    }

    private final BasicDataSource dataSource;

    private JdbcPushDownConnectionManager(KylinConfig config, String id) throws ClassNotFoundException {
        dataSource = new BasicDataSource();

        Class.forName(config.getJdbcDriverClass(id));
        dataSource.setDriverClassName(config.getJdbcDriverClass(id));
        dataSource.setUrl(config.getJdbcUrl(id));
        dataSource.setUsername(config.getJdbcUsername(id));
        dataSource.setPassword(config.getJdbcPassword(id));
        dataSource.setMaxActive(config.getPoolMaxTotal(id));
        dataSource.setMaxIdle(config.getPoolMaxIdle(id));
        dataSource.setMinIdle(config.getPoolMinIdle(id));

        // Default settings
        dataSource.setTestOnBorrow(true);
        dataSource.setValidationQuery("select 1");
        dataSource.setRemoveAbandoned(true);
        dataSource.setRemoveAbandonedTimeout(300);
    }

    public void close() {
        try {
            dataSource.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void close(Connection conn) {
        try {
            conn.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
