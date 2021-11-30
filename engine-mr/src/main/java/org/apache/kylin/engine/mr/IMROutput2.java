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

package org.apache.kylin.engine.mr;

import java.util.List;

import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.kylin.cube.CubeInstance;
import org.apache.kylin.cube.CubeSegment;
import org.apache.kylin.cube.cuboid.CuboidScheduler;
import org.apache.kylin.job.execution.DefaultChainedExecutable;

public interface IMROutput2 {

    /** Return a helper to participate in batch cubing job flow. */
    public IMRBatchCubingOutputSide2 getBatchCubingOutputSide(CubeSegment seg);

    /**
     * Participate the batch cubing flow as the output side. Responsible for saving
     * the cuboid output to storage at the end of Phase 3.
     * 
     * - Phase 1: Create Flat Table
     * - Phase 2: Build Dictionary
     * - Phase 3: Build Cube
     * - Phase 4: Update Metadata & Cleanup
     */
    public interface IMRBatchCubingOutputSide2 {

        /** Add step that executes after build dictionary and before build cube. */
        public void addStepPhase2_BuildDictionary(DefaultChainedExecutable jobFlow);

        /**
         * Add step that saves cuboids from HDFS to storage.
         * 
         * The cuboid output is a directory of sequence files, where key is CUBOID+D1+D2+..+Dn, 
         * value is M1+M2+..+Mm. CUBOID is 8 bytes cuboid ID; Dx is dimension value with
         * dictionary encoding; Mx is measure value serialization form.
         */
        public void addStepPhase3_BuildCube(DefaultChainedExecutable jobFlow);

        /** Add step that does any necessary clean up. */
        public void addStepPhase4_Cleanup(DefaultChainedExecutable jobFlow);

        public IMROutputFormat getOutputFormat();

    }

    public interface IMROutputFormat {

        /** Configure the InputFormat of given job. */
        public void configureJobInput(Job job, String input) throws Exception;

        /** Configure the OutputFormat of given job. */
        public void configureJobOutput(Job job, String output, CubeSegment segment, CuboidScheduler cuboidScheduler, int level) throws Exception;

    }

    /** Return a helper to participate in batch merge job flow. */
    public IMRBatchMergeOutputSide2 getBatchMergeOutputSide(CubeSegment seg);

    /**
     * Participate the batch cubing flow as the output side. Responsible for saving
     * the cuboid output to storage at the end of Phase 2.
     * 
     * - Phase 1: Merge Dictionary
     * - Phase 2: Merge Cube
     * - Phase 3: Update Metadata & Cleanup
     */
    public interface IMRBatchMergeOutputSide2 {

        /** Add step that executes after merge dictionary and before merge cube. */
        public void addStepPhase1_MergeDictionary(DefaultChainedExecutable jobFlow);

        /**
         * Add step that saves cuboid output from HDFS to storage.
         * 
         * The cuboid output is a directory of sequence files, where key is CUBOID+D1+D2+..+Dn, 
         * value is M1+M2+..+Mm. CUBOID is 8 bytes cuboid ID; Dx is dimension value with
         * dictionary encoding; Mx is measure value serialization form.
         */
        public void addStepPhase2_BuildCube(CubeSegment set, List<CubeSegment> mergingSegments, DefaultChainedExecutable jobFlow);

        /** Add step that does any necessary clean up. */
        public void addStepPhase3_Cleanup(DefaultChainedExecutable jobFlow);

        public IMRMergeOutputFormat getOutputFormat();
    }

    public interface IMRMergeOutputFormat {

        /** Configure the InputFormat of given job. */
        public void configureJobInput(Job job, String input) throws Exception;

        /** Configure the OutputFormat of given job. */
        public void configureJobOutput(Job job, String output, CubeSegment segment) throws Exception;

        public CubeSegment findSourceSegment(FileSplit fileSplit, CubeInstance cube);
    }

    public IMRBatchOptimizeOutputSide2 getBatchOptimizeOutputSide(CubeSegment seg);

    /**
     * Participate the batch cubing flow as the output side. Responsible for saving
     * the cuboid output to storage at the end of Phase 3.
     *
     * - Phase 1: Filter Recommended Cuboid Data
     * - Phase 2: Copy Dictionary & Calculate Statistics & Update Reused Cuboid Shard
     * - Phase 3: Build Cube
     * - Phase 4: Cleanup Optimize
     * - Phase 5: Update Metadata & Cleanup
     */
    public interface IMRBatchOptimizeOutputSide2 {

        /** Create HTable based on recommended cuboids & statistics*/
        public void addStepPhase2_CreateHTable(DefaultChainedExecutable jobFlow);

        /** Build only missing cuboids*/
        public void addStepPhase3_BuildCube(DefaultChainedExecutable jobFlow);

        /** Cleanup intermediate cuboid data on HDFS*/
        public void addStepPhase4_Cleanup(DefaultChainedExecutable jobFlow);

        /** Invoked by Checkpoint job & Cleanup old segments' HTables and related working directory*/
        public void addStepPhase5_Cleanup(DefaultChainedExecutable jobFlow);
    }
}
