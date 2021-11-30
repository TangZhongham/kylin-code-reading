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
package org.apache.kylin.source.jdbc.extensible;

import org.apache.kylin.engine.spark.ISparkInput;
import org.apache.kylin.job.execution.DefaultChainedExecutable;
import org.apache.kylin.metadata.model.IJoinedFlatTableDesc;
import org.apache.kylin.metadata.model.ISegment;
import org.apache.kylin.sdk.datasource.framework.JdbcConnector;

public class JdbcHiveSparkInput extends JdbcHiveInputBase implements ISparkInput {

    private final JdbcConnector dataSource;

    JdbcHiveSparkInput(JdbcConnector dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public IBatchCubingInputSide getBatchCubingInputSide(IJoinedFlatTableDesc flatDesc) {
        return new JdbcSparkBatchCubingInputSide(flatDesc, dataSource);
    }

    @Override
    public IBatchMergeInputSide getBatchMergeInputSide(ISegment seg) {
        return new ISparkBatchMergeInputSide() {
            @Override
            public void addStepPhase1_MergeDictionary(DefaultChainedExecutable jobFlow) {
                // doing nothing
            }
        };
    }

    public static class JdbcSparkBatchCubingInputSide extends JDBCBaseBatchCubingInputSide implements ISparkBatchCubingInputSide {

        public JdbcSparkBatchCubingInputSide(IJoinedFlatTableDesc flatDesc, JdbcConnector dataSource) {
            super(flatDesc, dataSource);
        }
    }
}
