package com.example.testplatform.workflow.controller;

import com.example.testplatform.common.result.Result;
import com.example.testplatform.workflow.domain.dto.FlowExecuteRequestDTO;
import com.example.testplatform.workflow.domain.vo.FlowExecuteResponseVO;
import com.example.testplatform.workflow.domain.vo.FlowExecuteResultVO;
import com.example.testplatform.workflow.domain.vo.FlowExecutionHistoryVO;
import com.example.testplatform.workflow.domain.vo.NodeDetailVO;
import com.example.testplatform.workflow.service.IFlowExecutionService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 流程执行实例表 前端控制器
 *
 * @author TestPlatform
 * @since 2026-06-17
 */
@Tag(name = "流程执行管理", description = "流程启动、执行结果查询等接口")
@RestController
@RequestMapping("/api-flow-execution")
public class FlowExecutionController {

    @Resource
    private IFlowExecutionService flowExecutionService;

    @Operation(summary = "启动流程执行", description = "根据流程定义ID启动流程，异步执行，立即返回")
    @PostMapping("/{flowDefinitionId}/execute")
    public Result<FlowExecuteResponseVO> execute(
            @PathVariable String flowDefinitionId,
            @RequestBody @Valid FlowExecuteRequestDTO dto) {
        FlowExecuteResponseVO response = flowExecutionService.executeFlow(flowDefinitionId, dto);
        return Result.success(response);
    }

    @Operation(summary = "查询执行结果", description = "根据 Flowable 流程实例ID查询执行结果")
    @GetMapping("/execution/{processInstanceId}")
    public Result<FlowExecuteResultVO> getExecutionResult(
            @PathVariable String processInstanceId) {
        FlowExecuteResultVO result = flowExecutionService.getExecutionResult(processInstanceId);
        return Result.success(result);
    }

    @Operation(summary = "查询节点执行详情", description = "根据流程实例ID和活动ID查询节点详情，包含解密后的输入/输出快照")
    @GetMapping("/execution/{processInstanceId}/node/{activityId}")
    public Result<NodeDetailVO> getNodeDetail(
            @PathVariable String processInstanceId,
            @PathVariable String activityId) {
        NodeDetailVO detail = flowExecutionService.getNodeDetail(processInstanceId, activityId);
        return Result.success(detail);
    }

    @Operation(summary = "分页查询执行历史", description = "分页查询流程执行历史记录，支持按流程定义ID、状态、流程名称模糊、时间范围组合搜索")
    @GetMapping("/history")
    public Result<IPage<FlowExecutionHistoryVO>> listExecutionHistory(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String flowDefinitionId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String flowName,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        IPage<FlowExecutionHistoryVO> page = flowExecutionService.listExecutionHistory(
                pageNum, pageSize, flowDefinitionId, status, flowName, startTime, endTime);
        return Result.success(page);
    }

    /**
     * 终止流程执行（节点失败时前端调用，立即停止后续节点执行）
     * <p>对应前端错误控制逻辑：任意节点变红时调用此接口终止流程</p>
     */
    @Operation(summary = "终止流程执行", description = "根据流程实例ID终止正在执行的流程，停止后续节点执行")
    @PostMapping("/execution/{processInstanceId}/terminate")
    public Result<Void> terminateExecution(@PathVariable String processInstanceId) {
        flowExecutionService.terminateExecution(processInstanceId);
        return Result.success();
    }

    /**
     * 查询执行详情（含每个节点的开始/结束时间、耗时、状态、日志）
     * <p>供前端"执行记录"弹窗展示</p>
     */
    @Operation(summary = "查询执行详情", description = "根据流程实例ID查询执行详情，包含节点轨迹、开始/结束时间、耗时、日志")
    @GetMapping("/execution/{processInstanceId}/detail")
    public Result<FlowExecuteResultVO> getExecutionDetail(@PathVariable String processInstanceId) {
        FlowExecuteResultVO detail = flowExecutionService.getExecutionResult(processInstanceId);
        return Result.success(detail);
    }

    /**
     * 重试流程执行（从失败节点重新执行）
     */
    @Operation(summary = "重试流程执行", description = "根据流程实例ID重新启动流程执行")
    @PostMapping("/execution/{processInstanceId}/retry")
    public Result<FlowExecuteResponseVO> retryExecution(@PathVariable String processInstanceId) {
        FlowExecuteResponseVO response = flowExecutionService.retryExecution(processInstanceId);
        return Result.success(response);
    }

    /**
     * 清理历史"执行中"脏数据
     * <p>
     * 扫描 flow_execution 表中 status=0 但 Flowable 流程实例实际已结束的记录，
     * 同步更新为正确的终态。用于一次性修复历史遗留的僵尸"执行中"记录。
     * </p>
     */
    @Operation(summary = "清理执行中脏数据", description = "扫描并修复 flow_execution 表中状态为'执行中'但流程实际已结束的历史脏数据")
    @PostMapping("/cleanup-zombie")
    public Result<Integer> cleanupZombieRecords() {
        int fixedCount = flowExecutionService.cleanupZombieExecutingRecords();
        return Result.success(fixedCount);
    }
}
