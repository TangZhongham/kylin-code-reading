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

package org.apache.kylin.cube.cuboid.algorithm;

import java.util.List;

/**
 * Algorithm to calculate the cuboid benifit and recommend cost-effective cuboid list based on the cube statistics.
 */
public interface CuboidRecommendAlgorithm {

    /**
     * Return a list of recommended cuboids for the building segment based on expansionRate
     *
     */
    List<Long> recommend(double expansionRate);

    /**
     * Start the Algorithm running
     * 
     * @param maxSpaceLimit
     * @return
     */
    List<Long> start(double maxSpaceLimit);

    /**
     * Cancel the Algorithm running
     *
     *  Users can call this method from another thread to can the Algorithm and return the result earlier
     *
     */
    void cancel();
}
