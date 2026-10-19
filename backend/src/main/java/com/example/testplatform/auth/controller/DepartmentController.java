package com.example.testplatform.auth.controller;


import com.example.testplatform.common.result.Result;
import com.example.testplatform.auth.domain.po.Department;
import com.example.testplatform.auth.domain.po.UserDepartmentRole;
import com.example.testplatform.auth.domain.po.User;
import com.example.testplatform.auth.service.IDepartmentService;
import com.example.testplatform.auth.service.IUserDepartmentRoleService;
import com.example.testplatform.auth.service.IUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 部门表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-06-09
 */
@RestController
@RequestMapping("/department")
@Tag(name = "部门管理", description = "部门相关接口")
public class DepartmentController {

    @Autowired
    private IDepartmentService departmentService;

    @Autowired
    private IUserService userService;

    @Autowired
    private IUserDepartmentRoleService userDepartmentRoleService;

    @GetMapping("/list")
    @Operation(summary = "获取部门列表", description = "管理员返回所有部门，普通用户仅返回所属部门")
    public Result<List<Department>> getDepartmentList() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                List<Department> allDepartments = departmentService.getAllDepartments();
                return Result.success(allDepartments);
            }

            String username = auth.getName();
            // @TableLogic 启用后，MP 自动过滤 deleted=1
            User user = userService.getOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, username));

            if (user == null) {
                return Result.error("用户不存在");
            }

            boolean isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (isAdmin) {
                return Result.success(departmentService.getAllDepartments());
            }

            List<UserDepartmentRole> udrList = userDepartmentRoleService.list(
                    new LambdaQueryWrapper<UserDepartmentRole>()
                            .eq(UserDepartmentRole::getUserId, user.getId()));

            if (udrList.isEmpty()) {
                return Result.success(Collections.emptyList());
            }

            List<String> deptIds = udrList.stream()
                    .map(UserDepartmentRole::getDepartmentId)
                    .distinct()
                    .collect(Collectors.toList());

            List<Department> userDepartments = departmentService.list(
                    new LambdaQueryWrapper<Department>()
                            .in(Department::getId, deptIds));

            return Result.success(userDepartments);
        } catch (Exception e) {
            return Result.error("获取部门列表失败: " + e.getMessage());
        }
    }
}