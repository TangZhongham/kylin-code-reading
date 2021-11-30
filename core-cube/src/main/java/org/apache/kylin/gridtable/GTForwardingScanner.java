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

import static org.apache.kylin.shaded.com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Iterator;

/**
 * A {@link IGTScanner} which forwards all its method calls to another scanner.
 *
 * @see <a href="http://en.wikipedia.org/wiki/Decorator_pattern">decorator pattern</a>.
 */
public class GTForwardingScanner implements IGTScanner {
    protected IGTScanner delegated;

    protected GTForwardingScanner(IGTScanner delegated) {
        this.delegated = checkNotNull(delegated, "delegated");
    }

    @Override
    public GTInfo getInfo() {
        return delegated.getInfo();
    }

    @Override
    public void close() throws IOException {
        delegated.close();
    }

    @Override
    public Iterator<GTRecord> iterator() {
        return delegated.iterator();
    }
}
