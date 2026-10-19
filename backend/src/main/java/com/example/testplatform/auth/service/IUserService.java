package com.example.testplatform.auth.service;

import com.example.testplatform.auth.domain.po.User;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author author
 * @since 2026-06-09
 */
public interface IUserService extends IService<User> {
    IPage<User> getUserPage(Page<User> page, String departmentId, String roleId, Integer status);
    boolean changePassword(Long userId, String oldPassword, String newPassword);
}
