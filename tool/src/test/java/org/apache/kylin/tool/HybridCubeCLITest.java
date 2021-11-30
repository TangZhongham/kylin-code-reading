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

package org.apache.kylin.tool;

import java.io.IOException;

import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.common.util.LocalFileMetadataTestCase;
import org.apache.kylin.cube.CubeInstance;
import org.apache.kylin.cube.CubeManager;
import org.apache.kylin.metadata.model.SegmentRange;
import org.apache.kylin.metadata.project.ProjectManager;
import org.apache.kylin.metadata.realization.RealizationType;
import org.apache.kylin.storage.hybrid.HybridInstance;
import org.apache.kylin.storage.hybrid.HybridManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class HybridCubeCLITest extends LocalFileMetadataTestCase {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        this.createTestMetadata();
    }

    @After
    public void after() throws Exception {
        this.cleanupTestMetadata();
    }

    @Test
    public void test1Create() throws IOException {
        HybridManager hybridManager = HybridManager.getInstance(KylinConfig.getInstanceFromEnv());
        Assert.assertNull(hybridManager.getHybridInstance("ssb_hybrid"));
        HybridCubeCLI.main(new String[] { "-name", "ssb_hybrid", "-project", "default", "-model", "ssb", "-cubes", "ssb_cube1,ssb_cube2", "-action", "create" });

        HybridInstance hybridInstance = hybridManager.getHybridInstance("ssb_hybrid");
        Assert.assertNotNull(hybridInstance);
        Assert.assertEquals("ssb_hybrid", hybridInstance.getName());
        Assert.assertEquals(2, hybridInstance.getRealizationEntries().size());
    }

    @Test
    public void test2Update() throws IOException {
        HybridManager hybridManager = HybridManager.getInstance(KylinConfig.getInstanceFromEnv());
        Assert.assertNull(hybridManager.getHybridInstance("ssb_hybrid"));
        HybridCubeCLI.main(new String[] { "-name", "ssb_hybrid", "-project", "default", "-model", "ssb", "-cubes", "ssb_cube1,ssb_cube2", "-action", "create" });

        HybridInstance hybridInstance = hybridManager.getHybridInstance("ssb_hybrid");
        Assert.assertNotNull(hybridManager.getHybridInstance("ssb_hybrid"));
        Assert.assertEquals("ssb_hybrid", hybridInstance.getName());
        Assert.assertEquals(2, hybridInstance.getRealizationEntries().size());
        HybridCubeCLI.main(new String[] { "-name", "ssb_hybrid", "-project", "default", "-model", "ssb", "-cubes", "ssb_cube1,ssb_cube2,ssb_cube3", "-action", "update" });

        hybridInstance = hybridManager.getHybridInstance("ssb_hybrid");
        Assert.assertNotNull(hybridInstance);
        Assert.assertEquals("ssb_hybrid", hybridInstance.getName());
        Assert.assertEquals(3, hybridInstance.getRealizationEntries().size());
    }

    @Test
    public void test3Delete() throws IOException {
        HybridManager hybridManager = HybridManager.getInstance(KylinConfig.getInstanceFromEnv());
        Assert.assertNull(hybridManager.getHybridInstance("ssb_hybrid"));
        HybridCubeCLI.main(new String[] { "-name", "ssb_hybrid", "-project", "default", "-model", "ssb", "-cubes", "ssb_cube1,ssb_cube2", "-action", "create" });
        Assert.assertNotNull(hybridManager.getHybridInstance("ssb_hybrid"));
        HybridCubeCLI.main(new String[] { "-name", "ssb_hybrid", "-project", "default", "-model", "ssb", "-action", "delete" });

        HybridInstance hybridInstance = hybridManager.getHybridInstance("ssb_hybrid");
        Assert.assertNull(hybridInstance);
        Assert.assertEquals(0, ProjectManager.getInstance(KylinConfig.getInstanceFromEnv()).findProjects(RealizationType.HYBRID, "ssb_hybrid").size());
    }

    @Test
    public void testSegmentOverlap() throws IOException {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Segments has overlap");

        HybridManager hybridManager = HybridManager.getInstance(KylinConfig.getInstanceFromEnv());
        Assert.assertNull(hybridManager.getHybridInstance("ssb_hybrid"));
        HybridCubeCLI.main(new String[] { "-name", "ssb_hybrid", "-project", "default", "-model", "ssb", "-cubes", "ssb_cube1,ssb_cube2", "-action", "create" });

        CubeManager cubeManager = CubeManager.getInstance(KylinConfig.getInstanceFromEnv());
        CubeInstance cube1 = cubeManager.getCube("ssb_cube1");
        CubeInstance cube2 = cubeManager.getCube("ssb_cube2");

        // 2012-01-01,2012-01-03
        cubeManager.appendSegment(cube1, new SegmentRange.TSRange(1325376000000L, 1325548800000L));
        // 2012-01-02,2012-01-04
        cubeManager.appendSegment(cube2, new SegmentRange.TSRange(1325462400000L, 1325635200000L));

        HybridCubeCLI.main(new String[] { "-name", "ssb_hybrid", "-project", "default", "-model", "ssb", "-cubes", "ssb_cube1,ssb_cube2", "-action", "update" });
    }

    @Test
    public void testSegmentNotOverlap() throws IOException {
        HybridManager hybridManager = HybridManager.getInstance(KylinConfig.getInstanceFromEnv());
        Assert.assertNull(hybridManager.getHybridInstance("ssb_hybrid"));
        HybridCubeCLI.main(new String[] { "-name", "ssb_hybrid", "-project", "default", "-model", "ssb", "-cubes", "ssb_cube1,ssb_cube2", "-action", "create" });

        CubeManager cubeManager = CubeManager.getInstance(KylinConfig.getInstanceFromEnv());
        CubeInstance cube1 = cubeManager.getCube("ssb_cube1");
        CubeInstance cube2 = cubeManager.getCube("ssb_cube2");

        // 2012-01-01,2012-01-03
        cubeManager.appendSegment(cube1, new SegmentRange.TSRange(1325376000000L, 1325548800000L));
        // 2012-01-03,2012-01-04
        cubeManager.appendSegment(cube2, new SegmentRange.TSRange(1325548800000L, 1325635200000L));

        // not throw exception
        HybridCubeCLI.main(new String[] { "-name", "ssb_hybrid", "-project", "default", "-model", "ssb", "-cubes", "ssb_cube1,ssb_cube2", "-action", "update" });
    }

}
