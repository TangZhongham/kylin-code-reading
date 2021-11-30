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

SELECT LSTG_FORMAT_NAME,LSTG_SITE_ID,SLR_SEGMENT_CD, test_cal_dt.WEEK_BEG_DT,
       sum(price) as GMV, COUNT(1) AS TRANS_CNT, min(price) as minP, max(price) as maxP,
       COUNT(DISTINCT TEST_COUNT_DISTINCT_BITMAP), COUNT(DISTINCT SELLER_ID),
       intersect_count( TEST_COUNT_DISTINCT_BITMAP, lstg_format_name, array['FP-GTC']) as a,
       intersect_value( TEST_COUNT_DISTINCT_BITMAP, lstg_format_name, array['FP-GTC']) as a
FROM test_kylin_fact
         inner JOIN edw.test_cal_dt as test_cal_dt
                    ON test_kylin_fact.cal_dt = test_cal_dt.cal_dt
         inner JOIN test_category_groupings
                    ON test_kylin_fact.leaf_categ_id = test_category_groupings.leaf_categ_id AND test_kylin_fact.lstg_site_id = test_category_groupings.site_id
         inner JOIN edw.test_sites as test_sites
                    ON test_kylin_fact.lstg_site_id = test_sites.site_id
WHERE test_cal_dt.WEEK_BEG_DT >= '2001-09-09'
  AND test_cal_dt.WEEK_BEG_DT <= '2018-05-16'
GROUP BY LSTG_FORMAT_NAME,LSTG_SITE_ID,SLR_SEGMENT_CD,test_cal_dt.WEEK_BEG_DT
;{"scanRowCount":10280,"scanBytes":0,"scanFiles":3,"cuboidId":[276480],"exactlyMatched":[false]}