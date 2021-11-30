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

package org.apache.kylin.rest.service;

import static org.apache.kylin.rest.security.AclEntityType.PROJECT_INSTANCE;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kylin.common.persistence.AclEntity;
import org.apache.kylin.common.persistence.RootPersistentEntity;
import org.apache.kylin.metadata.MetadataConstants;
import org.apache.kylin.metadata.project.ProjectInstance;
import org.apache.kylin.rest.response.AccessEntryResponse;
import org.apache.kylin.rest.security.AclPermission;
import org.apache.kylin.rest.security.AclPermissionFactory;
import org.apache.kylin.rest.security.springacl.MutableAclRecord;
import org.apache.kylin.rest.service.AclServiceTest.MockAclEntity;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.AccessControlEntry;
import org.springframework.security.acls.model.Acl;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.acls.model.Sid;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 */
public class AccessServiceTest extends ServiceTestBase {

    @Autowired
    @Qualifier("accessService")
    AccessService accessService;

    @Autowired
    @Qualifier("projectService")
    ProjectService projectService;

    @Test
    public void testRevokeProjectPermission() throws IOException {
        List<ProjectInstance> projects = projectService.listProjects(10000, 0);
        assertTrue(projects.size() > 0);
        ProjectInstance project = projects.get(0);
        PrincipalSid sid = new PrincipalSid("ANALYST");
        RootPersistentEntity ae = accessService.getAclEntity(PROJECT_INSTANCE, project.getUuid());
        accessService.grant(ae, AclPermission.ADMINISTRATION, sid);
        Assert.assertEquals(1, accessService.getAcl(ae).getEntries().size());
        accessService.revokeProjectPermission("ANALYST", MetadataConstants.TYPE_USER);
        Assert.assertEquals(0, accessService.getAcl(ae).getEntries().size());
    }

    @Test
    public void testBasics() throws JsonProcessingException {
        Sid adminSid = accessService.getSid("ADMIN", true);
        Assert.assertNotNull(adminSid);
        Assert.assertNotNull(AclPermissionFactory.getPermissions());

        AclEntity ae = new AclServiceTest.MockAclEntity("test-domain-object");
        accessService.clean(ae, true);
        AclEntity attachedEntity = new AclServiceTest.MockAclEntity("attached-domain-object");
        accessService.clean(attachedEntity, true);

        // test getAcl
        Acl acl = accessService.getAcl(ae);
        Assert.assertNull(acl);

        // test init
        acl = accessService.init(ae, AclPermission.ADMINISTRATION);
        Assert.assertTrue(((PrincipalSid) acl.getOwner()).getPrincipal().equals("ADMIN"));
        Assert.assertEquals(accessService.generateAceResponses(acl).size(), 1);
        AccessEntryResponse aer = accessService.generateAceResponses(acl).get(0);
        Assert.assertTrue(aer.getId() != null);
        Assert.assertTrue(aer.getPermission() == AclPermission.ADMINISTRATION);
        Assert.assertTrue(((PrincipalSid) aer.getSid()).getPrincipal().equals("ADMIN"));

        // test grant
        Sid modeler = accessService.getSid("MODELER", true);
        acl = accessService.grant(ae, AclPermission.ADMINISTRATION, modeler);
        Assert.assertEquals(accessService.generateAceResponses(acl).size(), 2);

        int modelerEntryId = 0;
        for (AccessControlEntry ace : acl.getEntries()) {
            PrincipalSid sid = (PrincipalSid) ace.getSid();

            if (sid.getPrincipal().equals("MODELER")) {
                modelerEntryId = (Integer) ace.getId();
                Assert.assertTrue(ace.getPermission() == AclPermission.ADMINISTRATION);
            }
        }

        // test update
        acl = accessService.update(ae, modelerEntryId, AclPermission.READ);

        Assert.assertEquals(accessService.generateAceResponses(acl).size(), 2);

        for (AccessControlEntry ace : acl.getEntries()) {
            PrincipalSid sid = (PrincipalSid) ace.getSid();

            if (sid.getPrincipal().equals("MODELER")) {
                modelerEntryId = (Integer) ace.getId();
                Assert.assertTrue(ace.getPermission() == AclPermission.READ);
            }
        }

        accessService.clean(attachedEntity, true);

        Acl attachedEntityAcl = accessService.getAcl(attachedEntity);
        Assert.assertNull(attachedEntityAcl);
        attachedEntityAcl = accessService.init(attachedEntity, AclPermission.ADMINISTRATION);

        accessService.inherit(attachedEntity, ae);

        attachedEntityAcl = accessService.getAcl(attachedEntity);
        Assert.assertTrue(attachedEntityAcl.getParentAcl() != null);
        Assert.assertTrue(attachedEntityAcl.getParentAcl().getObjectIdentity().getIdentifier().equals("test-domain-object"));
        Assert.assertTrue(attachedEntityAcl.getEntries().size() == 1);

        // test revoke
        acl = accessService.revoke(ae, modelerEntryId);
        Assert.assertEquals(accessService.generateAceResponses(acl).size(), 1);

        // test clean
        accessService.clean(ae, true);
        acl = accessService.getAcl(ae);
        Assert.assertNull(acl);

        attachedEntityAcl = accessService.getAcl(attachedEntity);
        Assert.assertNull(attachedEntityAcl);
    }

