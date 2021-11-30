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

package org.apache.kylin.storage.stream.rpc;

import org.apache.kylin.cube.CubeInstance;
import org.apache.kylin.metadata.tuple.ITuple;
import org.apache.kylin.metadata.tuple.TupleInfo;
import org.apache.kylin.stream.core.model.DataRequest;
import org.apache.kylin.stream.core.model.DataResponse;
import org.apache.kylin.stream.core.model.Node;
import org.apache.kylin.stream.core.query.StreamingTupleConverter;
import org.apache.kylin.stream.core.util.RecordsSerializer;
import org.apache.kylin.stream.server.rest.controller.DataController;

import java.util.Iterator;

public class MockedStreamDataSearchClient extends HttpStreamDataSearchClient {

    @Override
    public Iterator<ITuple> doSearch(DataRequest dataRequest, CubeInstance cube, StreamingTupleConverter tupleConverter, RecordsSerializer recordsSerializer, Node receiver, TupleInfo tupleInfo) throws Exception {
        DataResponse response = new DataController().query(dataRequest);
        return deserializeResponse(tupleConverter, recordsSerializer, cube.getName(), tupleInfo, response);
    }
}
