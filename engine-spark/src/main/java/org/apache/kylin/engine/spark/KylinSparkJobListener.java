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

package org.apache.kylin.engine.spark;

import java.io.Serializable;

import org.apache.spark.scheduler.SparkListener;
import org.apache.spark.scheduler.SparkListenerTaskEnd;

public class KylinSparkJobListener extends SparkListener implements Serializable {

    public JobMetrics metrics = new JobMetrics();

    @Override
    public void onTaskEnd(SparkListenerTaskEnd taskEnd) {
        if (taskEnd != null && taskEnd.taskMetrics() != null && taskEnd.taskMetrics().outputMetrics() != null) {
            metrics.bytesWritten += taskEnd.taskMetrics().outputMetrics().bytesWritten();
            metrics.recordsWritten += taskEnd.taskMetrics().outputMetrics().recordsWritten();
        }
    }

    public JobMetrics getMetrics() {
        return metrics;
    }

    public static class JobMetrics implements Serializable {
        long bytesWritten;
        long recordsWritten;

        public long getBytesWritten() {
            return bytesWritten;
        }

        public long getRecordsWritten() {
            return recordsWritten;
        }
    }

}
