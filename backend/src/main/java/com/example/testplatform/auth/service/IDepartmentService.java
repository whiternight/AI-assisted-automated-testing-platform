
package com.example.testplatform.auth.service;

import com.example.testplatform.auth.domain.po.Department;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 部门表 服务类
 * </p>
 *
 * @author author
 * @since 2026-06-09
 */
public interface IDepartmentService extends IService<Department> {

    List<Department> getAllDepartments();
}