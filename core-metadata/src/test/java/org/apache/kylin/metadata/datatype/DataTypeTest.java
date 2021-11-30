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

package org.apache.kylin.metadata.datatype;

import org.junit.Assert;
import org.junit.Test;

public class DataTypeTest {

    @Test
    public void testComplexType() {
        DataType arrayInt = DataType.getType("array<int>");
        DataType arrayString = DataType.getType("array<string>");

        Assert.assertTrue(DataType.isComplexType(arrayInt));
        Assert.assertTrue(DataType.isComplexType(arrayString));
    }

    @Test
    public void testIsSupportedType() {
        Assert.assertFalse(DataType.isKylinSupported("array<int>"));
        Assert.assertFalse(DataType.isKylinSupported("map<string, int>"));
        Assert.assertFalse(DataType.isKylinSupported(null));
    }
}
