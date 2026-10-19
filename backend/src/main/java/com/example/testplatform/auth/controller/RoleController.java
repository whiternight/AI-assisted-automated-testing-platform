
package com.example.testplatform.auth.controller;


import com.example.testplatform.common.result.Result;
import com.example.testplatform.auth.domain.po.Role;
import com.example.testplatform.auth.service.IRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/role")
public class RoleController {

    @Autowired
    private IRoleService roleService;

    @GetMapping("/list")
    public Result<List<Role>> getAllRoles() {
        try {
            List<Role> roles = roleService.getAllRoles();
            return Result.success(roles);
        } catch (Exception e) {
            return Result.error("获取角色列表失败: " + e.getMessage());
        }
    }
}