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

import org.apache.kylin.shaded.com.google.common.base.Preconditions;
import org.apache.kylin.shaded.com.google.common.collect.ImmutableMap;

/**
 * Calculate the benefit based on Benefit Per Unit Space with query probability distribution.
 */
public class PBPUSCalculator extends BPUSCalculator {

    public PBPUSCalculator(final CuboidStats cuboidStats) {
        super(cuboidStats);
    }

    protected PBPUSCalculator(CuboidStats cuboidStats, ImmutableMap<Long, Long> initCuboidAggCostMap) {
        super(cuboidStats, initCuboidAggCostMap);
    }

    @Override
    protected double getCostSaving(long descendant, long cuboid) {
        return getCuboidHitProbability(descendant) * super.getCostSaving(descendant, cuboid);
    }

    protected double getCuboidHitProbability(long cuboid) {
        return cuboidStats.getCuboidHitProbability(cuboid);
    }

    public double getMinBenefitRatio() {
        int cuboidDomainSize = cuboidStats.getAllCuboidsForSelection().size();
        Preconditions.checkArgument(cuboidDomainSize > 0,
                "The set of cuboids for selection is empty!!!");
        return super.getMinBenefitRatio() / cuboidDomainSize;
    }

    @Override
    public BenefitPolicy getInstance() {
        return new PBPUSCalculator(this.cuboidStats, this.initCuboidAggCostMap);
    }
}
