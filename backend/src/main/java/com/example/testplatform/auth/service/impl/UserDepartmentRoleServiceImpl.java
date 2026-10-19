
package com.example.testplatform.auth.service.impl;

import com.example.testplatform.auth.domain.po.UserDepartmentRole;
import com.example.testplatform.auth.mapper.UserDepartmentRoleMapper;
import com.example.testplatform.auth.service.IUserDepartmentRoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 用户部门角色关联表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-06-09
 */
@Service
public class UserDepartmentRoleServiceImpl extends ServiceImpl<UserDepartmentRoleMapper, UserDepartmentRole> implements IUserDepartmentRoleService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserRoles(Long userId, List<String> roleIds, String departmentId) {
        // @TableLogic 启用后，MP 自动过滤 deleted=1
        LambdaQueryWrapper<UserDepartmentRole> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(UserDepartmentRole::getUserId, userId);

        if (departmentId != null && !departmentId.isEmpty()) {
            deleteWrapper.eq(UserDepartmentRole::getDepartmentId, departmentId);
        }

        List<UserDepartmentRole> existingRelations = list(deleteWrapper);
        if (!existingRelations.isEmpty()) {
            // 走 MyBatis-Plus 逻辑删除：UPDATE ... SET deleted=1 WHERE id IN (...)
            remove(deleteWrapper);
        }

        if (roleIds != null && !roleIds.isEmpty()) {
            for (String roleId : roleIds) {
                UserDepartmentRole newRelation = new UserDepartmentRole();
                newRelation.setUserId(userId);
                newRelation.setDepartmentId(departmentId != null ? departmentId : "default");
                newRelation.setRoleId(roleId);
                newRelation.setIsDefault(0);
                newRelation.setCreatedAt(LocalDateTime.now());
                newRelation.setUpdatedAt(LocalDateTime.now());
                newRelation.setDeleted(0);
                save(newRelation);
            }
        }
    }

    @Override
    public List<UserDepartmentRole> getUserRoles(Long userId) {
        // @TableLogic 启用后，MP 自动过滤 deleted=1，无需手动加
        return list(
                new LambdaQueryWrapper<UserDepartmentRole>()
                        .eq(UserDepartmentRole::getUserId, userId)
        );
    }

    @Override
    public String getDeptIdByUserId(Long id) {
        // @TableLogic 启用后，MP 自动过滤 deleted=1
        QueryWrapper<UserDepartmentRole> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("department_id")
                .eq("user_id", id)
                .eq("is_default", 1)
                .orderByDesc("updated_at")
                .last("LIMIT 1");
        UserDepartmentRole relation = getOne(queryWrapper);
        return relation != null ? relation.getDepartmentId() : null;
    }
}