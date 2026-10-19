
package com.example.testplatform.auth.mapper;

import com.example.testplatform.auth.domain.po.Permission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PermissionMapper extends BaseMapper<Permission> {

    List<Permission> selectPermissionsByUserId(@Param("userId") Long userId);
}