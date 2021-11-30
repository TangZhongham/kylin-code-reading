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

/** This class will come with HBase 2.0 in package org.apache.hadoop.hbase.util **/
package org.apache.kylin.storage.hbase.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.ClusterStatus;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.RegionLoad;
import org.apache.hadoop.hbase.ServerLoad;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.kylin.common.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HBaseRegionSizeCalculator {

    private static final Logger logger = LoggerFactory.getLogger(HBaseRegionSizeCalculator.class);

    /**
     * Maps each region to its size in bytes.
     **/
    private final Map<byte[], Long> sizeMap = new TreeMap<byte[], Long>(Bytes.BYTES_COMPARATOR);

    private final Map<byte[], Pair<Integer, Integer>> countMap = new TreeMap<>(Bytes.BYTES_COMPARATOR);

    static final String ENABLE_REGIONSIZECALCULATOR = "hbase.regionsizecalculator.enable";

    /**
     * Computes size of each region for table and given column families.
     * */
    public HBaseRegionSizeCalculator(String tableName, Connection hbaseConnection) throws IOException {

        Table table = null;
        Admin admin = null;
        try {
            table = hbaseConnection.getTable(TableName.valueOf(tableName));
            admin = hbaseConnection.getAdmin();

            if (!enabled(table.getConfiguration())) {
                logger.info("Region size calculation disabled.");
                return;
            }

            logger.info("Calculating region sizes for table \"" + table.getName() + "\".");

            // Get regions for table.
            RegionLocator regionLocator = hbaseConnection.getRegionLocator(table.getName());
            List<HRegionLocation> regionLocationList = regionLocator.getAllRegionLocations();
            Set<byte[]> tableRegions = new TreeSet<byte[]>(Bytes.BYTES_COMPARATOR);

            for (HRegionLocation hRegionLocation : regionLocationList) {
                tableRegions.add(hRegionLocation.getRegionInfo().getRegionName());
            }

            ClusterStatus clusterStatus = admin.getClusterStatus();
            Collection<ServerName> servers = clusterStatus.getServers();
            final long megaByte = 1024L * 1024L;

            // Iterate all cluster regions, filter regions from our table and
            // compute their size.
            for (ServerName serverName : servers) {
                ServerLoad serverLoad = clusterStatus.getLoad(serverName);

                for (RegionLoad regionLoad : serverLoad.getRegionsLoad().values()) {
                    byte[] regionId = regionLoad.getName();

                    if (tableRegions.contains(regionId)) {

                        long regionSizeBytes = regionLoad.getStorefileSizeMB() * megaByte;
                        sizeMap.put(regionId, regionSizeBytes);
                        countMap.put(regionId, new Pair<>(regionLoad.getStores(), regionLoad.getStorefiles()));

                        if (regionSizeBytes == 0L) {
                            logger.info("Region " + regionLoad.getNameAsString() + " has size " + regionSizeBytes);
                        }
                    }
                }
            }
        } finally {
            IOUtils.closeQuietly(admin);
        }

    }

    boolean enabled(Configuration configuration) {
        return configuration.getBoolean(ENABLE_REGIONSIZECALCULATOR, true);
    }

    /**
     * Returns size of given region in bytes. Returns 0 if region was not found.
     **/
    public long getRegionSize(byte[] regionId) {
        Long size = sizeMap.get(regionId);
        if (size == null) {
            logger.info("Unknown region:" + Arrays.toString(regionId));
            return 0;
        } else {
            return size;
        }
    }

    public Map<byte[], Long> getRegionSizeMap() {
        return Collections.unmodifiableMap(sizeMap);
    }

    public Map<byte[], Pair<Integer, Integer>> getRegionHFileCountMap() {
        return Collections.unmodifiableMap(countMap);
    }
}
