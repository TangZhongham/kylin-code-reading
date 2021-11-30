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

package org.apache.kylin.query.relnode;

import java.util.ArrayList;
import java.util.List;

import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.adapter.enumerable.EnumerableRel;
import org.apache.calcite.adapter.enumerable.EnumerableWindowBridge;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTrait;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.Window;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexLiteral;
import org.apache.commons.compress.utils.Lists;
import org.apache.kylin.metadata.expression.TupleExpression;
import org.apache.kylin.metadata.model.TblColRef;

import com.google.common.base.Preconditions;

/**
 */
public class OLAPWindowRel extends Window implements OLAPRel {
    ColumnRowType columnRowType;
    OLAPContext context;

    public OLAPWindowRel(RelOptCluster cluster, RelTraitSet traitSet, RelNode input, List<RexLiteral> constants,
            RelDataType rowType, List<Group> groups) {
        super(cluster, traitSet, input, constants, rowType, groups);
        Preconditions.checkArgument(getConvention() == CONVENTION);
        Preconditions.checkArgument(getConvention() == input.getConvention());
    }

    @Override
    public Window copy(RelTraitSet traitSet, List<RelNode> inputs) {
        assert inputs.size() == 1;
        return new OLAPWindowRel(getCluster(), traitSet, inputs.get(0), constants, rowType, groups);
    }

    @Override
    public RelOptCost computeSelfCost(RelOptPlanner planner, RelMetadataQuery mq) {
        return super.computeSelfCost(planner, mq).multiplyBy(.05);
    }

    @Override
    public RelWriter explainTerms(RelWriter pw) {

        return super.explainTerms(pw)
                .item("ctx", context == null ? "" : String.valueOf(context.id) + "@" + context.realization)//
                .itemIf("constants", constants, !constants.isEmpty()) //
                .itemIf("groups", groups, !groups.isEmpty());
    }

    @Override
    public void implementOLAP(OLAPImplementor implementor) {
        implementor.fixSharedOlapTableScan(this);
        implementor.visitChild(getInput(), this);

        this.columnRowType = buildColumnRowType();
        this.context = implementor.getContext();
        this.context.hasWindow = true;
    }

    ColumnRowType buildColumnRowType() {
        OLAPRel olapChild = (OLAPRel) getInput(0);
        ColumnRowType inputColumnRowType = olapChild.getColumnRowType();

        List<TblColRef> columns = new ArrayList<>();
        // the input col always be collected by left
        columns.addAll(inputColumnRowType.getAllColumns());

        // add window aggregate calls column
        for (Group group : groups) {
            List<TupleExpression> sourceColOuter = Lists.newArrayList();
            group.keys.asSet().stream().map(inputColumnRowType::getTupleExpressionByIndex).forEach(sourceColOuter::add);
            group.orderKeys.getFieldCollations().stream().map(RelFieldCollation::getFieldIndex)
                    .map(inputColumnRowType::getTupleExpressionByIndex).forEach(sourceColOuter::add);
            for (AggregateCall aggrCall : group.getAggregateCalls(this)) {
                TblColRef aggrCallCol = TblColRef.newInnerColumn(aggrCall.getName(),
                        TblColRef.InnerDataTypeEnum.LITERAL);
                List<TupleExpression> sourceColInner = Lists.newArrayList(sourceColOuter.iterator());
                aggrCall.getArgList().stream().filter(i -> i < inputColumnRowType.size())
                        .map(inputColumnRowType::getTupleExpressionByIndex).forEach(sourceColInner::add);
                aggrCallCol.setSubTupleExps(sourceColInner);
                columns.add(aggrCallCol);
            }
        }
        return new ColumnRowType(columns);
    }

    @Override
    public void implementRewrite(RewriteImplementor implementor) {
        for (RelNode child : getInputs()) {
            implementor.visitChild(this, child);
        }
    }

    @Override
    public EnumerableRel implementEnumerable(List<EnumerableRel> inputs) {
        for (EnumerableRel input : inputs) {
            if (input instanceof OLAPRel) {
                ((OLAPRel) input).replaceTraitSet(EnumerableConvention.INSTANCE);
            }
        }
        return EnumerableWindowBridge.createEnumerableWindow(getCluster(), traitSet, inputs.get(0), constants, rowType,
                groups);
    }

    @Override
    public OLAPContext getContext() {
        return context;
    }

    @Override
    public ColumnRowType getColumnRowType() {
        return columnRowType;
    }

    @Override
    public boolean hasSubQuery() {
        for (RelNode child : getInputs()) {
            if (((OLAPRel) child).hasSubQuery()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public RelTraitSet replaceTraitSet(RelTrait trait) {
        RelTraitSet oldTraitSet = this.traitSet;
        this.traitSet = this.traitSet.replace(trait);
        return oldTraitSet;
    }
}
