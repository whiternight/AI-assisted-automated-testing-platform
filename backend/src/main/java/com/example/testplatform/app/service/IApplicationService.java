package com.example.testplatform.app.service;

import com.example.testplatform.app.domain.dto.ApplicationCreateDTO;
import com.example.testplatform.app.domain.po.Application;
import com.example.testplatform.app.domain.vo.DepartmentAppTreeVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 应用表 服务类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
public interface IApplicationService extends IService<Application> {

    /**
     * 根据部门ID数组批量查询应用列表
     *
     * @param departmentIds 部门ID
     * @return 应用列表
     */
    List<Application> listByDepartmentIds(List<String> departmentIds);

    /**
     * 新增应用
     * <p>
     * 创建应用并关联到指定部门，自动填充创建时间和逻辑删除标志
     *
     * @param dto 应用创建参数
     * @return 新创建的应用ID
     */
    String createApplication(ApplicationCreateDTO dto);

    /**
     * 逻辑删除应用
     * <p>
     * 将应用的 deleted 字段置为 1，不会物理删除数据
     *
     * @param id 应用ID
     */
    void deleteApplication(String id);

    /**
     * 查询部门-应用树状结构
     * <p>
     * 返回所有部门及其下的应用列表，用于前端渲染树状菜单
     *
     * @return 部门-应用树状列表
     */
    List<DepartmentAppTreeVO> listDepartmentAppTree();

}
