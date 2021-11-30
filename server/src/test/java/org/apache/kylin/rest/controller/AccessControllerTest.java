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

package org.apache.kylin.rest.controller;

import org.apache.kylin.cube.CubeInstance;
import org.apache.kylin.metadata.project.ProjectInstance;
import org.apache.kylin.rest.request.AccessRequest;
import org.apache.kylin.rest.response.AccessEntryResponse;
import org.apache.kylin.rest.response.CubeInstanceResponse;
import org.apache.kylin.rest.security.AclEntityType;
import org.apache.kylin.rest.security.AclPermissionType;
import org.apache.kylin.rest.security.ManagedUser;
import org.apache.kylin.rest.service.CubeService;
import org.apache.kylin.rest.service.ProjectService;
import org.apache.kylin.rest.service.ServiceTestBase;
import org.apache.kylin.rest.service.UserGroupService;
import org.apache.kylin.rest.service.UserService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author xduo
 */
public class AccessControllerTest extends ServiceTestBase implements AclEntityType, AclPermissionType {

    private CubeController cubeController;

    private ProjectController projectController;

    private String MODELER = "MODELER";

    private String ANALYST = "ANALYST";

    private String ADMIN = "ADMIN";

    @Autowired
    @Qualifier("projectService")
    ProjectService projectService;

    @Autowired
    @Qualifier("accessController")
    AccessController accessController;

    @Autowired
    @Qualifier("cubeMgmtService")
    CubeService cubeService;

    @Autowired
    @Qualifier("userService")
    UserService userService;

    @Autowired
    @Qualifier("userGroupService")
    private UserGroupService userGroupService;

    @Before
    public void setup() throws Exception {
        super.setup();
        cubeController = new CubeController();
        cubeController.setCubeService(cubeService);
        projectController = new ProjectController();
        projectController.setProjectService(projectService);
    }

    @Test
    public void testGetUserPermissionInPrj() throws IOException {
        List<ProjectInstance> projects = projectController.getProjects(10000, 0);
        assertTrue(projects.size() > 0);
        ProjectInstance project = projects.get(0);
        userGroupService.addGroup("g1");
        userGroupService.addGroup("g2");
        userGroupService.addGroup("g3");
        userGroupService.addGroup("g4");

        ManagedUser user = new ManagedUser("u", "kylin", false, "all_users", "g1", "g2", "g3", "g4");
        userService.createUser(user);

        grantPermission("g1", READ, project.getUuid());
        grantPermission("g2", READ, project.getUuid());

        swichUser("u", "g1", "g2");
        Assert.assertEquals(READ, accessController.getUserPermissionInPrj(project.getName()));

        grantPermission("g1", MANAGEMENT, project.getUuid());
        grantPermission("g2", ADMINISTRATION, project.getUuid());
        grantPermission("g3", OPERATION, project.getUuid());
        grantPermission("g4", READ, project.getUuid());

        swichUser("u", "g1", "g2", "g3", "g4");
        Assert.assertEquals(ADMINISTRATION, accessController.getUserPermissionInPrj(project.getName()));
    }

    @Test
    public void testBasics() throws IOException {
        swichToAdmin();
        List<AccessEntryResponse> aes = accessController.getAccessEntities(CUBE_INSTANCE,
                "a24ca905-1fc6-4f67-985c-38fa5aeafd92");
        Assert.assertTrue(aes.size() == 0);

        AccessRequest accessRequest = getAccessRequest(MODELER, ADMINISTRATION, true);
        aes = accessController.grant(CUBE_INSTANCE, "a24ca905-1fc6-4f67-985c-38fa5aeafd92", accessRequest);
        Assert.assertTrue(aes.size() == 1);

        int aeId = 0;
        for (AccessEntryResponse ae : aes) {
            aeId = (Integer) ae.getId();
        }
        Assert.assertNotNull(aeId);

        accessRequest = new AccessRequest();
        accessRequest.setAccessEntryId(aeId);
        accessRequest.setPermission(READ);

        aes = accessController.update(CUBE_INSTANCE, "a24ca905-1fc6-4f67-985c-38fa5aeafd92", accessRequest);
        Assert.assertTrue(aes.size() == 1);
        for (AccessEntryResponse ae : aes) {
            aeId = (Integer) ae.getId();
        }
        Assert.assertNotNull(aeId);

        accessRequest = new AccessRequest();
        accessRequest.setAccessEntryId(aeId);
        accessRequest.setPermission(READ);
        accessRequest.setSid("ADMIN");
        aes = accessController.revoke(CUBE_INSTANCE, "a24ca905-1fc6-4f67-985c-38fa5aeafd92", accessRequest);
        assertEquals(0, aes.size());
    }

