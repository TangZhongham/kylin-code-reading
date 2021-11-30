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

-- unionall subquery under join
select count(*) as cnt
FROM TEST_KYLIN_FACT as TEST_A
join (
    select * from TEST_KYLIN_FACT where CAL_DT < DATE '2012-02-01'
    union all
    select * from TEST_KYLIN_FACT where CAL_DT > DATE '2013-12-31'
) TEST_B
on TEST_A.ORDER_ID = TEST_B.ORDER_ID
group by TEST_A.SELLER_ID
;{"scanRowCount":30000,"scanBytes":0,"scanFiles":3,"cuboidId":[2097151,2097151,2097151]}