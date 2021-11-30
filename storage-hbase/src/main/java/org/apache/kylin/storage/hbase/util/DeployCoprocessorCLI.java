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

package org.apache.kylin.storage.hbase.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.common.KylinVersion;
import org.apache.kylin.common.util.Bytes;
import org.apache.kylin.common.util.Pair;
import org.apache.kylin.cube.CubeInstance;
import org.apache.kylin.cube.CubeManager;
import org.apache.kylin.cube.CubeSegment;
import org.apache.kylin.metadata.model.IStorageAware;
import org.apache.kylin.metadata.model.SegmentStatusEnum;
import org.apache.kylin.metadata.project.ProjectInstance;
import org.apache.kylin.metadata.project.ProjectManager;
import org.apache.kylin.metadata.project.RealizationEntry;
import org.apache.kylin.metadata.realization.IRealizationConstants;
import org.apache.kylin.metadata.realization.RealizationType;
import org.apache.kylin.storage.hbase.HBaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.kylin.shaded.com.google.common.annotations.VisibleForTesting;
import org.apache.kylin.shaded.com.google.common.collect.Lists;

/**
 *
 */
public class DeployCoprocessorCLI {

    public static final String CubeEndpointClass = "org.apache.kylin.storage.hbase.cube.v2.coprocessor.endpoint.CubeVisitService";
    public static final String CubeObserverClassOld = "org.apache.kylin.storage.hbase.coprocessor.observer.AggregateRegionObserver";
    public static final String CubeObserverClassOld2 = "org.apache.kylin.storage.hbase.cube.v1.coprocessor.observer.AggregateRegionObserver";
    public static final String IIEndpointClassOld = "org.apache.kylin.storage.hbase.coprocessor.endpoint.IIEndpoint";
    public static final String IIEndpointClass = "org.apache.kylin.storage.hbase.ii.coprocessor.endpoint.IIEndpoint";

    private static final Logger logger = LoggerFactory.getLogger(DeployCoprocessorCLI.class);

    public static void main(String[] args) throws IOException {
        KylinConfig kylinConfig = KylinConfig.getInstanceFromEnv();
        Configuration hconf = HBaseConnection.getCurrentHBaseConfiguration();
        FileSystem fileSystem = FileSystem.get(hconf);
        Connection conn = HBaseConnection.get(kylinConfig.getStorageUrl());
        Admin hbaseAdmin = null;
        DeployCoprocessorCLIOps ops = parseArgs(args);

        try {
            hbaseAdmin = conn.getAdmin();
            String localCoprocessorJar;
            if ("default".equals(ops.getLocalCoprocessorJar())) {
                localCoprocessorJar = kylinConfig.getCoprocessorLocalJar();
            } else {
                localCoprocessorJar = new File(ops.getLocalCoprocessorJar()).getAbsolutePath();
            }
            logger.info("Identify coprocessor jar " + localCoprocessorJar);
            logger.info("Use at most {} threads to do upgrade", ops.getMaxThreads());

            List<String> tableNames = getHTableNames(kylinConfig);
            logger.info("Identify tables " + tableNames);

            String filterType = ops.getFilterType().toLowerCase(Locale.ROOT);
            if (filterType.equals("-table")) {
                tableNames = filterByTables(tableNames, Arrays.asList(ops.getEntities()));
            } else if (filterType.equals("-cube")) {
                tableNames = filterByCubes(tableNames, Arrays.asList(ops.getEntities()));
            } else if (filterType.equals("-project")) {
                tableNames = filterByProjects(tableNames, Arrays.asList(ops.getEntities()));
            } else if (!filterType.equals("all")) {
                printUsageAndExit();
            }
            logger.info("Tables after filtering by type " + filterType + ": " + tableNames);

            tableNames = filterByGitCommit(hbaseAdmin, tableNames);
            logger.info("Will execute tables " + tableNames);

            long start = System.currentTimeMillis();

            Set<String> oldJarPaths = getCoprocessorJarPaths(hbaseAdmin, tableNames);
            logger.info("Old coprocessor jar: " + oldJarPaths);

            Path hdfsCoprocessorJar = uploadCoprocessorJar(localCoprocessorJar, fileSystem, oldJarPaths);
            logger.info("New coprocessor jar: " + hdfsCoprocessorJar);

            Pair<List<String>, List<String>> results = resetCoprocessorOnHTables(hbaseAdmin, hdfsCoprocessorJar,
                    tableNames, ops.getMaxThreads());

            // Don't remove old jars, missing coprocessor jar will fail hbase
            // removeOldJars(oldJarPaths, fileSystem);

            logger.info("Processed time: " + (System.currentTimeMillis() - start));
            logger.info("Processed tables count: " + results.getFirst().size());
            logger.info("Processed tables: " + results.getFirst());
            logger.error("Failed tables count: " + results.getSecond().size());
            logger.error("Failed tables : " + results.getSecond());
            logger.info("Active coprocessor jar: " + hdfsCoprocessorJar);
        } finally {
            if (hbaseAdmin != null) {
                hbaseAdmin.close();
            }
        }
    }

