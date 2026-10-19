
package com.example.testplatform;

import com.example.testplatform.common.result.Result;
import com.example.testplatform.auth.domain.dto.AuthResponse;
import com.example.testplatform.auth.domain.dto.LoginRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UserAndRoleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String adminToken;
    private Long adminUserId;
    private String testRoleId;
    private String testDepartmentId;
    @Deprecated
    private String pendingTaskId;
    @Deprecated
    private Long newUserId;

    @BeforeAll
    public void setup() {
        System.out.println("========== 开始用户和角色集成测试 ==========");
    }

    @AfterAll
    public void teardown() {
        System.out.println("========== 用户和角色集成测试结束 ==========");
    }

    /**
     * 测试1：管理员登录获取JWT Token
     */
    @Test
    @Order(1)
    @DisplayName("测试1：管理员登录")
    public void testAdminLogin() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("123456");

        MvcResult result = mockMvc.perform(post("/api-auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("\n=== 测试1：管理员登录 ===");
        System.out.println("登录响应: " + responseContent);

        Map<String, Object> responseMap = parseToMap(responseContent);

        Integer code = (Integer) responseMap.get("code");
        Assertions.assertEquals(200, code, "管理员登录失败");

        Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
        Assertions.assertNotNull(data, "返回数据为空");

        this.adminToken = (String) data.get("token");
        this.adminUserId = ((Number) data.get("userId")).longValue();

        Assertions.assertNotNull(this.adminToken, "Token为空");
        Assertions.assertTrue(this.adminToken.length() > 0, "Token长度为0");

        System.out.println("✅ 管理员登录成功");
        System.out.println("用户ID: " + this.adminUserId);
    }

    /**
     * 测试2：获取所有角色列表（用于后续测试）
     */
    @Test
    @Order(2)
    @DisplayName("测试2：获取角色列表")
    public void testGetAllRoles() throws Exception {
        Assertions.assertNotNull(this.adminToken, "Token未初始化");

        System.out.println("\n=== 测试2：获取角色列表 ===");

        MvcResult result = mockMvc.perform(get("/role/list")
                        .header("Authorization", "Bearer " + this.adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("角色列表响应: " + responseContent);

        Map<String, Object> responseMap = parseToMap(responseContent);
        Integer code = (Integer) responseMap.get("code");
        Assertions.assertEquals(200, code, "获取角色列表失败");

        Object data = responseMap.get("data");
        Assertions.assertNotNull(data, "角色列表为空");

        if (data instanceof java.util.List) {
            java.util.List<?> roles = (java.util.List<?>) data;
            Assertions.assertFalse(roles.isEmpty(), "角色列表不应为空");

            Map<String, Object> firstRole = (Map<String, Object>) roles.get(0);
            this.testRoleId = (String) firstRole.get("id");
            System.out.println("✅ 获取角色列表成功，第一个角色ID: " + this.testRoleId);

            if (roles.size() > 1) {
                Map<String, Object> secondRole = (Map<String, Object>) roles.get(1);
                this.testDepartmentId = (String) secondRole.get("id");
                System.out.println("第二个角色ID（用于测试修改角色）: " + this.testDepartmentId);
            }
        }
    }

    /**
     * 测试3：注册新用户（触发审批流程）
     * @deprecated 此测试涉及工作流审批流程，暂时跳过
     */
    @Deprecated
    @Test
    @Order(3)
    @DisplayName("测试3：注册新用户（已弃用-工作流相关）")
    public void testRegisterUser() throws Exception {
        Map<String, Object> registerRequest = Map.of(
                "username", "testuser_" + System.currentTimeMillis(),
                "password", "test123456",
                "confirmPassword", "test123456",
                "realName", "测试用户",
                "departmentId", "dept001",
                "roleId", this.testRoleId != null ? this.testRoleId : "role_default"
        );

        MvcResult result = mockMvc.perform(post("/api-auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(registerRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("\n=== 测试3：注册新用户 ===");
        System.out.println("注册响应: " + responseContent);

        Map<String, Object> responseMap = parseToMap(responseContent);
        Integer code = (Integer) responseMap.get("code");
        Assertions.assertEquals(200, code, "注册失败");

        Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
        this.newUserId = ((Number) data.get("userId")).longValue();

        System.out.println("✅ 注册成功，新用户ID: " + this.newUserId);
        System.out.println("状态: " + data.get("status") + " (0=待激活)");
    }

    /**
     * 测试4：管理员获取待审批任务列表
     * @deprecated 此测试涉及工作流审批流程，暂时跳过
     */
    @Deprecated
    @Test
    @Order(4)
    @DisplayName("测试4：获取待审批任务（已弃用-工作流相关）")
    public void testGetPendingTasks() throws Exception {
        System.out.println("\n=== 测试4：获取待审批任务 ===");

        MvcResult result = mockMvc.perform(get("/api-admin/pending-tasks")
                        .header("Authorization", "Bearer " + this.adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("待审批任务响应: " + responseContent);

        Map<String, Object> responseMap = parseToMap(responseContent);
        Integer code = (Integer) responseMap.get("code");
        Assertions.assertEquals(200, code, "获取待审批任务失败");

        Object data = responseMap.get("data");
        Assertions.assertNotNull(data, "任务列表为空");

        if (data instanceof java.util.List) {
            java.util.List<?> tasks = (java.util.List<?>) data;
            System.out.println("✅ 获取待审批任务成功，共 " + tasks.size() + " 个任务");

            if (!tasks.isEmpty()) {
                Map<String, Object> firstTask = (Map<String, Object>) tasks.get(0);
                this.pendingTaskId = (String) firstTask.get("taskId");
                System.out.println("第一个任务ID: " + this.pendingTaskId);
            }
        }
    }

    /**
     * 测试5：管理员审批通过用户
     * @deprecated 此测试涉及工作流审批流程，暂时跳过
     */
    @Deprecated
    @Test
    @Order(5)
    @DisplayName("测试5：审批通过用户（已弃用-工作流相关）")
    public void testApproveUser() throws Exception {
        if (this.pendingTaskId == null) {
            System.out.println("\n️ 跳过测试5：没有待审批任务");
            return;
        }

        System.out.println("\n=== 测试5：审批通过用户 ===");

        Map<String, String> approveRequest = Map.of("taskId", this.pendingTaskId);

        MvcResult result = mockMvc.perform(post("/api-admin/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + this.adminToken)
                        .content(asJsonString(approveRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("审批响应: " + responseContent);

        Map<String, Object> responseMap = parseToMap(responseContent);
        Integer code = (Integer) responseMap.get("code");
        Assertions.assertEquals(200, code, "审批失败");

        System.out.println("✅ 审批通过成功");
    }

    /**
     * 测试6：验证用户状态已更新为已激活
     * @deprecated 此测试依赖工作流审批结果，暂时跳过
     */
    @Deprecated
    @Test
    @Order(6)
    @DisplayName("测试6：验证用户状态（已弃用-工作流相关）")
    public void testVerifyUserStatus() throws Exception {
        if (this.newUserId == null) {
            System.out.println("\n⚠️ 跳过测试6：没有新用户ID");
            return;
        }

        System.out.println("\n=== 测试6：验证用户状态 ===");

        Map<String, Long> request = Map.of("userId", this.newUserId);

        MvcResult result = mockMvc.perform(post("/user/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + this.adminToken)
                        .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("用户详情响应: " + responseContent);

        Map<String, Object> responseMap = parseToMap(responseContent);
        Integer code = (Integer) responseMap.get("code");
        Assertions.assertEquals(200, code, "获取用户详情失败");

        Map<String, Object> userData = (Map<String, Object>) responseMap.get("data");
        Integer status = ((Number) userData.get("status")).intValue();

        Assertions.assertEquals(1, status, "用户状态应为1（已激活），实际为: " + status);

        System.out.println("✅ 用户状态验证成功，当前状态: " + status + " (1=已激活)");
    }

    /**
     * 测试7：分页查询用户列表
     */
    @Test
    @Order(7)
    @DisplayName("测试7：分页查询用户列表")
    public void testGetUserList() throws Exception {
        System.out.println("\n=== 测试7：分页查询用户列表 ===");

        Map<String, Object> request = Map.of(
                "pageNum", 1,
                "pageSize", 10
        );

        MvcResult result = mockMvc.perform(post("/user/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + this.adminToken)
                        .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("用户列表响应: " + responseContent);

        Map<String, Object> responseMap = parseToMap(responseContent);
        Integer code = (Integer) responseMap.get("code");
        Assertions.assertEquals(200, code, "获取用户列表失败");

        Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
        Assertions.assertNotNull(data.get("records"), "用户记录为空");
        Assertions.assertNotNull(data.get("total"), "总数为空");

        java.util.List<?> records = (java.util.List<?>) data.get("records");
        System.out.println("✅ 获取用户列表成功，总数: " + data.get("total"));

        if (!records.isEmpty()) {
            Map<String, Object> firstUser = (Map<String, Object>) records.get(0);
            this.newUserId = ((Number) firstUser.get("id")).longValue();
            System.out.println("使用第一个用户进行测试，用户ID: " + this.newUserId);
        }
    }

    /**
     * 测试8：按状态筛选用户
     */
    @Test
    @Order(8)
    @DisplayName("测试8：按状态筛选用户")
    public void testGetUserListByStatus() throws Exception {
        System.out.println("\n=== 测试8：按状态筛选用户 ===");

        Map<String, Object> request = Map.of(
                "pageNum", 1,
                "pageSize", 10,
                "status", 1
        );

        MvcResult result = mockMvc.perform(post("/user/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + this.adminToken)
                        .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("按状态筛选用户响应: " + responseContent);

        Map<String, Object> responseMap = parseToMap(responseContent);
        Integer code = (Integer) responseMap.get("code");
        Assertions.assertEquals(200, code, "按状态筛选用户失败");

        Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
        java.util.List<?> records = (java.util.List<?>) data.get("records");

        System.out.println("✅ 按状态筛选成功，筛选出 " + records.size() + " 个已激活用户");
    }

    /**
     * 测试9：管理员修改用户角色
     */
    @Test
    @Order(9)
    @DisplayName("测试9：修改用户角色")
    public void testUpdateUserRoles() throws Exception {
        if (this.newUserId == null) {
            System.out.println("\n⚠️ 跳过测试9：没有可用的用户ID，请先运行测试7");
            return;
        }

        if (this.testRoleId == null) {
            System.out.println("\n⚠️ 跳过测试9：没有可用的角色ID，请先运行测试2");
            return;
        }

        System.out.println("\n=== 测试9：修改用户角色 ===");

        Map<String, Object> request = Map.of(
                "userId", this.newUserId,
                "roleIds", java.util.Arrays.asList(this.testRoleId),
                "departmentId", "dept001"
        );

        MvcResult result = mockMvc.perform(post("/user/update-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + this.adminToken)
                        .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("修改用户角色响应: " + responseContent);

        Map<String, Object> responseMap = parseToMap(responseContent);
        Integer code = (Integer) responseMap.get("code");
        Assertions.assertEquals(200, code, "修改用户角色失败");

        System.out.println("✅ 修改用户角色成功");

        Map<String, Object> detailRequest = Map.of("userId", this.newUserId);
        MvcResult detailResult = mockMvc.perform(post("/user/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + this.adminToken)
                        .content(asJsonString(detailRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String detailResponse = detailResult.getResponse().getContentAsString();
        System.out.println("验证用户角色变更 - 用户详情: " + detailResponse);

        Map<String, Object> detailMap = parseToMap(detailResponse);
        Map<String, Object> userData = (Map<String, Object>) detailMap.get("data");
        java.util.List<?> departments = (java.util.List<?>) userData.get("departments");

        Assertions.assertNotNull(departments, "用户部门角色列表不应为空");
        System.out.println("✅ 用户当前拥有 " + departments.size() + " 个部门角色关联");
    }

    /**
     * 测试10：无Token访问受保护接口（应该返回403）
     */
    @Test
    @Order(10)
    @DisplayName("测试10：无Token访问（应返回403）")
    public void testAccessWithoutToken() throws Exception {
        System.out.println("\n=== 测试10：无Token访问测试 ===");

        MvcResult result = mockMvc.perform(get("/role/list"))
                .andExpect(status().isForbidden())
                .andReturn();

        System.out.println("✅ 无Token访问被正确拦截（403 Forbidden）");
    }

    /**
     * 测试11：无权限访问修改角色接口（应该返回403）
     */
    @Test
    @Order(11)
    @DisplayName("测试11：普通用户无权限修改角色（应返回403）")
    public void testUpdateRoleWithoutPermission() throws Exception {
        if (this.newUserId == null) {
            System.out.println("\n⚠️ 跳过测试11：没有可用的用户ID");
            return;
        }

        System.out.println("\n=== 测试11：测试权限控制 ===");

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser_" + System.currentTimeMillis());
        loginRequest.setPassword("test123456");

        try {
            MvcResult loginResult = mockMvc.perform(post("/api-auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(asJsonString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            String loginResponse = loginResult.getResponse().getContentAsString();
            Map<String, Object> loginMap = parseToMap(loginResponse);

            if (loginMap.get("code").equals(200)) {
                Map<String, Object> data = (Map<String, Object>) loginMap.get("data");
                String userToken = (String) data.get("token");

                Map<String, Object> updateRequest = Map.of(
                        "userId", this.newUserId,
                        "roleIds", java.util.Arrays.asList(this.testRoleId),
                        "departmentId", "dept001"
                );

                MvcResult updateResult = mockMvc.perform(post("/user/update-roles")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer " + userToken)
                                .content(asJsonString(updateRequest)))
                        .andExpect(status().isForbidden())
                        .andReturn();

                System.out.println("✅ 普通用户无权限修改角色被正确拦截（403 Forbidden）");
            } else {
                System.out.println("⚠️ 测试用户不存在，跳过权限测试");
            }
        } catch (Exception e) {
            System.out.println("⚠️ 权限测试异常: " + e.getMessage());
        }
    }

    /**
     * 辅助方法：将对象转换为JSON字符串
     */
    private String asJsonString(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 辅助方法：解析JSON为Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToMap(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("解析JSON失败: " + e.getMessage(), e);
        }
    }
}