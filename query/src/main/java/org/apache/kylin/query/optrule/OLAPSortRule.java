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

import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.convert.ConverterRule;
import org.apache.calcite.rel.core.Sort;
import org.apache.kylin.query.relnode.OLAPRel;
import org.apache.kylin.query.relnode.OLAPSortRel;

/**
 */
public class OLAPSortRule extends ConverterRule {

    public static final OLAPSortRule INSTANCE = new OLAPSortRule();

    public OLAPSortRule() {
        super(Sort.class, Convention.NONE, OLAPRel.CONVENTION, "OLAPSortRule");
    }

    @Override
    public RelNode convert(RelNode rel) {
        final Sort sort = (Sort) rel;
        if (sort.offset != null || sort.fetch != null) {
            return null;
        }
        final RelTraitSet traitSet = sort.getTraitSet().replace(OLAPRel.CONVENTION);
        final RelNode input = sort.getInput();
        return new OLAPSortRel(rel.getCluster(), traitSet, convert(input, input.getTraitSet().replace(OLAPRel.CONVENTION)), sort.getCollation(), sort.offset, sort.fetch);
    }

}