    @VisibleForTesting
    static DeployCoprocessorCLIOps parseArgs(String[] args) {
        String localCoprocessorJar;
        int maxThreads = 8;
        String filterType;
        String[] entities;

        if (args == null || args.length <= 1) {
            printUsageAndExit();
            return null;
        } else if (StringUtils.isNumeric(args[1])) {
            maxThreads = Integer.parseInt(args[1]);
            filterType = args[2];
            entities = Arrays.copyOfRange(args, 3, args.length);
        } else {
            filterType = args[1];
            entities = Arrays.copyOfRange(args, 2, args.length);
        }

        localCoprocessorJar = args[0];
        if (StringUtils.isEmpty(localCoprocessorJar) || StringUtils.isEmpty(filterType)) {
            printUsageAndExit();
        }

        return new DeployCoprocessorCLIOps(localCoprocessorJar, maxThreads, filterType, entities);
    }

    private static void printUsageAndExit() {
        logger.info("Usage: ");
        logger.info(
                "$KYLIN_HOME/bin/kylin.sh  org.apache.kylin.storage.hbase.util.DeployCoprocessorCLI $KYLIN_HOME/lib/kylin-coprocessor-*.jar [nOfThread] all");
        logger.info(
                "$KYLIN_HOME/bin/kylin.sh  org.apache.kylin.storage.hbase.util.DeployCoprocessorCLI $KYLIN_HOME/lib/kylin-coprocessor-*.jar [nOfThread] -table tableName1 tableName2 ...");
        logger.info(
                "$KYLIN_HOME/bin/kylin.sh  org.apache.kylin.storage.hbase.util.DeployCoprocessorCLI $KYLIN_HOME/lib/kylin-coprocessor-*.jar [nOfThread] -cube cubeName1 cubeName2 ... ");
        logger.info(
                "$KYLIN_HOME/bin/kylin.sh  org.apache.kylin.storage.hbase.util.DeployCoprocessorCLI $KYLIN_HOME/lib/kylin-coprocessor-*.jar [nOfThread] -project projectName1 projectName2 ...");
        System.exit(0);
    }

    private static List<String> filterByGitCommit(Admin hbaseAdmin, List<String> tableNames) throws IOException {
        List<String> result = Lists.newLinkedList();
        List<String> filteredList = Lists.newLinkedList();

        String commitInfo = KylinVersion.getGitCommitInfo();
        if (StringUtils.isEmpty(commitInfo)) {
            return tableNames;
        }
        logger.info("Commit Information: " + commitInfo);
        int skipTableCnt = 0;
        for (String tableName : tableNames) {
            if (!hbaseAdmin.isTableAvailable(TableName.valueOf(tableName))) {
                logger.warn("Table: " + tableName + " is not available currently, skip it");
                skipTableCnt ++;
                continue;
            }
            HTableDescriptor tableDesc = hbaseAdmin.getTableDescriptor(TableName.valueOf(tableName));
            String gitTag = tableDesc.getValue(IRealizationConstants.HTableGitTag);
            if (commitInfo.equals(gitTag)) {
                filteredList.add(tableName);
            } else {
                result.add(tableName);
            }
        }
        logger.info("Skip {} tables for not founding in HBase Cluster", skipTableCnt);
        logger.info("Filtered tables don't need to deploy coprocessors: " + filteredList);
        return result;
    }

    private static List<String> filterByProjects(List<String> allTableNames, List<String> projectNames) {
        ProjectManager projectManager = ProjectManager.getInstance(KylinConfig.getInstanceFromEnv());
        CubeManager cubeManager = CubeManager.getInstance(KylinConfig.getInstanceFromEnv());

        List<String> result = Lists.newArrayList();
        for (String p : projectNames) {
            p = p.trim();
            if (p.endsWith(",")) {
                p = p.substring(0, p.length() - 1);
            }

            ProjectInstance projectInstance = projectManager.getProject(p);
            List<RealizationEntry> cubeList = projectInstance.getRealizationEntries(RealizationType.CUBE);
            for (RealizationEntry cube : cubeList) {
                CubeInstance cubeInstance = cubeManager.getCube(cube.getRealization());
                for (CubeSegment segment : cubeInstance.getSegments()) {
                    String tableName = segment.getStorageLocationIdentifier();
                    if (allTableNames.contains(tableName)) {
                        result.add(tableName);
                    }
                }
            }
        }
        return result;
    }

