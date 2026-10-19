package com.example.testplatform.auth.service.impl;

import com.example.testplatform.auth.domain.po.RolePermission;
import com.example.testplatform.auth.mapper.RolePermissionMapper;
import com.example.testplatform.auth.service.IRolePermissionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 角色权限关联表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-06-09
 */
@Service
public class RolePermissionServiceImpl extends ServiceImpl<RolePermissionMapper, RolePermission> implements IRolePermissionService {

}
