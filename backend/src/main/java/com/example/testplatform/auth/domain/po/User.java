package com.example.testplatform.auth.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户表
 * </p>
 *
 * @author author
 * @since 2026-06-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user")
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    @TableField("username")
    private String username;

    /**
     * BCrypt 加密
     */
    @TableField("password")
    private String password;

    /**
     * 真实姓名
     */
    @TableField("real_name")
    private String realName;

    /**
     * 0=待激活，1=已激活，2=锁定
     */
    @TableField("status")
    private Integer status;

    /**
     * 最近一次登录
     */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 连续失败次数
     */
    @TableField("login_fail_count")
    private Integer loginFailCount;

    /**
     * 锁定截止时间
     */
    @TableField("lock_until")
    private LocalDateTime lockUntil;

    /**
     * 创建时间，毫秒精度
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间，毫秒精度
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除：0=正常，1=已删除
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;


}
