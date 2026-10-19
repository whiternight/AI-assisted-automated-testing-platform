
package com.example.testplatform.auth.service;

import com.example.testplatform.auth.domain.po.Role;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IRoleService extends IService<Role> {

    List<Role> getAllRoles();

    Role getRoleById(String id);
}