    @Test
    public void testAuthInProjectLevel() throws Exception {
        List<AccessEntryResponse> aes = null;
        swichToAdmin();
        List<ProjectInstance> projects = projectController.getProjects(10000, 0);
        assertTrue(projects.size() > 0);
        ProjectInstance project = projects.get(0);
        swichToAnalyst();
        projects = projectController.getProjects(10000, 0);
        assertEquals(0, projects.size());
        //grant auth in project level
        swichToAdmin();
        aes = accessController.grant(PROJECT_INSTANCE, project.getUuid(), getAccessRequest(ANALYST, READ, true));
        swichToAnalyst();
        projects = projectController.getProjects(10000, 0);
        assertEquals(1, projects.size());

        //revoke auth
        swichToAdmin();
        AccessRequest request = getAccessRequest(ANALYST, READ, true);
        request.setAccessEntryId((Integer) aes.get(0).getId());
        accessController.revoke(PROJECT_INSTANCE, project.getUuid(), request);
        swichToAnalyst();
        projects = projectController.getProjects(10000, 0);
        assertEquals(0, projects.size());
    }

    @Test
    public void testAuthInCubeLevel() throws Exception {
        swichToAdmin();
        List<CubeInstanceResponse> cubes = cubeController.getCubes(null, null, null, 100000, 0);
        assertTrue(cubes.size() > 0);
        CubeInstance cube = cubes.get(0);
        swichToAnalyst();
        cubes.clear();
        try {
            cubes = cubeController.getCubes(null, null, null, 100000, 0);
        } catch (AccessDeniedException e) {
            //correct
        }
        assertTrue(cubes.size() == 0);

        //grant auth
        AccessRequest accessRequest = getAccessRequest(ANALYST, READ, true);
        try {
            accessController.grant(CUBE_INSTANCE, cube.getUuid(), accessRequest);
            fail("ANALYST should not have auth to grant");
        } catch (AccessDeniedException e) {
            //correct
        }
        swichToAdmin();
        List<ProjectInstance> projects = projectController.getProjects(10000, 0);
        List<AccessEntryResponse> aes = accessController.grant(PROJECT_INSTANCE, projects.get(0).getUuid(),
                accessRequest);
        Assert.assertTrue(aes.size() == 1);
        swichToAnalyst();
        cubes = cubeController.getCubes(null, null, "default", 100000, 0);
        assertTrue(cubes.size() > 0);
        cubes.clear();

        //revoke auth
        try {
            accessController.revoke(PROJECT_INSTANCE, projects.get(0).getUuid(), accessRequest);
            fail("ANALYST should not have auth to revoke");
        } catch (AccessDeniedException e) {
            //correct
        }
        swichToAdmin();
        accessRequest.setAccessEntryId((Integer) aes.get(0).getId());
        accessController.revoke(PROJECT_INSTANCE, projects.get(0).getUuid(), accessRequest);
        swichToAnalyst();
        try {
            cubes = cubeController.getCubes(null, null, null, 10000, 0);
        } catch (AccessDeniedException e) {
            //correct
        }
        assertEquals(0, cubes.size());
    }

