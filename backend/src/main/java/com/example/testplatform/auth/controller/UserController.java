
package com.example.testplatform.auth.controller;


import com.example.testplatform.common.result.Result;
import com.example.testplatform.auth.domain.po.Department;
import com.example.testplatform.auth.domain.po.Role;
import com.example.testplatform.auth.domain.po.User;
import com.example.testplatform.auth.domain.po.UserDepartmentRole;
import com.example.testplatform.auth.service.IDepartmentService;
import com.example.testplatform.auth.service.IRoleService;
import com.example.testplatform.auth.service.IUserDepartmentRoleService;
import com.example.testplatform.auth.service.IUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private IUserService userService;

    @Autowired
    private IDepartmentService departmentService;

    @Autowired
    private IRoleService roleService;

    @Autowired
    private IUserDepartmentRoleService userDepartmentRoleService;

    @PostMapping("/list")
    public Result<Map<String, Object>> getUserList(@RequestBody UserListRequest request) {
        try {
            int pageNum = request.getPageNum() != null && request.getPageNum() > 0 ? request.getPageNum() : 1;
            int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 10;

            Page<User> page = new Page<>(pageNum, pageSize);

            IPage<User> userPage = userService.getUserPage(page, request.getDepartmentId(), request.getRoleId(), request.getStatus());

            List<Map<String, Object>> userList = userPage.getRecords().stream().map(user -> {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("username", user.getUsername());
                userMap.put("realName", user.getRealName());
                userMap.put("status", user.getStatus());
                userMap.put("lastLoginTime", user.getLastLoginTime());
                userMap.put("createdAt", user.getCreatedAt());
                userMap.put("updatedAt", user.getUpdatedAt());

                List<UserDepartmentRole> udrList = getUserDepartmentRoles(user.getId());
                List<Map<String, Object>> deptRoleList = udrList.stream().map(udr -> {
                    Map<String, Object> deptRoleMap = new HashMap<>();
                    deptRoleMap.put("departmentId", udr.getDepartmentId());
                    deptRoleMap.put("roleId", udr.getRoleId());
                    deptRoleMap.put("isDefault", udr.getIsDefault());

                    Department dept = departmentService.getById(udr.getDepartmentId());
                    if (dept != null) {
                        deptRoleMap.put("departmentName", dept.getName());
                    }

                    Role role = roleService.getRoleById(udr.getRoleId());
                    if (role != null) {
                        deptRoleMap.put("roleName", role.getName());
                    }

                    return deptRoleMap;
                }).collect(Collectors.toList());

                userMap.put("departments", deptRoleList);

                return userMap;
            }).collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("records", userList);
            result.put("total", userPage.getTotal());
            result.put("pageNum", userPage.getCurrent());
            result.put("pageSize", userPage.getSize());
            result.put("pages", userPage.getPages());

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取用户列表失败: " + e.getMessage());
        }
    }
    //做用户详情
    @PostMapping("/detail")
    public Result<Map<String, Object>> getUserDetail(@RequestBody UserDetailRequest request) {
        try {
            if (request.getUserId() == null) {
                return Result.error(400, "用户ID不能为空");
            }

            LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
            // @TableLogic 启用后，MP 自动过滤 deleted=1
            queryWrapper.eq(User::getId, request.getUserId());
            User user = userService.getOne(queryWrapper);

            if (user == null) {
                return Result.error(404, "用户不存在或已被删除");
            }

            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId());
            userMap.put("username", user.getUsername());
            userMap.put("realName", user.getRealName());
            userMap.put("status", user.getStatus());
            userMap.put("lastLoginTime", user.getLastLoginTime());
            userMap.put("loginFailCount", user.getLoginFailCount());
            userMap.put("lockUntil", user.getLockUntil());
            userMap.put("createdAt", user.getCreatedAt());
            userMap.put("updatedAt", user.getUpdatedAt());

            List<UserDepartmentRole> udrList = getUserDepartmentRoles(user.getId());
            List<Map<String, Object>> deptRoleList = udrList.stream().map(udr -> {
                Map<String, Object> deptRoleMap = new HashMap<>();
                deptRoleMap.put("userDepartmentRoleId", udr.getId());
                deptRoleMap.put("departmentId", udr.getDepartmentId());
                deptRoleMap.put("roleId", udr.getRoleId());
                deptRoleMap.put("isDefault", udr.getIsDefault());

                Department dept = departmentService.getById(udr.getDepartmentId());
                if (dept != null) {
                    deptRoleMap.put("departmentName", dept.getName());
                }

                Role role = roleService.getRoleById(udr.getRoleId());
                if (role != null) {
                    deptRoleMap.put("roleName", role.getName());
                }

                return deptRoleMap;
            }).collect(Collectors.toList());

            userMap.put("departments", deptRoleList);

            return Result.success(userMap);
        } catch (Exception e) {
            return Result.error("获取用户详情失败: " + e.getMessage());
        }
    }
    @PostMapping("/update-roles")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateUserRoles(@RequestBody UpdateUserRolesRequest request) {
        try {
            if (request.getUserId() == null) {
                return Result.error(400, "用户ID不能为空");
            }

            if (request.getRoleIds() == null || request.getRoleIds().isEmpty()) {
                return Result.error(400, "角色列表不能为空");
            }

            userDepartmentRoleService.updateUserRoles(
                    request.getUserId(),
                    request.getRoleIds(),
                    request.getDepartmentId()
            );

            return Result.success("用户角色更新成功");
        } catch (Exception e) {
            return Result.error("更新用户角色失败: " + e.getMessage());
        }
    }

    private List<UserDepartmentRole> getUserDepartmentRoles(Long userId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserDepartmentRole> queryWrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        // @TableLogic 启用后，MP 自动过滤 deleted=1
        queryWrapper.eq(UserDepartmentRole::getUserId, userId);
        return userDepartmentRoleService.list(queryWrapper);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserListRequest {
        private Integer pageNum;
        private Integer pageSize;
        private String departmentId;
        private String roleId;
        private Integer status;
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserDetailRequest {
        private Long userId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UpdateUserRolesRequest {
        private Long userId;
        private List<String> roleIds;
        private String departmentId;
    }
}