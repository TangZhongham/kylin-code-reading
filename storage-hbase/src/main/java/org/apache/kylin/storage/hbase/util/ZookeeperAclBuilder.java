/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.apache.kylin.storage.hbase.util;

import java.util.Collections;
import java.util.List;

import org.apache.curator.framework.CuratorFrameworkFactory.Builder;
import org.apache.curator.framework.api.ACLProvider;
import org.apache.hadoop.util.ZKUtil;
import org.apache.kylin.common.KylinConfig;
import org.apache.zookeeper.data.ACL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by peng.jianhua on 17-6-5.
 */
public class ZookeeperAclBuilder {

    private static Logger logger = LoggerFactory.getLogger(ZookeeperAclBuilder.class);

    private List<ACL> zkAcls;
    private List<ZKUtil.ZKAuthInfo> zkAuthInfo;
    private boolean isNeedAcl = KylinConfig.getInstanceFromEnv().isZookeeperAclEnabled();

    public Builder setZKAclBuilder(Builder builder) {
        Builder aclBuilder;
        ACLProvider aclProvider;

        if (!isNeedAcl()) {
            return builder;
        }

        aclProvider = new ACLProvider() {
            private List<ACL> acl;

            @Override
            public List<ACL> getDefaultAcl() {
                if (acl == null) {
                    this.acl = zkAcls;
                }
                return acl;
            }

            @Override
            public List<ACL> getAclForPath(String path) {
                return acl;
            }
        };

        aclBuilder = builder.aclProvider(aclProvider);
        for (ZKUtil.ZKAuthInfo auth : zkAuthInfo) {
            aclBuilder = aclBuilder.authorization(auth.getScheme(), auth.getAuth());
        }
        return aclBuilder;
    }

    public ZookeeperAclBuilder invoke() {
        try {
            if (isNeedAcl()) {
                zkAcls = getZKAcls();
                zkAuthInfo = getZKAuths();
            }
        } catch (Exception e) {
            isNeedAcl = false;
            return this;
        }
        return this;
    }

    public static List<ZKUtil.ZKAuthInfo> getZKAuths() throws Exception {
        // Parse Auths from configuration.
        String zkAuthConf = KylinConfig.getInstanceFromEnv().getZKAuths();
        try {
            zkAuthConf = ZKUtil.resolveConfIndirection(zkAuthConf);
            if (zkAuthConf != null) {
                return ZKUtil.parseAuth(zkAuthConf);
            } else {
                return Collections.emptyList();
            }
        } catch (Exception e) {
            logger.error("Couldn't read Auth based on 'kylin.env.zookeeper.zk-auth' in kylin.properties");
            throw e;
        }
    }

    public static List<ACL> getZKAcls() throws Exception {
        // Parse ACLs from configuration.
        String zkAclConf = KylinConfig.getInstanceFromEnv().getZKAcls();
        try {
            zkAclConf = ZKUtil.resolveConfIndirection(zkAclConf);
            return ZKUtil.parseACLs(zkAclConf);
        } catch (Exception e) {
            logger.error("Couldn't read ACLs based on 'kylin.env.zookeeper.zk-acl' in kylin.properties");
            throw e;
        }
    }

    public boolean isNeedAcl() {
        return isNeedAcl;
    }
}
