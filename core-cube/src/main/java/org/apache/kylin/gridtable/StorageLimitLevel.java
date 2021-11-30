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

package org.apache.kylin.gridtable;

public enum StorageLimitLevel {
    NO_LIMIT,
    //even if enableStorageLimitIfPossible() is false,
    //(meaning we have to scan through all the cuboid rows)
    //we can still take advantage of the fact that we don't need all of the agg keys
    LIMIT_ON_RETURN_SIZE,
    //iff enableStorageLimitIfPossible() is true
    LIMIT_ON_SCAN
}
