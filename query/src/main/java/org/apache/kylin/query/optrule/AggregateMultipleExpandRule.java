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

package org.apache.kylin.query.optrule;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptRuleOperand;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Aggregate;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;

/**
 * Support grouping query. Expand the non-simple aggregate to more than one simple aggregates.
 * Add project on expanded simple aggregate to add indicators of origin aggregate.
 * All projects on aggregate added into one union, which replace the origin aggregate.
 * The new aggregates will be transformed by {@link org.apache.kylin.query.optrule.AggregateProjectReduceRule}, to reduce rolled up dimensions.
 * In case to scan other cuboid data without the rolled up dimensions.
 *
 * <p>Examples:
 * <p>  Origin Aggregate:   {@code group by grouping sets ((dim A, dim B, dim C), (dim A, dim C), (dim B, dim C))}
 * <p>  Transformed Union:
 *     {@code select dim A, dim B, dim C, 0, 0, 0
 *            union all
 *            select dim A, null, dim C, 0, 1, 0
 *            union all
 *            select null, dim B, dim C, 1, 0, 0
 *     }
 */
public class AggregateMultipleExpandRule extends RelOptRule {
    public static final AggregateMultipleExpandRule INSTANCE = new AggregateMultipleExpandRule(//
            operand(LogicalAggregate.class, null, new Predicate<Aggregate>() {
                @Override
                public boolean apply(@Nullable Aggregate input) {
                    return input.getGroupType() != Aggregate.Group.SIMPLE;
                }
            }, operand(RelNode.class, any())), "AggregateMultipleExpandRule");

    private AggregateMultipleExpandRule(RelOptRuleOperand operand, String description) {
        super(operand, description);
    }

    private static List<ImmutableBitSet> asList(ImmutableBitSet groupSet) {
        ArrayList<ImmutableBitSet> l = new ArrayList<>(1);
        l.add(groupSet);
        return l;
    }

    @Override
    public void onMatch(RelOptRuleCall call) {
        LogicalAggregate aggr = (LogicalAggregate) call.getRelList().get(0);
        RelNode input = aggr.getInput();
        RelBuilder relBuilder = call.builder();
        RexBuilder rexBuilder = aggr.getCluster().getRexBuilder();

        for (ImmutableBitSet groupSet : aggr.getGroupSets()) {
            // push the simple aggregate with one group set
            relBuilder.push(aggr.copy(aggr.getTraitSet(), input, false, groupSet, asList(groupSet), aggr.getAggCallList()));

            ImmutableList.Builder<RexNode> rexNodes = new ImmutableList.Builder<>();
            int index = 0;
            Iterator<Integer> groupSetIter = aggr.getGroupSet().iterator();
            Iterator<RelDataTypeField> typeIterator = aggr.getRowType().getFieldList().iterator();
            Iterator<Integer> groupKeyIter = groupSet.iterator();
            int groupKey = groupKeyIter.next();

            // iterate the group keys, fill with null if the key is rolled up
            while (groupSetIter.hasNext()) {
                Integer aggrGroupKey = groupSetIter.next();
                RelDataType type = typeIterator.next().getType();
                if (groupKey == aggrGroupKey) {
                    rexNodes.add(rexBuilder.makeInputRef(type, index++));
                    groupKey = groupKeyIter.next();
                } else {
                    rexNodes.add(rexBuilder.makeNullLiteral(type));
                }
            }

            // fill aggr calls input ref
            while (typeIterator.hasNext()) {
                RelDataType type = typeIterator.next().getType();
                rexNodes.add(rexBuilder.makeInputRef(type, index++));
            }
            relBuilder.project(rexNodes.build());
        }
        RelNode unionAggr = relBuilder.union(true, aggr.getGroupSets().size()).build();

        call.transformTo(unionAggr);
    }
}
