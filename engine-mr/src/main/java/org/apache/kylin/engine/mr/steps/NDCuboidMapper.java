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

package org.apache.kylin.engine.mr.steps;

import java.io.IOException;
import java.util.Collection;

import org.apache.hadoop.io.Text;
import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.common.util.ByteArray;
import org.apache.kylin.common.util.Pair;
import org.apache.kylin.cube.CubeInstance;
import org.apache.kylin.cube.CubeManager;
import org.apache.kylin.cube.CubeSegment;
import org.apache.kylin.cube.common.RowKeySplitter;
import org.apache.kylin.cube.cuboid.Cuboid;
import org.apache.kylin.cube.cuboid.CuboidScheduler;
import org.apache.kylin.cube.model.CubeDesc;
import org.apache.kylin.engine.mr.KylinMapper;
import org.apache.kylin.engine.mr.common.AbstractHadoopJob;
import org.apache.kylin.engine.mr.common.BatchConstants;
import org.apache.kylin.engine.mr.common.CuboidSchedulerUtil;
import org.apache.kylin.engine.mr.common.NDCuboidBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author George Song (ysong1)
 * 
 */
public class NDCuboidMapper extends KylinMapper<Text, Text, Text, Text> {

    private static final Logger logger = LoggerFactory.getLogger(NDCuboidMapper.class);

    private Text outputKey = new Text();
    private String cubeName;
    private String segmentID;
    private CubeDesc cubeDesc;
    private CubeSegment cubeSegment;
    private CuboidScheduler cuboidScheduler;

    private int handleCounter;
    private int skipCounter;

    private RowKeySplitter rowKeySplitter;

    private NDCuboidBuilder ndCuboidBuilder;

    @Override
    protected void doSetup(Context context) throws IOException {
        super.bindCurrentConfiguration(context.getConfiguration());

        cubeName = context.getConfiguration().get(BatchConstants.CFG_CUBE_NAME);
        segmentID = context.getConfiguration().get(BatchConstants.CFG_CUBE_SEGMENT_ID);
        String cuboidModeName = context.getConfiguration().get(BatchConstants.CFG_CUBOID_MODE);

        KylinConfig config = AbstractHadoopJob.loadKylinPropsAndMetadata();

        CubeInstance cube = CubeManager.getInstance(config).getCube(cubeName);
        cubeDesc = cube.getDescriptor();
        cubeSegment = cube.getSegmentById(segmentID);
        ndCuboidBuilder = new NDCuboidBuilder(cubeSegment);
        // initialize CubiodScheduler
        cuboidScheduler = CuboidSchedulerUtil.getCuboidSchedulerByMode(cubeSegment, cuboidModeName);
        rowKeySplitter = new RowKeySplitter(cubeSegment);
    }



    @Override
    public void doMap(Text key, Text value, Context context) throws IOException, InterruptedException {
        long cuboidId = rowKeySplitter.split(key.getBytes());
        Cuboid parentCuboid = Cuboid.findForMandatory(cubeDesc, cuboidId);

        Collection<Long> myChildren = cuboidScheduler.getSpanningCuboid(cuboidId);

        // if still empty or null
        if (myChildren == null || myChildren.size() == 0) {
            context.getCounter(BatchConstants.MAPREDUCE_COUNTER_GROUP_NAME, "Skipped records").increment(1L);
            if (skipCounter++ % BatchConstants.NORMAL_RECORD_LOG_THRESHOLD == 0) {
                logger.info("Skipping record with ordinal: " + skipCounter);
            }
            return;
        }

        context.getCounter(BatchConstants.MAPREDUCE_COUNTER_GROUP_NAME, "Processed records").increment(1L);

        if (handleCounter++ % BatchConstants.NORMAL_RECORD_LOG_THRESHOLD == 0) {
            logger.info("Handling record with ordinal: " + handleCounter);
            logger.info("Parent cuboid: " + parentCuboid.getId() + "; Children: " + myChildren);
        }

        Pair<Integer, ByteArray> result;
        for (Long child : myChildren) {
            Cuboid childCuboid = Cuboid.findForMandatory(cubeDesc, child);
            result = ndCuboidBuilder.buildKey(parentCuboid, childCuboid, rowKeySplitter.getSplitBuffers());
            outputKey.set(result.getSecond().array(), 0, result.getFirst());
            context.write(outputKey, value);
        }

    }
}
