package com.example.testplatform.auth.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户部门角色关联表
 * </p>
 *
 * @author author
 * @since 2026-06-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_department_role")
public class UserDepartmentRole implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，UUID
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * FK → user(id)
     */
    private Long userId;

    /**
     * FK → department(id)
     */
    private String departmentId;

    /**
     * FK → role(id)
     */
    private String roleId;

    /**
     * 0=否，1=是默认组合
     */
    private Integer isDefault;

    /**
     * 创建时间，毫秒精度
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，毫秒精度
     */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除：0=正常，1=已删除
     */
    @TableLogic
    private Integer deleted;


}