    private static List<String> filterByCubes(List<String> allTableNames, List<String> cubeNames) {
        CubeManager cubeManager = CubeManager.getInstance(KylinConfig.getInstanceFromEnv());
        List<String> result = Lists.newArrayList();
        for (String c : cubeNames) {
            c = c.trim();
            if (c.endsWith(","))
                c = c.substring(0, c.length() - 1);

            CubeInstance cubeInstance = cubeManager.getCube(c);
            for (CubeSegment segment : cubeInstance.getSegments()) {
                String tableName = segment.getStorageLocationIdentifier();
                if (allTableNames.contains(tableName)) {
                    result.add(tableName);
                }
            }
        }
        return result;
    }

    private static List<String> filterByTables(List<String> allTableNames, List<String> tableNames) {
        List<String> result = Lists.newArrayList();
        for (String t : tableNames) {
            t = t.trim();
            if (t.endsWith(","))
                t = t.substring(0, t.length() - 1);

            if (allTableNames.contains(t)) {
                result.add(t);
            }
        }
        return result;
    }

    public static void deployCoprocessor(HTableDescriptor tableDesc) {
        try {
            initHTableCoprocessor(tableDesc);
            logger.info("hbase table " + tableDesc.getTableName() + " deployed with coprocessor.");

        } catch (Exception ex) {
            logger.error("Error deploying coprocessor on " + tableDesc.getTableName(), ex);
            logger.error("Will try creating the table without coprocessor.");
        }
    }

    private static void initHTableCoprocessor(HTableDescriptor desc) throws IOException {
        KylinConfig kylinConfig = KylinConfig.getInstanceFromEnv();
        Configuration hconf = HBaseConnection.getCurrentHBaseConfiguration();
        FileSystem fileSystem = FileSystem.get(hconf);

        String localCoprocessorJar = kylinConfig.getCoprocessorLocalJar();
        Path hdfsCoprocessorJar = DeployCoprocessorCLI.uploadCoprocessorJar(localCoprocessorJar, fileSystem, null);

        DeployCoprocessorCLI.addCoprocessorOnHTable(desc, hdfsCoprocessorJar);
    }

    public static void addCoprocessorOnHTable(HTableDescriptor desc, Path hdfsCoprocessorJar) throws IOException {
        logger.info("Add coprocessor on " + desc.getNameAsString());
        desc.addCoprocessor(CubeEndpointClass, hdfsCoprocessorJar, 1001, null);
    }

    public static boolean resetCoprocessor(String tableName, Admin hbaseAdmin, Path hdfsCoprocessorJar)
            throws IOException {
        KylinConfig kylinConfig = KylinConfig.getInstanceFromEnv();
        HTableDescriptor desc = hbaseAdmin.getTableDescriptor(TableName.valueOf(tableName));

        //when the table has migrated from dev env to test(prod) env, the dev server
        //should not reset the coprocessor of the table.
        String host = desc.getValue(IRealizationConstants.HTableTag);
        if (!host.equalsIgnoreCase(kylinConfig.getMetadataUrlPrefix())) {
            logger.warn("This server doesn't own this table: " + tableName);
            return false;
        }

        logger.info("reset coprocessor on " + tableName);

        logger.info("Disable " + tableName);
        if (hbaseAdmin.isTableEnabled(TableName.valueOf(tableName))) {
            hbaseAdmin.disableTable(TableName.valueOf(tableName));
        }

        while (desc.hasCoprocessor(CubeObserverClassOld2)) {
            desc.removeCoprocessor(CubeObserverClassOld2);
        }
        while (desc.hasCoprocessor(CubeEndpointClass)) {
            desc.removeCoprocessor(CubeEndpointClass);
        }
        while (desc.hasCoprocessor(IIEndpointClass)) {
            desc.removeCoprocessor(IIEndpointClass);
        }
        // remove legacy coprocessor from v1.x
        while (desc.hasCoprocessor(CubeObserverClassOld)) {
            desc.removeCoprocessor(CubeObserverClassOld);
        }
        while (desc.hasCoprocessor(IIEndpointClassOld)) {
            desc.removeCoprocessor(IIEndpointClassOld);
        }
        addCoprocessorOnHTable(desc, hdfsCoprocessorJar);

        // update commit tags
        String commitInfo = KylinVersion.getGitCommitInfo();
        if (!StringUtils.isEmpty(commitInfo)) {
            desc.setValue(IRealizationConstants.HTableGitTag, commitInfo);
        }

        hbaseAdmin.modifyTable(TableName.valueOf(tableName), desc);

        logger.info("Enable " + tableName);
        hbaseAdmin.enableTable(TableName.valueOf(tableName));

        return true;
    }

