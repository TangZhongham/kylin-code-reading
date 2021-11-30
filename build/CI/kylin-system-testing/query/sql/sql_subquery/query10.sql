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

select t1.leaf_categ_id, max_price, min_price, sum_price
from
(select leaf_categ_id, sum(price) as sum_price from KYLIN_SALES group by leaf_categ_id) t1
join
(select leaf_categ_id, max(price) as max_price from KYLIN_SALES group by leaf_categ_id) t2
on t1.leaf_categ_id = t2.leaf_categ_id
join
(select leaf_categ_id, max(price) as min_price from KYLIN_SALES group by leaf_categ_id) t3
on t1.leaf_categ_id = t3.leaf_categ_id
order by t1.leaf_categ_id