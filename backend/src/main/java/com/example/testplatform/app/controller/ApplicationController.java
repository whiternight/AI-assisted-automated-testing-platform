package com.example.testplatform.app.controller;

import com.example.testplatform.apicase.domain.vo.ApiCaseVO;
import com.example.testplatform.common.result.Result;
import com.example.testplatform.app.domain.dto.ApplicationCreateDTO;
import com.example.testplatform.app.domain.po.Application;
import com.example.testplatform.app.domain.vo.DepartmentAppTreeVO;

import com.example.testplatform.auth.domain.po.Department;
import com.example.testplatform.auth.service.IDepartmentService;
import com.example.testplatform.apicase.service.IApiCaseService;
import com.example.testplatform.app.service.IApplicationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 应用表 前端控制器
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Tag(name = "应用管理", description = "应用CRUD接口")
@RestController
@RequestMapping("/application")
public class ApplicationController {

    @Resource
    private IApplicationService applicationService;

    @Resource
    private IApiCaseService apiCaseService;

    @Resource
    private IDepartmentService departmentService;

    /**
     * 获取部门列表（直接返回所有部门）
     * GET /application/department/list
     */
    @Operation(summary = "获取部门列表", description = "直接返回所有部门列表")
    @GetMapping("/department/list")
    public Result<List<Department>> getDepartmentList() {
        List<Department> list = departmentService.getAllDepartments();
        return Result.success(list);
    }

    /**
     * 根据部门ID查询应用列表
     * POST /application/listByDepartment
     */
    @Operation(summary = "根据部门ID数组批量查询应用列表")
    @PostMapping("/listByDepartment")
    public Result<List<Application>> listByDepartmentId(@RequestBody List<String> departmentIds) {
        List<Application> list = applicationService.listByDepartmentIds(departmentIds);
        return Result.success(list);
    }

    /**
     * 新增应用
     * POST /application
     */
    @Operation(summary = "新增应用", description = "创建应用并关联到指定部门")
    @PostMapping
    public Result<String> createApplication(@RequestBody @Valid ApplicationCreateDTO dto) {
        String id = applicationService.createApplication(dto);
        return Result.success(id);
    }

    /**
     * 逻辑删除应用
     * DELETE /application/{id}
     */
    @Operation(summary = "逻辑删除应用", description = "将应用标记为已删除，不物理删除数据")
    @DeleteMapping("/{id}")
    public Result<Void> deleteApplication(@PathVariable String id) {
        applicationService.deleteApplication(id);
        return Result.success();
    }

    /**
     * 根据应用ID分页查询用例列表
     * GET /application/cases?applicationId=xxx&pageNum=1&pageSize=20
     */
    @Operation(summary = "根据应用ID分页查询用例列表")
    @GetMapping("/cases")
    public Result<Page<ApiCaseVO>> listCasesByApplicationId(
            @RequestParam String applicationId,
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "20") Long pageSize) {
        Page<ApiCaseVO> page = apiCaseService.listByApplicationId(applicationId, pageNum, pageSize);
        return Result.success(page);
    }

    /**
     * 查询部门-应用树状结构
     * GET /application/department-tree
     */
    @Operation(summary = "查询部门-应用树状结构", description = "返回所有部门及其下的应用列表，用于前端渲染树状菜单")
    @GetMapping("/department-tree")
    public Result<List<DepartmentAppTreeVO>> getDepartmentAppTree() {
        List<DepartmentAppTreeVO> tree = applicationService.listDepartmentAppTree();
        return Result.success(tree);
    }

}
