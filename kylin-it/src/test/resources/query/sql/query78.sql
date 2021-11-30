--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

select sum(PRICE) as GMV, LSTG_FORMAT_NAME as FORMAT_NAME
from test_kylin_fact
where (LSTG_FORMAT_NAME in ('ABIN')) or  (LSTG_FORMAT_NAME>='FP-GTC' and LSTG_FORMAT_NAME<='Others')
group by LSTG_FORMAT_NAME
;{"scanRowCount":300,"scanBytes":190822,"scanFiles":1,"cuboidId":14336}