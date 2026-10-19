package com.example.testplatform.apicase.controller;

import com.example.testplatform.apicase.domain.vo.CaseActivityVO;
import com.example.testplatform.apicase.service.ICaseActivityService;
import com.example.testplatform.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用例活跃度控制器
 *
 * @author TestPlatform
 */
@RestController
@RequestMapping("/case-activity")
@RequiredArgsConstructor
@Tag(name = "用例活跃度", description = "用例活跃度管理与查询接口")
public class CaseActivityController {

    private final ICaseActivityService caseActivityService;

    /**
     * 查询单个用例活跃度详情
     */
    @GetMapping("/{caseId}")
    @Operation(summary = "查询用例活跃度详情", description = "根据用例ID查询活跃度详情，包含各维度最近活跃时间及活跃状态")
    public Result<CaseActivityVO> getActivity(
            @Parameter(description = "用例ID", required = true) @PathVariable String caseId) {
        CaseActivityVO vo = caseActivityService.getActivityByCaseId(caseId);
        if (vo == null) {
            return Result.notFound("该用例暂无活跃度记录");
        }
        return Result.success(vo);
    }

    /**
     * 查询活跃度列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询活跃度列表", description = "支持按应用ID、活跃状态筛选")
    public Result<List<CaseActivityVO>> listActivities(
            @Parameter(description = "应用ID") @RequestParam(required = false) String applicationId,
            @Parameter(description = "是否活跃：true=活跃，false=不活跃，不传=全部") @RequestParam(required = false) Boolean active) {
        List<CaseActivityVO> list = caseActivityService.listActivities(applicationId, active);
        return Result.success(list);
    }

    /**
     * 查询活跃度统计概览
     */
    @GetMapping("/stats")
    @Operation(summary = "活跃度统计概览", description = "返回总用例数、活跃用例数、不活跃用例数、已逻辑删除数")
    public Result<CaseActivityVO> getActivityStats(
            @Parameter(description = "应用ID，不传则统计全局") @RequestParam(required = false) String applicationId) {
        CaseActivityVO stats = caseActivityService.getActivityStats(applicationId);
        return Result.success(stats);
    }

    /**
     * 手动记录查看活跃（前端打开用例详情时调用）
     */
    @PostMapping("/{caseId}/view")
    @Operation(summary = "记录查看活跃", description = "前端打开用例详情页面时调用，更新最近查看时间")
    public Result<Void> recordView(
            @Parameter(description = "用例ID", required = true) @PathVariable String caseId) {
        caseActivityService.recordViewActivity(caseId);
        return Result.success();
    }

    /**
     * 手动触发逻辑删除（管理接口）
     */
    @PostMapping("/cleanup")
    @Operation(summary = "清理不活跃用例", description = "手动触发逻辑删除：将7天内未活跃的用例标记为已删除")
    public Result<Integer> cleanupInactiveCases() {
        int count = caseActivityService.logicalDeleteInactiveCases();
        return Result.success("清理完成", count);
    }

    /**
     * 初始化用例活跃度记录
     */
    @PostMapping("/{caseId}/init")
    @Operation(summary = "初始化活跃度记录", description = "为新建用例初始化活跃度记录，如已存在则跳过")
    public Result<Void> initActivity(
            @Parameter(description = "用例ID", required = true) @PathVariable String caseId) {
        caseActivityService.initActivityIfAbsent(caseId);
        return Result.success();
    }
}