    private void swichUser(String name, String... auth) {
        Authentication token = new TestingAuthenticationToken(name, name, auth);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private void swichToAdmin() {
        Authentication adminAuth = new TestingAuthenticationToken("ADMIN", "ADMIN", "ROLE_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(adminAuth);
    }

    private void swichToAnalyst() {
        Authentication analystAuth = new TestingAuthenticationToken("ANALYST", "ANALYST", "ROLE_ANALYST");
        SecurityContextHolder.getContext().setAuthentication(analystAuth);
    }

    private AccessRequest getAccessRequest(String role, String permission, boolean isPrincipal) {
        AccessRequest accessRequest = new AccessRequest();
        accessRequest.setPermission(permission);
        accessRequest.setSid(role);
        accessRequest.setPrincipal(isPrincipal);
        return accessRequest;
    }

    private void grantPermission(String sid, String permission, String uuid) throws IOException {
        swichToAdmin();
        AccessRequest groupAccessRequest = getAccessRequest(sid, permission, false);
        accessController.grant(PROJECT_INSTANCE, uuid, groupAccessRequest);
    }

    @Test
    public void testIndexInAclOfResponse() throws IOException {
        swichToAdmin();
        List<ProjectInstance> projects = projectController.getProjects(10000, 0);
        assertTrue(projects.size() > 0);
        ProjectInstance project = projects.get(0);
        ManagedUser user = new ManagedUser("user_0", "kylin", false, "all_users");
        userService.createUser(user);
        user = new ManagedUser("user_1", "kylin", false, "all_users");
        userService.createUser(user);
        user = new ManagedUser("user_2", "kylin", false, "all_users");
        userService.createUser(user);
        List<AccessEntryResponse> aes = accessController.getAccessEntities(PROJECT_INSTANCE,
            project.getUuid());
        assertEquals(0, aes.size());

        AccessRequest accessRequest = getAccessRequest("user_0", ADMINISTRATION, true);
        aes = accessController.grant(PROJECT_INSTANCE, project.getUuid(), accessRequest);
        assertTrue(checkAccessEntryResponse(aes, accessController.getAccessEntities(PROJECT_INSTANCE,
            project.getUuid())));
        assertEquals(1, aes.size());

        int aeId = 0;
        for (AccessEntryResponse ae : aes) {
            aeId = (Integer) ae.getId();
        }
        accessRequest = new AccessRequest();
        accessRequest.setAccessEntryId(aeId);
        accessRequest.setPermission(READ);

        aes = accessController.update(PROJECT_INSTANCE, project.getUuid(), accessRequest);
        assertTrue(checkAccessEntryResponse(aes, accessController.getAccessEntities(PROJECT_INSTANCE,
            project.getUuid())));
        assertEquals(1, aes.size());

        accessRequest = getAccessRequest("user_1", ADMINISTRATION, true);
        aes = accessController.grant(PROJECT_INSTANCE, project.getUuid(), accessRequest);
        assertTrue(checkAccessEntryResponse(aes, accessController.getAccessEntities(PROJECT_INSTANCE,
            project.getUuid())));
        assertEquals(2, aes.size());

        accessRequest = getAccessRequest("user_2", ADMINISTRATION, true);
        aes = accessController.grant(PROJECT_INSTANCE, project.getUuid(), accessRequest);
        assertTrue(checkAccessEntryResponse(aes, accessController.getAccessEntities(PROJECT_INSTANCE,
            project.getUuid())));
        assertEquals(3, aes.size());


        accessRequest = new AccessRequest();
        accessRequest.setAccessEntryId(1);
        accessRequest.setSid("user_1");
        accessRequest.setPrincipal(true);
        aes = accessController.revoke(PROJECT_INSTANCE, project.getUuid(), accessRequest);
        assertTrue(checkAccessEntryResponse(aes, accessController.getAccessEntities(PROJECT_INSTANCE,
            project.getUuid())));
        assertEquals(2, aes.size());

        accessRequest = getAccessRequest("user_1", ADMINISTRATION, true);
        aes = accessController.grant(PROJECT_INSTANCE, project.getUuid(), accessRequest);
        assertTrue(checkAccessEntryResponse(aes, accessController.getAccessEntities(PROJECT_INSTANCE,
            project.getUuid())));
        assertEquals(3, aes.size());

        accessRequest = new AccessRequest();
        accessRequest.setAccessEntryId(1);
        accessRequest.setSid("user_1");
        accessRequest.setPrincipal(true);
        aes = accessController.revoke(PROJECT_INSTANCE, project.getUuid(), accessRequest);
        assertTrue(checkAccessEntryResponse(aes, accessController.getAccessEntities(PROJECT_INSTANCE,
            project.getUuid())));
        assertEquals(2, aes.size());

        accessRequest = new AccessRequest();
        accessRequest.setAccessEntryId(0);
        accessRequest.setSid("user_0");
        accessRequest.setPrincipal(true);
        aes = accessController.revoke(PROJECT_INSTANCE, project.getUuid(), accessRequest);
        assertTrue(checkAccessEntryResponse(aes, accessController.getAccessEntities(PROJECT_INSTANCE,
            project.getUuid())));
        assertEquals(1, aes.size());

        accessRequest = new AccessRequest();
        accessRequest.setAccessEntryId(0);
        accessRequest.setSid("user_2");
        accessRequest.setPrincipal(true);
        aes = accessController.revoke(PROJECT_INSTANCE, project.getUuid(), accessRequest);
        assertTrue(checkAccessEntryResponse(aes, accessController.getAccessEntities(PROJECT_INSTANCE,
            project.getUuid())));
        assertEquals(0, aes.size());
    }

    private boolean checkAccessEntryResponse(List<AccessEntryResponse> left, List<AccessEntryResponse> right) {
        for (int i = 0; i < left.size(); i++) {
            AccessEntryResponse leftAer = left.get(i);
            AccessEntryResponse rightAer = right.get(i);
            if (!(leftAer.getId().equals(rightAer.getId())
                && leftAer.getPermission().getMask() == rightAer.getPermission().getMask()
                && leftAer.getSid().equals(rightAer.getSid())
                && leftAer.isGranting() == rightAer.isGranting())) {
                return false;
            }
        }
        return true;
    }
}
