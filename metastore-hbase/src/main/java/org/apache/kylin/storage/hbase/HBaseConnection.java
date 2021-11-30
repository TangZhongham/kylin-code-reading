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

package org.apache.kylin.storage.hbase;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.util.Threads;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.common.StorageURL;
import org.apache.kylin.common.lock.DistributedLock;
import org.apache.kylin.common.threadlocal.InternalThreadLocal;
import org.apache.kylin.common.util.HadoopUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

/**
 * @author yangli9
 * 
 */
public class HBaseConnection {

    public static final String HTABLE_UUID_TAG = "UUID";

    private static final Logger logger = LoggerFactory.getLogger(HBaseConnection.class);

    private static final Map<StorageURL, Configuration> configCache = new ConcurrentHashMap<StorageURL, Configuration>();
    private static final Map<StorageURL, Connection> connPool = new ConcurrentHashMap<StorageURL, Connection>();
    private static final InternalThreadLocal<Configuration> configThreadLocal = new InternalThreadLocal<>();

    private static ExecutorService coprocessorPool = null;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    closeCoprocessorPool();
                    closeAndRestConnPool().join();
                } catch (InterruptedException e) {
                    logger.error("", e);
                }
            }
        });
    }

    public static ExecutorService getCoprocessorPool() {
        if (coprocessorPool != null) {
            return coprocessorPool;
        }

        synchronized (HBaseConnection.class) {
            if (coprocessorPool != null) {
                return coprocessorPool;
            }

            KylinConfig config = KylinConfig.getInstanceFromEnv();

            // copy from HConnectionImplementation.getBatchPool()
            int maxThreads = config.getHBaseMaxConnectionThreads();
            int coreThreads = config.getHBaseCoreConnectionThreads();
            long keepAliveTime = config.getHBaseConnectionThreadPoolAliveSeconds();
            LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(maxThreads * 100);
            ThreadPoolExecutor tpe = new ThreadPoolExecutor(coreThreads, maxThreads, keepAliveTime, TimeUnit.SECONDS, workQueue, //
                    Threads.newDaemonThreadFactory("kylin-coproc-"));
            tpe.allowCoreThreadTimeOut(true);

            logger.info("Creating coprocessor thread pool with max of {}, core of {}", maxThreads, coreThreads);

            coprocessorPool = tpe;
            return coprocessorPool;
        }
    }

    private static void closeCoprocessorPool() {
        if (coprocessorPool == null)
            return;

        coprocessorPool.shutdown();
        try {
            if (!coprocessorPool.awaitTermination(10, TimeUnit.SECONDS)) {
                coprocessorPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            coprocessorPool.shutdownNow();
        }
    }
    
    private static Thread closeAndRestConnPool() {
        final List<Connection> copy = new ArrayList<>(connPool.values());
        connPool.clear();
        
        Thread t = new Thread() {
            public void run() {
                logger.info("Closing HBase connections...");
                for (Connection conn : copy) {
                    try {
                        conn.close();
                    } catch (Exception e) {
                        logger.error("error closing hbase connection " + conn, e);
                    }
                }
            }
        };
        t.setName("close-hbase-conn");
        t.start();
        return t;
    }

    public static void clearConnCache() {
        closeAndRestConnPool();
    }

    public static Configuration getCurrentHBaseConfiguration() {
        if (configThreadLocal.get() == null) {
            StorageURL storageUrl = KylinConfig.getInstanceFromEnv().getStorageUrl();
            configThreadLocal.set(newHBaseConfiguration(storageUrl));
        }
        return configThreadLocal.get();
    }

    private static Configuration newHBaseConfiguration(StorageURL url) {
        // using a hbase:xxx URL is deprecated, instead hbase config is always loaded from hbase-site.xml in classpath
        if (!"hbase".equals(url.getScheme()))
            throw new IllegalArgumentException("to use hbase storage, pls set 'kylin.storage.url=hbase' in kylin.properties");

        Configuration conf = HBaseConfiguration.create(HadoopUtil.getCurrentConfiguration());
        addHBaseClusterNNHAConfiguration(conf);

        // support hbase using a different FS
        KylinConfig kylinConf = KylinConfig.getInstanceFromEnv();
        String hbaseClusterFs = kylinConf.getHBaseClusterFs();
        if (StringUtils.isNotEmpty(hbaseClusterFs)) {
            conf.set(FileSystem.FS_DEFAULT_NAME_KEY, hbaseClusterFs);
        } else {
            try {
                FileSystem fs = HadoopUtil.getWorkingFileSystem(HadoopUtil.getCurrentConfiguration());
                conf.set(FileSystem.FS_DEFAULT_NAME_KEY, fs.getUri().toString());
                logger.debug("Using the working dir FS for HBase: " + fs.getUri().toString());
            } catch (IOException e) {
                logger.error("Fail to set working dir to HBase configuration", e);
            }
        }

        // https://issues.apache.org/jira/browse/KYLIN-953
        if (StringUtils.isBlank(conf.get("hadoop.tmp.dir"))) {
            conf.set("hadoop.tmp.dir", "/tmp");
        }
        if (StringUtils.isBlank(conf.get("hbase.fs.tmp.dir"))) {
            conf.set("hbase.fs.tmp.dir", "/tmp");
        }
        
        for (Entry<String, String> entry : url.getAllParameters().entrySet()) {
            conf.set(entry.getKey(), entry.getValue());
        }

        return conf;
    }

    // See YARN-3021. Copy here in case of missing in dependency MR client jars
    public static final String JOB_NAMENODES_TOKEN_RENEWAL_EXCLUDE = "mapreduce.job.hdfs-servers.token-renewal.exclude";

    public static void addHBaseClusterNNHAConfiguration(Configuration conf) {
        String hdfsConfigFile = KylinConfig.getInstanceFromEnv().getHBaseClusterHDFSConfigFile();
        if (hdfsConfigFile == null || hdfsConfigFile.isEmpty()) {
            return;
        }
        Configuration hdfsConf = new Configuration(false);
        hdfsConf.addResource(hdfsConfigFile);
        Collection<String> nameServices = hdfsConf.getTrimmedStringCollection(DFSConfigKeys.DFS_NAMESERVICES);
        Collection<String> mainNameServices = conf.getTrimmedStringCollection(DFSConfigKeys.DFS_NAMESERVICES);
        for (String serviceId : nameServices) {
            mainNameServices.add(serviceId);

            String serviceConfKey = DFSConfigKeys.DFS_HA_NAMENODES_KEY_PREFIX + "." + serviceId;
            String proxyConfKey = DFSConfigKeys.DFS_CLIENT_FAILOVER_PROXY_PROVIDER_KEY_PREFIX + "." + serviceId;
            conf.set(serviceConfKey, hdfsConf.get(serviceConfKey, ""));
            conf.set(proxyConfKey, hdfsConf.get(proxyConfKey, ""));

            Collection<String> nameNodes = hdfsConf.getTrimmedStringCollection(serviceConfKey);
            for (String nameNode : nameNodes) {
                String rpcConfKey = DFSConfigKeys.DFS_NAMENODE_RPC_ADDRESS_KEY + "." + serviceId + "." + nameNode;
                conf.set(rpcConfKey, hdfsConf.get(rpcConfKey, ""));
            }
        }
        conf.setStrings(DFSConfigKeys.DFS_NAMESERVICES, mainNameServices.toArray(new String[0]));
        // See YARN-3021, instruct RM skip renew token of hbase cluster name services
        conf.setStrings(JOB_NAMENODES_TOKEN_RENEWAL_EXCLUDE, nameServices.toArray(new String[0]));
    }

    public static String makeQualifiedPathInHBaseCluster(String inPath) {
        Path path = new Path(inPath);
        path = Path.getPathWithoutSchemeAndAuthority(path);

        FileSystem fs = HadoopUtil.getFileSystem(path, getCurrentHBaseConfiguration()); // Must be HBase's FS, not working FS
        return fs.makeQualified(path).toString();
    }

    public static FileSystem getFileSystemInHBaseCluster(String inPath) {
        Path path = new Path(inPath);
        path = Path.getPathWithoutSchemeAndAuthority(path);

        FileSystem fs = HadoopUtil.getFileSystem(path, getCurrentHBaseConfiguration()); // Must be HBase's FS, not working FS
        return fs;
    }


    // ============================================================================

    // returned Connection can be shared by multiple threads and does not require close()
    @SuppressWarnings("resource")
    public static Connection get(StorageURL url) {
        // find configuration
        Configuration conf = configCache.get(url);
        if (conf == null) {
            conf = newHBaseConfiguration(url);
            configCache.put(url, conf);
        }

        Connection connection = connPool.get(url);
        try {
            while (true) {
                // I don't use DCL since recreate a connection is not a big issue.
                if (connection == null || connection.isClosed()) {
                    logger.info("connection is null or closed, creating a new one");
                    connection = ConnectionFactory.createConnection(conf);
                    connPool.put(url, connection);
                }

                if (connection == null || connection.isClosed()) {
                    Thread.sleep(10000);// wait a while and retry
                } else {
                    break;
                }
            }

        } catch (Throwable t) {
            logger.error("Error when open connection " + url, t);
            throw new RuntimeException("Error when open connection " + url, t);
        }

        return connection;
    }

    public static boolean tableExists(Connection conn, String tableName) throws IOException {
        Admin hbase = conn.getAdmin();
        try {
            return hbase.tableExists(TableName.valueOf(tableName));
        } finally {
            hbase.close();
        }
    }

    public static boolean tableExists(StorageURL hbaseUrl, String tableName) throws IOException {
        return tableExists(HBaseConnection.get(hbaseUrl), tableName);
    }

    public static void createHTableIfNeeded(StorageURL hbaseUrl, String tableName, String... families) throws IOException {
        createHTableIfNeeded(HBaseConnection.get(hbaseUrl), tableName, families);
    }

    public static void deleteTable(StorageURL hbaseUrl, String tableName) throws IOException {
        deleteTable(HBaseConnection.get(hbaseUrl), tableName);
    }

    public static void createHTableIfNeeded(Connection conn, String table, String... families) throws IOException {
        Admin admin = conn.getAdmin();
        TableName tableName = TableName.valueOf(table);
        DistributedLock lock = null;
        String lockPath = getLockPath(table);
        
        try {
            if (tableExists(conn, table)) {
                logger.debug("HTable '" + table + "' already exists");
                Set<String> existingFamilies = getFamilyNames(admin.getTableDescriptor(tableName));
                boolean wait = false;
                for (String family : families) {
                    if (existingFamilies.contains(family) == false) {
                        logger.debug("Adding family '" + family + "' to HTable '" + table + "'");
                        admin.addColumn(tableName, newFamilyDescriptor(family));
                        // addColumn() is async, is there a way to wait it finish?
                        wait = true;
                    }
                }
                if (wait) {
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        logger.warn("", e);
                    }
                }
                return;
            }

            lock = KylinConfig.getInstanceFromEnv().getDistributedLockFactory().lockForCurrentProcess();
            if (!lock.lock(lockPath, Long.MAX_VALUE))
                throw new RuntimeException("Cannot acquire lock to create HTable " + table);

            if (tableExists(conn, table)) {
                logger.debug("HTable '" + table + "' already exists");
                return;
            }

            logger.debug("Creating HTable '" + table + "'");

            HTableDescriptor desc = new HTableDescriptor(TableName.valueOf(table));

            if (null != families && families.length > 0) {
                for (String family : families) {
                    HColumnDescriptor fd = newFamilyDescriptor(family);
                    desc.addFamily(fd);
                }
            }

            admin.createTable(desc);

            logger.debug("HTable '" + table + "' created");
        } finally {
            admin.close();
            if (lock != null && lock.isLockedByMe(lockPath))
                lock.unlock(lockPath);
        }
    }

    private static Set<String> getFamilyNames(HTableDescriptor desc) {
        HashSet<String> result = Sets.newHashSet();
        for (byte[] bytes : desc.getFamiliesKeys()) {
            try {
                result.add(new String(bytes, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                logger.error(e.toString());
            }
        }
        return result;
    }

    private static HColumnDescriptor newFamilyDescriptor(String family) {
        HColumnDescriptor fd = new HColumnDescriptor(family);
        fd.setInMemory(true); // metadata tables are best in memory
        return fd;
    }

    public static void deleteTable(Connection conn, String tableName) throws IOException {
        Admin hbase = conn.getAdmin();

        try {
            if (!tableExists(conn, tableName)) {
                logger.debug("HTable '" + tableName + "' does not exists");
                return;
            }

            logger.debug("delete HTable '" + tableName + "'");

            if (hbase.isTableEnabled(TableName.valueOf(tableName))) {
                hbase.disableTable(TableName.valueOf(tableName));
            }
            hbase.deleteTable(TableName.valueOf(tableName));

            logger.debug("HTable '" + tableName + "' deleted");
        } finally {
            hbase.close();
        }
    }

    private static String getLockPath(String table) {
        return "/create_htable/" + table + "/lock";
    }

}
