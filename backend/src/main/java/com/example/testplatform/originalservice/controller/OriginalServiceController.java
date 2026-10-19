package com.example.testplatform.originalservice.controller;

import com.example.testplatform.common.result.Result;
import com.example.testplatform.originalservice.domain.dto.*;
import com.example.testplatform.originalservice.domain.vo.AvailableCaseVO;
import com.example.testplatform.originalservice.domain.vo.OriginalServiceVO;
import com.example.testplatform.originalservice.service.IOriginalServiceService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 原服务表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-06-17
 */
@Tag(name = "原服务管理", description = "原服务CRUD接口，支持用例的批量添加/删除及参数编辑")
@RestController
@RequestMapping("/original-service")
public class OriginalServiceController {

    @Resource
    private IOriginalServiceService originalServiceService;

    /**
     * 查询可选用例列表（未在原服务中的用例）
     * 用于"添加原服务"弹窗，展示可被选择的用例
     */
    @Operation(summary = "查询可选用例列表", description = "获取未在原服务中的用例列表，支持关键词、应用、协议类型筛选")
    @GetMapping("/available-cases")
    public Result<Page<AvailableCaseVO>> listAvailableCases(@Valid @ModelAttribute AvailableCaseQueryDTO queryDTO) {
        return Result.success(originalServiceService.listAvailableCases(queryDTO));
    }

    /**
     * 批量添加用例到原服务
     * 支持单选和批量选择用例
     */
    @Operation(summary = "批量添加用例到原服务", description = "将选中的用例批量添加到原服务表，已在表中的用例将跳过")
    @PostMapping("/add-cases")
    public Result<Integer> addCases(@RequestBody @Valid OriginalServiceAddDTO addDTO) {
        int count = originalServiceService.addCases(addDTO);
        return Result.success(count);
    }

    /**
     * 手动创建原服务
     */
    @Operation(summary = "手动创建原服务", description = "直接创建原服务，无需关联已有用例，支持配置请求参数、断言等")
    @PostMapping
    public Result<OriginalServiceVO> createOriginalService(@RequestBody @Valid OriginalServiceCreateDTO createDTO) {
        return Result.success(originalServiceService.createOriginalService(createDTO));
    }

    /**
     * 分页查询原服务列表
     */
    @Operation(summary = "分页查询原服务列表", description = "获取原服务列表，关联展示用例信息和应用信息")
    @GetMapping("/list")
    public Result<Page<OriginalServiceVO>> listOriginalServices(@Valid @ModelAttribute OriginalServiceQueryDTO queryDTO) {
        return Result.success(originalServiceService.listOriginalServices(queryDTO));
    }

    /**
     * 查询原服务详情（编辑页使用）
     */
    @Operation(summary = "查询原服务详情", description = "根据ID获取原服务详情，用于编辑页展示")
    @GetMapping("/{id}")
    public Result<OriginalServiceVO> getDetail(@PathVariable String id) {
        return Result.success(originalServiceService.getDetail(id));
    }

    /**
     * 更新原服务（修改请求参数等）
     */
    @Operation(summary = "更新原服务", description = "修改原服务的名称、入参、出参、断言等配置")
    @PutMapping("/{id}")
    public Result<Void> updateOriginalService(@PathVariable String id,
                                               @RequestBody @Valid OriginalServiceUpdateDTO updateDTO) {
        originalServiceService.updateOriginalService(id, updateDTO);
        return Result.success();
    }

    /**
     * 删除原服务（单条）
     */
    @Operation(summary = "删除原服务", description = "逻辑删除指定的原服务记录")
    @DeleteMapping("/{id}")
    public Result<Void> deleteOriginalService(@PathVariable String id) {
        originalServiceService.deleteOriginalService(id);
        return Result.success();
    }

    /**
     * 批量删除原服务
     */
    @Operation(summary = "批量删除原服务", description = "逻辑删除多条原服务记录")
    @DeleteMapping("/batch")
    public Result<Void> batchDeleteOriginalServices(@RequestBody List<String> ids) {
        originalServiceService.batchDeleteOriginalServices(ids);
        return Result.success();
    }
}
