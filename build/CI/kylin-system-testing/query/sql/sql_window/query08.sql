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
first_value(sum(price)) over (partition by lstg_format_name order by cast(part_dt as timestamp) range interval '3' day preceding) as "prev 3 days",
last_value(sum(price)) over (partition by lstg_format_name order by cast(part_dt as timestamp) range between current row and interval '3' day following) as "next 3 days"
from KYLIN_SALES
where part_dt < '2012-02-01'
group by part_dt, lstg_format_name