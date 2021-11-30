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
package org.apache.kylin.source.kafka;

import org.apache.kylin.cube.CubeSegment;
import org.apache.kylin.engine.spark.ISparkInput;
import org.apache.kylin.metadata.model.IJoinedFlatTableDesc;
import org.apache.kylin.metadata.model.ISegment;

public class KafkaSparkInput extends KafkaInputBase implements ISparkInput {

    private CubeSegment cubeSegment;

    @Override
    public IBatchCubingInputSide getBatchCubingInputSide(IJoinedFlatTableDesc flatDesc) {
        this.cubeSegment = (CubeSegment) flatDesc.getSegment();
        return new KafkaSparkBatchCubingInputSide(cubeSegment, flatDesc);
    }

    @Override
    public IBatchMergeInputSide getBatchMergeInputSide(ISegment seg) {
        return new KafkaSparkBatchMergeInputSide((CubeSegment)seg);
    }

    public static class KafkaSparkBatchCubingInputSide extends BaseBatchCubingInputSide implements ISparkBatchCubingInputSide {

        public KafkaSparkBatchCubingInputSide(CubeSegment seg, IJoinedFlatTableDesc flatDesc) {
            super(seg, flatDesc);
        }
    }

    public static class KafkaSparkBatchMergeInputSide extends BaseBatchMergeInputSide implements ISparkBatchMergeInputSide {

        KafkaSparkBatchMergeInputSide(CubeSegment cubeSegment) {
            super(cubeSegment);
        }
    }
}
