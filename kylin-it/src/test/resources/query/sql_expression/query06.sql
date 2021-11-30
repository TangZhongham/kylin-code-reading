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

select T1.LSTG_FORMAT_NAME, avg(T1.gmv/T2.gmv)
from
(select LSTG_FORMAT_NAME, SLR_SEGMENT_CD,
    sum(0.9*(price+50)) as gmv
FROM test_kylin_fact
    inner JOIN edw.test_cal_dt as test_cal_dt
    ON test_kylin_fact.cal_dt = test_cal_dt.cal_dt
    inner JOIN test_category_groupings
    ON test_kylin_fact.leaf_categ_id = test_category_groupings.leaf_categ_id AND test_kylin_fact.lstg_site_id = test_category_groupings.site_id
group by LSTG_FORMAT_NAME, SLR_SEGMENT_CD) T1
inner join
(select LSTG_FORMAT_NAME, SLR_SEGMENT_CD,
    sum(case
    when LSTG_FORMAT_NAME = 'ABIN' then 0.8*(price+50)
    else price
    end) as gmv
FROM test_kylin_fact
    inner JOIN edw.test_cal_dt as test_cal_dt
    ON test_kylin_fact.cal_dt = test_cal_dt.cal_dt
    inner JOIN test_category_groupings
    ON test_kylin_fact.leaf_categ_id = test_category_groupings.leaf_categ_id AND test_kylin_fact.lstg_site_id = test_category_groupings.site_id
group by LSTG_FORMAT_NAME, SLR_SEGMENT_CD) T2
on T1.LSTG_FORMAT_NAME = T2.LSTG_FORMAT_NAME and T1.SLR_SEGMENT_CD = T2.SLR_SEGMENT_CD
group by T1.LSTG_FORMAT_NAME
order by T1.LSTG_FORMAT_NAME