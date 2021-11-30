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

select fact.cal_dt, sum(fact.price) as sum_price, count(1) as cnt_1
from test_kylin_fact fact 
 left JOIN edw.test_cal_dt as test_cal_dt
 ON fact.cal_dt = test_cal_dt.cal_dt
 left JOIN test_category_groupings
 ON fact.leaf_categ_id = test_category_groupings.leaf_categ_id AND fact.lstg_site_id = test_category_groupings.site_id
 left JOIN edw.test_sites as test_sites
 ON fact.lstg_site_id = test_sites.site_id
inner join
(
	select test_kylin_fact.cal_dt, sum(test_kylin_fact.price) from test_kylin_fact  left JOIN edw.test_cal_dt as test_cal_dt
 ON test_kylin_fact.cal_dt = test_cal_dt.cal_dt
 left JOIN test_category_groupings
 ON test_kylin_fact.leaf_categ_id = test_category_groupings.leaf_categ_id AND test_kylin_fact.lstg_site_id = test_category_groupings.site_id
 left JOIN edw.test_sites as test_sites
 ON test_kylin_fact.lstg_site_id = test_sites.site_id group by test_kylin_fact.cal_dt order by 2 desc limit 7
) cal_2 on fact.cal_dt = cal_2.cal_dt 
group by fact.cal_dt
;{"scanRowCount":2924,"scanBytes":430434,"scanFiles":4,"cuboidId":[262144, 262144]}