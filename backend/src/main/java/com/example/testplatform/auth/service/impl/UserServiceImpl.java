
package com.example.testplatform.auth.service.impl;

import com.example.testplatform.auth.domain.po.User;
import com.example.testplatform.auth.domain.po.UserDepartmentRole;
import com.example.testplatform.auth.mapper.UserMapper;
import com.example.testplatform.auth.service.IUserDepartmentRoleService;
import com.example.testplatform.auth.service.IUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private IUserDepartmentRoleService userDepartmentRoleService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Override
    public IPage<User> getUserPage(Page<User> page, String departmentId, String roleId, Integer status) {
        // @TableLogic 启用后，MP 自动过滤 deleted=1
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();

        if (status != null) {
            queryWrapper.eq(User::getStatus, status);
        }

        if (StringUtils.hasText(departmentId) || StringUtils.hasText(roleId)) {
            LambdaQueryWrapper<UserDepartmentRole> udrQuery = new LambdaQueryWrapper<>();

            if (StringUtils.hasText(departmentId)) {
                udrQuery.eq(UserDepartmentRole::getDepartmentId, departmentId);
            }
            if (StringUtils.hasText(roleId)) {
                udrQuery.eq(UserDepartmentRole::getRoleId, roleId);
            }

            List<UserDepartmentRole> udrList = userDepartmentRoleService.list(udrQuery);
            List<Long> userIds = udrList.stream()
                    .map(UserDepartmentRole::getUserId)
                    .distinct()
                    .collect(Collectors.toList());

            if (userIds.isEmpty()) {
                userIds.add(-1L);
            }

            queryWrapper.in(User::getId, userIds);
        }

        queryWrapper.orderByDesc(User::getCreatedAt);

        return this.page(page, queryWrapper);
    }
    @Override
    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        // @TableLogic 启用后，getById 自动过滤 deleted=1
        User user = this.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }

        if (oldPassword.equals(newPassword)) {
            throw new RuntimeException("新密码不能与原密码相同");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());

        return this.updateById(user);
    }
}