    private static Pair<List<String>, List<String>> resetCoprocessorOnHTables(final Admin hbaseAdmin,
            final Path hdfsCoprocessorJar, List<String> tableNames, int maxThreads) throws IOException {
        List<String> processedTables = Collections.synchronizedList(new ArrayList<String>());
        List<String> failedTables = Collections.synchronizedList(new ArrayList<String>());

        int nThread = Runtime.getRuntime().availableProcessors() * 2;
        if (nThread > maxThreads) {
            nThread = maxThreads;
        }
        logger.info("Use {} threads to do upgrade", nThread);
        ExecutorService coprocessorPool = Executors.newFixedThreadPool(nThread);
        CountDownLatch countDownLatch = new CountDownLatch(tableNames.size());

        for (final String tableName : tableNames) {
            coprocessorPool.execute(new ResetCoprocessorWorker(countDownLatch, hbaseAdmin, hdfsCoprocessorJar,
                    tableName, processedTables, failedTables));
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            logger.error("reset coprocessor failed: ", e);
        }

        coprocessorPool.shutdown();
        return new Pair<>(processedTables, failedTables);
    }

    private static class ResetCoprocessorWorker implements Runnable {
        private final CountDownLatch countDownLatch;
        private final Admin hbaseAdmin;
        private final Path hdfsCoprocessorJar;
        private final String tableName;
        private final List<String> processedTables;
        private final List<String> failedTables;

        public ResetCoprocessorWorker(CountDownLatch countDownLatch, Admin hbaseAdmin, Path hdfsCoprocessorJar,
                String tableName, List<String> processedTables, List<String> failedTables) {

            this.countDownLatch = countDownLatch;
            this.hbaseAdmin = hbaseAdmin;
            this.hdfsCoprocessorJar = hdfsCoprocessorJar;
            this.tableName = tableName;
            this.processedTables = processedTables;
            this.failedTables = failedTables;
        }

        @Override
        public void run() {
            try {
                boolean isProcessed = resetCoprocessor(tableName, hbaseAdmin, hdfsCoprocessorJar);
                if (isProcessed) {
                    processedTables.add(tableName);
                } else {
                    failedTables.add(tableName);
                }
            } catch (Exception ex) {
                failedTables.add(tableName);
                logger.error("Error processing " + tableName, ex);
            } finally {
                countDownLatch.countDown();
            }

        }
    }

    public static Path getNewestCoprocessorJar(KylinConfig config, FileSystem fileSystem) throws IOException {
        Path coprocessorDir = getCoprocessorHDFSDir(fileSystem, config);
        FileStatus newestJar = null;
        for (FileStatus fileStatus : fileSystem.listStatus(coprocessorDir)) {
            if (fileStatus.getPath().toString().endsWith(".jar")) {
                if (newestJar == null) {
                    newestJar = fileStatus;
                } else {
                    if (newestJar.getModificationTime() < fileStatus.getModificationTime())
                        newestJar = fileStatus;
                }
            }
        }
        if (newestJar == null)
            return null;

        Path path = newestJar.getPath().makeQualified(fileSystem.getUri(), null);
        logger.info("The newest coprocessor is " + path.toString());
        return path;
    }

