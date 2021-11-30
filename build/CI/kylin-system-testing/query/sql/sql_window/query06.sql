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
select part_dt, lstg_format_name, sum(price) as GMV,
(case lag(sum(price), 1, 0.0) over(partition by lstg_format_name order by part_dt)
when 0.0 then 0 else sum(price)/lag(sum(price), 1, 0.0) over(partition by lstg_format_name order by part_dt) end) as "prev"
from KYLIN_SALES
where part_dt < '2012-02-01'
group by part_dt, lstg_format_name