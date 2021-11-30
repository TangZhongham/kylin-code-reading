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

SELECT EXTRACT(YEAR FROM TEST_CAL_DT.WEEK_BEG_DT) AS yr_WEEK_BEG_DT_ok, QUARTER(TEST_CAL_DT.WEEK_BEG_DT) AS qr_WEEK_BEG_DT_ok 
 FROM TEST_KYLIN_FACT 
 inner JOIN EDW.TEST_CAL_DT AS TEST_CAL_DT ON (TEST_KYLIN_FACT.CAL_DT = TEST_CAL_DT.CAL_DT) 
 inner join test_category_groupings AS test_category_groupings
 on test_kylin_fact.leaf_categ_id = test_category_groupings.leaf_categ_id and test_kylin_fact.lstg_site_id = test_category_groupings.site_id
 GROUP BY EXTRACT(YEAR FROM TEST_CAL_DT.WEEK_BEG_DT), QUARTER(TEST_CAL_DT.WEEK_BEG_DT)
;{"scanRowCount":1462,"scanBytes":0,"scanFiles":2,"cuboidId":[262144]}