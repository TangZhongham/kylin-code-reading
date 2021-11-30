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

package org.apache.kylin.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

public class SourceConfigurationUtilTest {
    @Before
    public void setup() {
        System.setProperty("log4j.configuration", "file:../build/conf/kylin-tools-log4j.properties");
        System.setProperty("KYLIN_CONF", LocalFileMetadataTestCase.LOCALMETA_TEST_DATA);
    }

    @Test
    public void testHiveConf() {
        Properties properties = SourceConfigurationUtil.loadHiveJDBCProperties();
        assertTrue(properties.containsKey("hiveconf:hive.auto.convert.join.noconditionaltask.size"));
    }

    @Test
    public void testSqoopConf() {
        Map<String, String> configMap = SourceConfigurationUtil.loadSqoopConfiguration();
        assertFalse(configMap.isEmpty());
        assertEquals("1", configMap.get("dfs.replication"));
    }
}
