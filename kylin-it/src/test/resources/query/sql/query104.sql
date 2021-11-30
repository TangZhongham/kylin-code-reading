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


select concat(meta_categ_name, lstg_format_name) as c1, concat(meta_categ_name, 'CONST') as c2, concat(meta_categ_name, concat(test_sites.site_name, lstg_format_name)) as c3, count(1) as cnt, sum(price) as GMV

 from test_kylin_fact
 left JOIN edw.test_cal_dt as test_cal_dt
 ON test_kylin_fact.cal_dt = test_cal_dt.cal_dt
 left JOIN test_category_groupings
 ON test_kylin_fact.leaf_categ_id = test_category_groupings.leaf_categ_id AND test_kylin_fact.lstg_site_id = test_category_groupings.site_id
 left JOIN edw.test_sites as test_sites
 ON test_kylin_fact.lstg_site_id = test_sites.site_id

 where not ( meta_categ_name not in ('', 'a','Computers') or meta_categ_name not in ('Crafts','Computers'))
 group by concat(meta_categ_name, lstg_format_name), concat(meta_categ_name, 'CONST'), concat(meta_categ_name, concat(test_sites.site_name, lstg_format_name))
;{"scanRowCount":2277,"scanBytes":260571,"scanFiles":2,"cuboidId":79872}