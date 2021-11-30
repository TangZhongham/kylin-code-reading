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

package org.apache.kylin.cache.memcached;

import net.spy.memcached.DefaultConnectionFactory;

public class MemcachedCacheConfig {
    private long timeout = 500L;

    // comma delimited list of memcached servers, given as host:port combination
    private String hosts;

    private int maxChunkSize = 1024;

    private int maxObjectSize = 1024 * 1024;

    // memcached client read buffer size, -1 uses the spymemcached library default
    private int readBufferSize = DefaultConnectionFactory.DEFAULT_READ_BUFFER_SIZE;

    // maximum operation queue size. 0 means unbounded
    private int maxOperationQueueSize = 0;

    // whether enable compress the value data or not
    private boolean enableCompression = true;

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public void setMaxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }

    public int getMaxObjectSize() {
        return maxObjectSize;
    }

    public void setMaxObjectSize(int maxObjectSize) {
        this.maxObjectSize = maxObjectSize;
    }

    public int getMaxOperationQueueSize() {
        return maxOperationQueueSize;
    }

    public void setMaxOperationQueueSize(int maxOperationQueueSize) {
        this.maxOperationQueueSize = maxOperationQueueSize;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    public boolean isEnableCompression() {
        return enableCompression;
    }

    public void setEnableCompression(boolean enableCompression) {
        this.enableCompression = enableCompression;
    }
}