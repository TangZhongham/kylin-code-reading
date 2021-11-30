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

package org.apache.kylin.measure.topn;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

/**
 * Modified from Counter.java in https://github.com/addthis/stream-lib
 * 
 * @param <T>
 */
public class Counter<T> implements Externalizable, Serializable {

    protected T item;
    protected Double count;

    /**
     * For de-serialization
     */
    public Counter() {
    }

    public Counter(T item) {
        this.count = 0d;
        this.item = item;
    }

    public Counter(T item, Double count) {
        this.item = item;
        this.count = count;
    }

    public T getItem() {
        return item;
    }

    public Double getCount() {
        return count;
    }

    public void setItem(T item) {
        this.item = item;
    }

    public void setCount(Double count) {
        this.count = count;
    }

    @Override
    public String toString() {
        return item + ":" + count;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        item = (T) in.readObject();
        count = in.readDouble();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(item);
        out.writeDouble(count);
    }
}
