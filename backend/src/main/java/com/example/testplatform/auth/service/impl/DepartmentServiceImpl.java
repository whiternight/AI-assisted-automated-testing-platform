
package com.example.testplatform.auth.service.impl;

import com.example.testplatform.auth.domain.po.Department;
import com.example.testplatform.auth.mapper.DepartmentMapper;
import com.example.testplatform.auth.service.IDepartmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 部门表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-06-09
 */
@Service
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department> implements IDepartmentService {

    @Override
    public List<Department> getAllDepartments() {
        // @TableLogic 启用后，MP 自动过滤 deleted=1，无需手动加
        return this.list(
                new LambdaQueryWrapper<Department>()
                        .orderByAsc(Department::getName)
        );
    }
}