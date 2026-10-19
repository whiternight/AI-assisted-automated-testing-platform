
package com.example.testplatform.auth.controller;

import com.example.testplatform.common.result.*;
import com.example.testplatform.auth.domain.dto.*;
import com.example.testplatform.auth.domain.po.User;
import com.example.testplatform.auth.domain.po.UserDepartmentRole;
import com.example.testplatform.auth.security.JwtUtil;
import com.example.testplatform.auth.service.IDepartmentService;
import com.example.testplatform.auth.service.IRoleService;
import com.example.testplatform.auth.service.IUserDepartmentRoleService;
import com.example.testplatform.auth.service.IUserService;
import com.example.testplatform.auth.service.WorkflowService;
import com.example.testplatform.auth.util.ValidationUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证控制器 - 处理登录、注册、登出请求
 *
 * 关键点：
 * 1. 登录：验证用户名密码，生成JWT Token
 * 2. 注册：创建用户，启动Flowable审批流程
 * 3. 登出：前端清除Token（后端无状态）
 * 4. 登录失败处理：记录失败次数，达到阈值锁定账户
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录、注册、登出接口")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final IUserService userService;
    private final IRoleService roleService;
    private final IDepartmentService departmentService;
    private final IUserDepartmentRoleService userDepartmentRoleService;
    private final PasswordEncoder passwordEncoder;
    private final WorkflowService workflowService;

    @GetMapping("/getUser")
    public Result<User> getUser() {
        // 从 SecurityContext 获取当前登录用户，避免返回固定用户（id=1）
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return Result.error(401, "用户未登录");
        }

        String username = authentication.getName();
        User user = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        if (user == null) {
            return Result.error(404, "当前登录用户不存在");
        }

        return Result.success(user);
    }

    /**
     * 用户登录接口
     *
     * 关键点：
     * 1. 使用AuthenticationManager验证用户名密码
     * 2. 检查用户状态（待激活、已锁定等）
     * 3. 更新最后登录时间，重置失败次数
     * 4. 生成JWT Token返回给前端
     *
     * 流程：
     * 1. 接收登录请求（用户名+密码）
     * 2. 调用AuthenticationManager认证
     * 3. 认证成功后查询用户详细信息
     * 4. 检查用户状态是否正常
     * 5. 更新登录信息
     * 6. 生成JWT Token
     * 7. 返回Token和用户信息
     *
     * @param request 登录请求（用户名+密码）
     * @return JWT Token和用户信息
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码登录")
    public Result<AuthResponse> login(@RequestBody LoginRequest request) {
        try {
            // ========== 步骤1：认证用户名密码 ==========
            // 关键点：AuthenticationManager会自动调用UserDetailsService加载用户
            // 然后比对密码（BCrypt加密比对）
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
            // ========== 步骤2：查询用户详细信息 ==========
            // @TableLogic 启用后，MP 自动过滤 deleted=1，无需手动加 .eq(User::getDeleted, 0)
            User user = userService.getOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, request.getUsername()));

            if (user == null) {
                return Result.error("用户不存在");
            }

            // ========== 步骤3：检查用户状态 ==========
            // 关键点：允许 status=0（待激活）和 status=1（已激活）登录
            // status=2（已锁定/已拒绝）不允许登录
            String statusMessage = "";
            if (user.getStatus() == 0) {
                // status=0：待激活（刚注册，未完成审批），仅只读权限
                statusMessage = "（账户待激活，仅只读权限）";
            } else if (user.getStatus() == 2) {
                // status=2：已锁定（被拒绝或多次登录失败）
                return Result.error(403, "账户已被拒绝或锁定");
            }
            // status=1：已激活，正常登录

            // ========== 步骤4：更新登录信息 ==========
            user.setLastLoginTime(LocalDateTime.now());  // 更新最后登录时间
            user.setLoginFailCount(0);                   // 重置失败次数
            userService.updateById(user);

            // ========== 步骤5：生成JWT Token ==========
            String roles = getUserRoles(user.getId());   // 获取用户角色

            // 关键点：如果是待激活用户，添加 READ_ONLY 标记到角色中
            if (user.getStatus() == 0) {
                roles = roles + ",READ_ONLY";
            }

            String token = jwtUtil.generateToken(
                    user.getUsername(),  // 用户名
                    user.getId(),        // 用户ID
                    roles                // 角色列表（可能包含 READ_ONLY）
            );


            // ========== 步骤6：返回Token ==========
            return Result.success(new AuthResponse(
                    token,
                    user.getUsername(),
                    user.getId(),
                    user.getStatus(),
                    "登录成功" + statusMessage,
                    userDepartmentRoleService.getDeptIdByUserId(user.getId())
            ));

        } catch (Exception e) {
            // 认证失败（用户名或密码错误）
            handleLoginFailure(request.getUsername());
            return Result.error(401, "用户名或密码错误");
        }
    }

    /**
     * 用户注册接口
     *
     * 关键点：
     * 1. 验证两次密码是否一致
     * 2. 检查用户名是否已存在
     * 3. 密码BCrypt加密后存储
     * 4. 用户状态设为0（待激活）
     * 5. 创建用户-部门-角色关联
     * 6. 启动Flowable审批流程
     *
     * 流程：
     * 1. 接收注册请求（用户名+密码+确认密码+真实姓名+部门+角色）
     * 2. 验证密码一致性
     * 3. 检查用户名唯一性
     * 4. 创建用户记录（status=0待激活）
     * 5. 创建用户-部门-角色关联
     * 6. 启动Flowable工作流（通知管理员审批）
     * 7. 返回注册成功提示
     *
     * @param request 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "新用户注册，需要管理员激活")
    public Result<AuthResponse> register(@RequestBody RegisterRequest request) {
        // ========== 步骤1：验证用户名格式 ==========
        String usernameError = ValidationUtil.validateUsername(request.getUsername());
        if (usernameError != null) {
            return Result.error(usernameError);
        }
        // ========== 步骤2：验证密码安全度 ==========
        String passwordError = ValidationUtil.validatePassword(request.getPassword(), request.getUsername());
        if (passwordError != null) {
            return Result.error(passwordError);
        }

        // ========== 步骤3：验证密码一致性 ==========
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            return Result.error("两次密码输入不一致");
        }


        // ========== 步骤4：检查用户名唯一性 ==========
        // @TableLogic 启用后，MP 自动过滤 deleted=1
        User existUser = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        if (existUser != null) {
            return Result.error("用户名已存在");
        }

        // ========== 步骤5：创建用户 ==========
        User user = new User();
        user.setUsername(request.getUsername());
        // 密码必须BCrypt加密后存储
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        // 新用户状态为0（待激活），只能登录不能使用功能
        user.setStatus(0);
        user.setLoginFailCount(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleted(0);

        userService.save(user);

        // ========== 步骤6：创建用户-部门-角色关联 ==========
        // 建立用户与部门、角色的关联关系
        UserDepartmentRole udr = new UserDepartmentRole();
        udr.setUserId(user.getId());
        udr.setDepartmentId(request.getDepartmentId());
        udr.setRoleId(request.getRoleId());
        udr.setIsDefault(1);  // 设为默认关联
        udr.setCreatedAt(LocalDateTime.now());
        udr.setUpdatedAt(LocalDateTime.now());
        udr.setDeleted(0);

        userDepartmentRoleService.save(udr);

        // ========== 步骤7：启动Flowable审批流程 ==========
        // 启动工作流后，管理员会收到审批任务
        try {
            workflowService.startUserActivationProcess(
                    user.getId(),
                    user.getUsername()
            );
        } catch (Exception e) {
            return Result.error("注册成功，但启动激活流程失败: " + e.getMessage());
        }

        // ========== 步骤8：返回注册成功 ==========
        return Result.success(new AuthResponse(
                null,  // 注册成功后不返回Token，需要等待审批
                user.getUsername(),
                user.getId(),
                user.getStatus(),
                "注册成功，等待管理员激活"
        ));
    }

    /**
     * 用户登出接口
     *
     * 关键点：
     * - JWT是无状态的，后端不需要清除Token
     * - 前端清除localStorage中的Token即可
     * - Token会在自然过期后失效
     *
     * @return 登出成功提示
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "清除token（前端实现）")
    public Result<String> logout() {
        return Result.success("登出成功，请清除本地token");
    }

    /**
     * 修改密码接口
     *
     * 关键点：
     * 1. 从SecurityContext获取当前登录用户
     * 2. 验证原密码是否正确
     * 3. 检查新密码不能与原密码相同
     * 4. 对新密码进行BCrypt加密后存储
     * 5. 更新用户的密码和更新时间
     *
     * 流程：
     * 1. 接收修改密码请求（原密码+新密码）
     * 2. 从JWT Token中获取当前用户ID
     * 3. 验证原密码
     * 4. 验证新密码合法性
     * 5. 加密并保存新密码
     * 6. 返回成功提示
     *
     * @param request 修改密码请求
     * @return 操作结果
     */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "修改密码", description = "登录用户修改自己的密码")
    public Result<String> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            // ========== 步骤1：从SecurityContext获取当前用户 ==========
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            User user = userService.getOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, username));

            if (user == null) {
                return Result.error(404, "用户不存在");
            }

            // ========== 步骤2：调用服务层修改密码 ==========
            boolean success = userService.changePassword(
                    user.getId(),
                    request.getOldPassword(),
                    request.getNewPassword()
            );

            if (success) {
                return Result.success("密码修改成功");
            } else {
                return Result.error("密码修改失败");
            }

        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("修改密码失败: " + e.getMessage());
        }
    }
    /**
     * 获取用户角色列表
     *
     * 关键点：
     * - 从user_department_role表查询用户的角色
     * - 多个角色用逗号分隔
     * - 用于生成JWT Token时存入claims
     *
     * @param userId 用户ID
     * @return 角色ID字符串（逗号分隔）
     */
    private String getUserRoles(Long userId) {
        List<UserDepartmentRole> udrList = userDepartmentRoleService.list(
                new LambdaQueryWrapper<UserDepartmentRole>()
                        .eq(UserDepartmentRole::getUserId, userId)
        );

        StringBuilder roles = new StringBuilder();
        for (int i = 0; i < udrList.size(); i++) {
            if (i > 0) roles.append(",");
            roles.append(udrList.get(i).getRoleId());
        }
        return roles.toString();
    }

    /**
     * 处理登录失败
     *
     * 关键点：
     * 1. 记录登录失败次数
     * 2. 失败5次后锁定账户30分钟
     * 3. 同时设置status=2（已锁定）
     *
     * 安全策略：
     * - 防止暴力破解
     * - 临时锁定而非永久封禁
     * - 锁定期间无法登录
     *
     * @param username 用户名
     */
    private void handleLoginFailure(String username) {
        User user = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        if (user != null) {
            Integer failCount = user.getLoginFailCount() == null ? 0 : user.getLoginFailCount();
            user.setLoginFailCount(failCount + 1);

            // 关键点：连续失败5次后锁定账户
            if (failCount + 1 >= 5) {
                // 锁定30分钟
                user.setLockUntil(LocalDateTime.now().plusMinutes(30));
                user.setStatus(2);  // 设置为锁定状态
            }

            user.setUpdatedAt(LocalDateTime.now());
            userService.updateById(user);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }
}