    public static synchronized Path uploadCoprocessorJar(String localCoprocessorJar, FileSystem fileSystem,
            Set<String> oldJarPaths) throws IOException {
        Path uploadPath = null;
        File localCoprocessorFile = new File(localCoprocessorJar);

        // check existing jars
        if (oldJarPaths == null) {
            oldJarPaths = new HashSet<String>();
        }
        Path coprocessorDir = getCoprocessorHDFSDir(fileSystem, KylinConfig.getInstanceFromEnv());
        for (FileStatus fileStatus : fileSystem.listStatus(coprocessorDir)) {
            if (isSame(localCoprocessorFile, fileStatus)) {
                uploadPath = fileStatus.getPath();
                break;
            }
            String filename = fileStatus.getPath().toString();
            if (filename.endsWith(".jar")) {
                oldJarPaths.add(filename);
            }
        }

        // upload if not existing
        if (uploadPath == null) {
            // figure out a unique new jar file name
            Set<String> oldJarNames = new HashSet<String>();
            for (String path : oldJarPaths) {
                oldJarNames.add(new Path(path).getName());
            }
            String baseName = getBaseFileName(localCoprocessorJar);
            String newName = null;
            int i = 0;
            while (newName == null) {
                newName = baseName + "-" + (i++) + ".jar";
                if (oldJarNames.contains(newName))
                    newName = null;
            }

            // upload
            uploadPath = new Path(coprocessorDir, newName);
            FileInputStream in = null;
            FSDataOutputStream out = null;
            try {
                in = new FileInputStream(localCoprocessorFile);
                out = fileSystem.create(uploadPath);
                IOUtils.copy(in, out);
            } finally {
                IOUtils.closeQuietly(in);
                IOUtils.closeQuietly(out);
            }

            fileSystem.setTimes(uploadPath, localCoprocessorFile.lastModified(), -1);

        }

        uploadPath = uploadPath.makeQualified(fileSystem.getUri(), null);
        return uploadPath;
    }

    private static boolean isSame(File localCoprocessorFile, FileStatus fileStatus) {
        return fileStatus.getLen() == localCoprocessorFile.length()
                && fileStatus.getModificationTime() == localCoprocessorFile.lastModified();
    }

    private static String getBaseFileName(String localCoprocessorJar) {
        File localJar = new File(localCoprocessorJar);
        String baseName = localJar.getName();
        if (baseName.endsWith(".jar"))
            baseName = baseName.substring(0, baseName.length() - ".jar".length());
        return baseName;
    }

    private static Path getCoprocessorHDFSDir(FileSystem fileSystem, KylinConfig config) throws IOException {
        String hdfsWorkingDirectory = config.getHdfsWorkingDirectory();
        hdfsWorkingDirectory = HBaseConnection.makeQualifiedPathInHBaseCluster(hdfsWorkingDirectory);
        Path coprocessorDir = new Path(hdfsWorkingDirectory, "coprocessor");
        fileSystem.mkdirs(coprocessorDir);
        return coprocessorDir;
    }

    private static Set<String> getCoprocessorJarPaths(Admin hbaseAdmin, List<String> tableNames) throws IOException {
        HashSet<String> result = new HashSet<String>();

        for (String tableName : tableNames) {
            HTableDescriptor tableDescriptor = null;
            try {
                tableDescriptor = hbaseAdmin.getTableDescriptor(TableName.valueOf(tableName));
            } catch (TableNotFoundException e) {
                logger.warn("Table not found " + tableName, e);
                continue;
            }

            Matcher keyMatcher;
            Matcher valueMatcher;
            for (Map.Entry<ImmutableBytesWritable, ImmutableBytesWritable> e : tableDescriptor.getValues().entrySet()) {
                keyMatcher = HConstants.CP_HTD_ATTR_KEY_PATTERN.matcher(Bytes.toString(e.getKey().get()));
                if (!keyMatcher.matches()) {
                    continue;
                }
                valueMatcher = HConstants.CP_HTD_ATTR_VALUE_PATTERN.matcher(Bytes.toString(e.getValue().get()));
                if (!valueMatcher.matches()) {
                    continue;
                }

                String jarPath = valueMatcher.group(1).trim();
                if (StringUtils.isNotEmpty(jarPath)) {
                    result.add(jarPath);
                }
            }
        }

        return result;
    }

    private static List<String> getHTableNames(KylinConfig config) {
        CubeManager cubeMgr = CubeManager.getInstance(config);

        ArrayList<String> result = new ArrayList<String>();
        for (CubeInstance cube : cubeMgr.listAllCubes()) {
            if (cube.getStorageType() == IStorageAware.ID_HBASE
                    || cube.getStorageType() == IStorageAware.ID_SHARDED_HBASE
                    || cube.getStorageType() == IStorageAware.ID_REALTIME_AND_HBASE) {
                for (CubeSegment seg : cube.getSegments(SegmentStatusEnum.READY)) {
                    String tableName = seg.getStorageLocationIdentifier();
                    if (StringUtils.isBlank(tableName) == false) {
                        result.add(tableName);
                        System.out.println("added new table: " + tableName);
                    }
                }
            }
        }

        return result;
    }
}
