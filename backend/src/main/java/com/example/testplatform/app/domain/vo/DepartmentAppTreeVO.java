package com.example.testplatform.app.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 部门-应用 树状结构 VO
 * <p>
 * 每个节点代表一个部门，包含其下的应用列表，
 * 便于前端渲染为树状菜单
 *
 * @author TestPlatform
 */
@Data
@Builder
public class DepartmentAppTreeVO {

    /** 部门ID */
    private String departmentId;

    /** 部门名称 */
    private String departmentName;

    /** 该部门下的应用列表 */
    private List<AppItem> applications;

    /**
     * 应用简要信息（树节点子项）
     */
    @Data
    @Builder
    public static class AppItem {

        /** 应用ID */
        private String id;

        /** 应用名称 */
        private String name;

        /** 应用描述 */
        private String description;

        /** 业务域ID */
        private String businessDomainId;

        /** 负责人ID */
        private Long ownerId;
    }
}
