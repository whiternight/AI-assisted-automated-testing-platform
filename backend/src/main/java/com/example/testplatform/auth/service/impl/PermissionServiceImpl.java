package com.example.testplatform.auth.service.impl;

import com.example.testplatform.auth.domain.po.Permission;
import com.example.testplatform.auth.mapper.PermissionMapper;
import com.example.testplatform.auth.service.IPermissionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 权限表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-06-09
 */
@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements IPermissionService {

}
