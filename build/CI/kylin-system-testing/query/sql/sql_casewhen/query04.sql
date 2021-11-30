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

select "KYLIN_SALES"."TRANS_ID",
       case
           when ("KYLIN_SALES"."LSTG_FORMAT_NAME"='Auction') = (1 = 1) then 'B'
           when ("KYLIN_SALES"."LSTG_FORMAT_NAME"='FP-GTC') = (1 = 1) then 'C'
           when ("KYLIN_SALES"."LSTG_FORMAT_NAME"='ABIN') = (1 = 1) then 'D'
           else 'N'
       end as phase
from KYLIN_SALES
where "KYLIN_SALES"."PART_DT" between '2013-01-01' and '2013-01-02'
group by "KYLIN_SALES"."TRANS_ID",
       case
           when ("KYLIN_SALES"."LSTG_FORMAT_NAME"='Auction') = (1 = 1) then 'B'
           when ("KYLIN_SALES"."LSTG_FORMAT_NAME"='FP-GTC') = (1 = 1) then 'C'
           when ("KYLIN_SALES"."LSTG_FORMAT_NAME"='ABIN') = (1 = 1) then 'D'
           else 'N'
       end