    @Test
    public void testBatchGrant() {
        AclEntity ae = new AclServiceTest.MockAclEntity("batch-grant");
        final Map<Sid, Permission> sidToPerm = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            sidToPerm.put(new PrincipalSid("u" + i), AclPermission.ADMINISTRATION);
        }
        accessService.batchGrant(ae, sidToPerm);
        MutableAclRecord acl = accessService.getAcl(ae);
        List<AccessControlEntry> e = acl.getEntries();
        Assert.assertEquals(10, e.size());
        for (int i = 0; i < e.size(); i++) {
            Assert.assertEquals(new PrincipalSid("u" + i), e.get(i).getSid());
        }
    }

    @Ignore
    @Test
    public void test100000Entries() throws JsonProcessingException {
        MockAclEntity ae = new MockAclEntity("100000Entries");
        long time = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            if (i % 10 == 0) {
                long now = System.currentTimeMillis();
                System.out.println((now - time) + " ms for last 10 entries, total " + i);
                time = now;
            }
            Sid sid = accessService.getSid("USER" + i, true);
            accessService.grant(ae, AclPermission.OPERATION, sid);
        }
    }

    @Test
    public void testCaseInsensitiveProjectPermission() {
        List<ProjectInstance> projects = projectService.listProjects(10000, 0);
        assertTrue(projects.size() > 0);
        ProjectInstance project = projects.get(0);
        PrincipalSid sid = new PrincipalSid("ANALYST");
        RootPersistentEntity ae = accessService.getAclEntity(PROJECT_INSTANCE, project.getUuid());
        accessService.grant(ae, AclPermission.READ, sid);
        Assert.assertEquals(1, accessService.getAcl(ae).getEntries().size());

        Authentication admin = SecurityContextHolder.getContext().getAuthentication();
        // Upper case username
        Authentication analystAuth = new TestingAuthenticationToken("ANALYST", "ANALYST", "ROLE_ANALYST");
        SecurityContextHolder.getContext().setAuthentication(analystAuth);
        Assert.assertEquals("ANALYST",  SecurityContextHolder.getContext().getAuthentication().getName());
        Assert.assertEquals("READ", accessService.getUserPermissionInPrj(project.getName()));

        // lower case username
        analystAuth = new TestingAuthenticationToken("analyst", "ANALYST", "ROLE_ANALYST");
        SecurityContextHolder.getContext().setAuthentication(analystAuth);
        Assert.assertEquals("analyst",  SecurityContextHolder.getContext().getAuthentication().getName());
        Assert.assertEquals("READ", accessService.getUserPermissionInPrj(project.getName()));

        SecurityContextHolder.getContext().setAuthentication(admin);
        accessService.revokeProjectPermission("ANALYST", MetadataConstants.TYPE_USER);
        Assert.assertEquals(0, accessService.getAcl(ae).getEntries().size());
    }

    @Test
    public void testIndexInAclRecord() throws IOException {
        List<ProjectInstance> projects = projectService.listProjects(10000, 0);
        assertTrue(projects.size() > 0);
        ProjectInstance project = projects.get(0);
        RootPersistentEntity ae = accessService.getAclEntity(PROJECT_INSTANCE, project.getUuid());
        final Map<Sid, Permission> sidToPerm = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            sidToPerm.put(new PrincipalSid("user_" + i), AclPermission.ADMINISTRATION);
        }
        accessService.batchGrant(ae, sidToPerm);
        Assert.assertEquals(3, accessService.getAcl(ae).getEntries().size());

        // check revoke
        MutableAclRecord newRecord = accessService.revoke(ae, 1);
        Assert.assertTrue(checkIndexInAclRecord(newRecord, accessService.getAcl(ae)));
        Assert.assertEquals(2, accessService.getAcl(ae).getEntries().size());

        // check grant
        PrincipalSid sid = new PrincipalSid("user_1");
        newRecord = accessService.grant(ae, AclPermission.ADMINISTRATION, sid);
        Assert.assertTrue(checkIndexInAclRecord(newRecord, accessService.getAcl(ae)));
        Assert.assertEquals(3, accessService.getAcl(ae).getEntries().size());

        // check update
        newRecord = accessService.update(ae, 2, AclPermission.OPERATION);
        Assert.assertTrue(checkIndexInAclRecord(newRecord, accessService.getAcl(ae)));
        Assert.assertEquals(3, accessService.getAcl(ae).getEntries().size());

        newRecord = accessService.revoke(ae, 0);
        Assert.assertTrue(checkIndexInAclRecord(newRecord, accessService.getAcl(ae)));
        Assert.assertEquals(2, accessService.getAcl(ae).getEntries().size());

        newRecord = accessService.revoke(ae, 0);
        Assert.assertTrue(checkIndexInAclRecord(newRecord, accessService.getAcl(ae)));
        Assert.assertEquals(1, accessService.getAcl(ae).getEntries().size());

        newRecord = accessService.revoke(ae, 0);
        Assert.assertTrue(checkIndexInAclRecord(newRecord, accessService.getAcl(ae)));
        Assert.assertEquals(0, accessService.getAcl(ae).getEntries().size());
    }

    private boolean checkIndexInAclRecord(MutableAclRecord left, MutableAclRecord right) {
        for (int i = 0; i < left.getEntries().size(); i++) {
            AccessControlEntry leftAce = left.getEntries().get(i);
            AccessControlEntry rightAce = right.getEntries().get(i);
            if (!(leftAce.equals(rightAce) && leftAce.getId().equals(rightAce.getId()))) {
                return false;
            }
        }
        return true;
    }
}
