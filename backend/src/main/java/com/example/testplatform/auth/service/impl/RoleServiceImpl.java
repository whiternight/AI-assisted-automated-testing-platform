
package com.example.testplatform.auth.service.impl;

import com.example.testplatform.auth.domain.po.Role;
import com.example.testplatform.auth.mapper.RoleMapper;
import com.example.testplatform.auth.service.IRoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements IRoleService {

    @Override
    public List<Role> getAllRoles() {
        // @TableLogic 启用后，MP 自动过滤 deleted=1，无需手动加
        return this.list(
                new LambdaQueryWrapper<Role>()
                        .orderByDesc(Role::getCreatedAt)
        );
    }

    @Override
    public Role getRoleById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        // @TableLogic 启用后，getById 自动过滤 deleted=1
        return this.getById(id);
    }
}