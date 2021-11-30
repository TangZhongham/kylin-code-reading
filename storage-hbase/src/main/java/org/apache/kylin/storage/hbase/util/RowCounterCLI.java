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

import java.io.IOException;
import java.util.Iterator;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.kylin.common.util.Bytes;
import org.apache.kylin.common.util.BytesUtil;
import org.apache.kylin.storage.hbase.HBaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class RowCounterCLI {
    private static final Logger logger = LoggerFactory.getLogger(RowCounterCLI.class);

    public static void main(String[] args) throws IOException {

        if (args == null || args.length != 3) {
            logger.info(
                    "Usage: hbase org.apache.hadoop.util.RunJar kylin-job-latest.jar org.apache.kylin.job.tools.RowCounterCLI [HTABLE_NAME] [STARTKEY] [ENDKEY]");
            return; // if no enough arguments provided, return with above message
        }

        logger.info(args[0]);
        String htableName = args[0];
        logger.info(args[1]);
        byte[] startKey = BytesUtil.fromReadableText(args[1]);
        logger.info(args[2]);
        byte[] endKey = BytesUtil.fromReadableText(args[2]);

        if (startKey == null) {
            logger.info("startkey is null ");
        } else {
            logger.info("startkey lenght: {}", startKey.length);
        }
        if(logger.isInfoEnabled()){
            logger.info("start key in binary: {}", Bytes.toStringBinary(startKey));
            logger.info("end key in binary: {}", Bytes.toStringBinary(endKey));
        }

        Configuration conf = HBaseConnection.getCurrentHBaseConfiguration();

        Scan scan = new Scan();
        scan.setCaching(512);
        scan.setCacheBlocks(true);
        scan.setStartRow(startKey);
        scan.setStopRow(endKey);

        logger.info("My Scan {}", scan);
        try (Connection conn = ConnectionFactory.createConnection(conf);
                Table tableInterface = conn.getTable(TableName.valueOf(htableName))) {
            Iterator<Result> iterator = tableInterface.getScanner(scan).iterator();
            int counter = 0;
            while (iterator.hasNext()) {
                iterator.next();
                counter++;
                if (counter % 1000 == 1) {
                    logger.info("number of rows: {}", counter);
                }
            }
            logger.info("number of rows: {}", counter);
        }
    }
}
