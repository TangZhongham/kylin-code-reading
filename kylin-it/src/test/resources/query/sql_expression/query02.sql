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

select LSTG_FORMAT_NAME,
    sum(price),
    sum(case
    when LSTG_FORMAT_NAME = 'ABIN' then 2*price
    when LSTG_FORMAT_NAME = 'Auction' then (1+2)*price*(2+3)+(2+3)*(3+2)*(4+5)-4+5
    else 3
    end)
FROM test_kylin_fact
    inner JOIN edw.test_cal_dt as test_cal_dt
    ON test_kylin_fact.cal_dt = test_cal_dt.cal_dt
    inner JOIN test_category_groupings
    ON test_kylin_fact.leaf_categ_id = test_category_groupings.leaf_categ_id AND test_kylin_fact.lstg_site_id = test_category_groupings.site_id
WHERE SLR_SEGMENT_CD < 16
group by LSTG_FORMAT_NAME
having sum((1+2)*price*(2+3)+(2+3)*(3+2)*(4+5)-4+5) > 1800000
order by LSTG_FORMAT_NAME