
package com.example.testplatform.auth.security;

import com.example.testplatform.auth.domain.po.UserDepartmentRole;
import com.example.testplatform.auth.mapper.PermissionMapper;
import com.example.testplatform.auth.mapper.UserDepartmentRoleMapper;
import com.example.testplatform.auth.mapper.UserMapper;
import com.example.testplatform.auth.domain.po.Permission;
import com.example.testplatform.auth.domain.po.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义用户详情服务 - 从数据库加载用户信息和权限
 *
 * 关键点：
 * 1. 实现UserDetailsService接口（Spring Security要求）
 * 2. 根据用户名查询用户
 * 3. 加载用户的所有权限
 * 4. 检查用户状态（激活、锁定等）
 * 5. 构建UserDetails对象返回
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;
    private final PermissionMapper permissionMapper;
    private final UserDepartmentRoleMapper userDepartmentRoleMapper;

    /**
     * 根据用户名加载用户详情
     *
     * 这是Spring Security的核心方法，在以下场景调用：
     * 1. 用户登录时验证用户名密码
     * 2. JWT过滤器中从Token获取用户名后加载用户信息
     *
     * 关键点：
     * - 查询用户基本信息
     * - 检查用户是否被删除
     * - 加载用户权限列表
     * - 检查用户状态（激活、锁定）
     * - 构建UserDetails对象
     *
     * @param username 用户名
     * @return UserDetails对象（包含用户名、密码、权限等）
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // ========== 步骤1：查询用户 ==========
        // @TableLogic 启用后，MP 自动过滤 deleted=1
       User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // ========== 步骤2：加载用户权限 ==========
       List<GrantedAuthority> authorities;
        if (user.getStatus() == 0) {
            authorities = getReadOnlyAuthorities();
        } else {
            authorities = getUserAuthorities(user.getId());
        }
        // ========== 步骤3：构建UserDetails对象 ==========
        // 关键点：UserDetails构造函数参数说明
        // - username: 用户名
        // - password: 加密后的密码
        // - accountNonExpired: 账户是否未过期
        // - accountNonLocked: 账户是否未锁定
        // - credentialsNonExpired: 凭证是否未过期
        // - authorities: 权限列表
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getStatus() != 2,  // status!=2 才算账户未锁定
                true,                    // 账户未过期
                true,                    // 凭证未过期
                !isLocked(user),         // 账户未锁定
                authorities              // 权限列表              // 权限列表
        );
    }
    /**
     * 获取只读权限列表
     *
     * 关键点：
     * - 只返回 action='view' 的权限
     * - 待激活用户只能查看，不能创建、更新、删除、执行
     */
    private List<GrantedAuthority> getReadOnlyAuthorities() {
        // 使用 QueryWrapper 查询所有 action='view' 的权限
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("view:*"));
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        return authorities;
    }
    /**
     * 获取用户的所有权限
     *
     * 关键点：
     * 1. 通过用户ID关联查询权限
     * 2. 权限格式：action:resource（如view:/admin）
     * 3. 转换为Spring Security的GrantedAuthority格式
     *
     * 数据库关联关系：
     * User → UserDepartmentRole → Role → RolePermission → Permission
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    private List<GrantedAuthority> getUserAuthorities(Long userId) {
        // 查询用户的所有权限（通过多表关联）
        List<GrantedAuthority> authorities = new ArrayList<>();
        List<Permission> permissions = permissionMapper.selectPermissionsByUserId(userId);
        for (Permission permission : permissions) {
            String authority = permission.getAction() + ":" + permission.getResource();
            authorities.add(new SimpleGrantedAuthority(authority));
        }
        List<UserDepartmentRole> udrList = userDepartmentRoleMapper.selectList(
                new LambdaQueryWrapper<UserDepartmentRole>()
                        .eq(UserDepartmentRole::getUserId, userId));
        boolean isAdmin = udrList.stream()
                .anyMatch(udr -> "ROLE_ADMIN".equals(udr.getRoleId()));
        if (isAdmin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return authorities;
    }

    /**
     * 检查用户是否被锁定
     *
     * 关键点：
     * - 检查lockUntil字段
     * - 如果当前时间在锁定截止时间之前，则视为锁定
     *
     * @param user 用户对象
     * @return true=已锁定，false=未锁定
     */
    private boolean isLocked(User user) {
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
            return true;
        }
        return false;
    }
}