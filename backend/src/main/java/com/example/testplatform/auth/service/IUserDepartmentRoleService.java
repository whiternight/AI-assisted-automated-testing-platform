package com.example.testplatform.auth.service;

import com.example.testplatform.auth.domain.po.UserDepartmentRole;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 用户部门角色关联表 服务类
 * </p>
 *
 * @author author
 * @since 2026-06-09
 */
public interface IUserDepartmentRoleService extends IService<UserDepartmentRole> {
    void updateUserRoles(Long userId, List<String> roleIds, String departmentId);

    List<UserDepartmentRole> getUserRoles(Long userId);

    String getDeptIdByUserId(Long id);
}
