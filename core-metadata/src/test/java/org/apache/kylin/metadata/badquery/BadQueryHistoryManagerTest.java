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

package org.apache.kylin.metadata.badquery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.NavigableSet;

import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.common.util.JsonUtil;
import org.apache.kylin.common.util.LocalFileMetadataTestCase;
import org.apache.kylin.common.util.RandomUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BadQueryHistoryManagerTest extends LocalFileMetadataTestCase {
    @Before
    public void setUp() throws Exception {
        this.createTestMetadata();
    }

    @After
    public void after() throws Exception {
        this.cleanupTestMetadata();
    }

    @Test
    public void testBasics() throws Exception {
        BadQueryHistory history = BadQueryHistoryManager.getInstance(getTestConfig())
                .getBadQueriesForProject("default");
        System.out.println(JsonUtil.writeValueAsIndentString(history));

        NavigableSet<BadQueryEntry> entries = history.getEntries();
        assertEquals(3, entries.size());

        BadQueryEntry entry1 = entries.first();
        assertEquals("Pushdown", entry1.getAdj());
        assertEquals("sandbox.hortonworks.com", entry1.getServer());
        assertEquals("select * from test_kylin_fact limit 10", entry1.getSql());

        entries.pollFirst();
        BadQueryEntry entry2 = entries.first();
        assertTrue(entry2.getStartTime() > entry1.getStartTime());
    }

    @Test
    public void testAddEntryToProject() throws IOException {
        KylinConfig kylinConfig = getTestConfig();
        BadQueryHistoryManager manager = BadQueryHistoryManager.getInstance(kylinConfig);
        BadQueryEntry entry = new BadQueryEntry("sql", "adj", 1459362239992L, 100, "server", "t-0", "user",
                RandomUtil.randomUUID().toString(), "cube");
        BadQueryHistory history = manager.upsertEntryToProject(entry, "default");
        NavigableSet<BadQueryEntry> entries = history.getEntries();
        assertEquals(4, entries.size());

        BadQueryEntry newEntry = entries.last();

        System.out.println(newEntry);
        assertEquals("sql", newEntry.getSql());
        assertEquals("adj", newEntry.getAdj());
        assertEquals(1459362239992L, newEntry.getStartTime());
        assertEquals("server", newEntry.getServer());
        assertEquals("user", newEntry.getUser());
        assertEquals("t-0", newEntry.getThread());

        for (int i = 0; i < kylinConfig.getBadQueryHistoryNum(); i++) {
            BadQueryEntry tmp = new BadQueryEntry("sql", "adj", 1459362239993L + i, 100 + i, "server", "t-0", "user",
                    RandomUtil.randomUUID().toString(), "cube");
            history = manager.upsertEntryToProject(tmp, "default");
        }
        assertEquals(kylinConfig.getBadQueryHistoryNum(), history.getEntries().size());
    }

    @Test
    public void testUpdateEntryToProject() throws IOException {
        KylinConfig kylinConfig = getTestConfig();
        BadQueryHistoryManager manager = BadQueryHistoryManager.getInstance(kylinConfig);

        String queryId = RandomUtil.randomUUID().toString();
        manager.upsertEntryToProject(
                new BadQueryEntry("sql", "adj", 1459362239000L, 100, "server", "t-0", "user", queryId, "cube"),
                "default");
        BadQueryHistory history = manager.upsertEntryToProject(
                new BadQueryEntry("sql", "adj2", 1459362239000L, 120, "server2", "t-1", "user", queryId, "cube"), "default");

        NavigableSet<BadQueryEntry> entries = history.getEntries();
        BadQueryEntry newEntry = entries
                .floor(new BadQueryEntry("sql", "adj2", 1459362239000L, 120, "server2", "t-1", "user", queryId, "cube"));
        System.out.println(newEntry);
        assertEquals("adj2", newEntry.getAdj());
        assertEquals("server2", newEntry.getServer());
        assertEquals("t-1", newEntry.getThread());
        assertEquals("user", newEntry.getUser());
        assertEquals(120, (int) newEntry.getRunningSec());
    }

}
