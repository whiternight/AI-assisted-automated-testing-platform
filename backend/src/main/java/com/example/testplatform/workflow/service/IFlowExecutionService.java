package com.example.testplatform.workflow.service;

import com.example.testplatform.workflow.domain.dto.FlowExecuteRequestDTO;
import com.example.testplatform.workflow.domain.po.FlowExecution;
import com.example.testplatform.workflow.domain.vo.FlowExecuteResponseVO;
import com.example.testplatform.workflow.domain.vo.FlowExecuteResultVO;
import com.example.testplatform.workflow.domain.vo.FlowExecutionHistoryVO;
import com.example.testplatform.workflow.domain.vo.NodeDetailVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 流程执行实例表 服务类
 *
 * @author TestPlatform
 * @since 2026-06-17
 */
public interface IFlowExecutionService extends IService<FlowExecution> {

    /**
     * 启动流程执行
     *
     * @param flowDefinitionId 流程定义ID（flow_definition 表 ID）
     * @param dto              执行请求参数
     * @return 执行启动响应
     */
    FlowExecuteResponseVO executeFlow(String flowDefinitionId, FlowExecuteRequestDTO dto);

    /**
     * 查询流程执行结果
     *
     * @param processInstanceId Flowable 流程实例 ID
     * @return 执行结果
     */
    FlowExecuteResultVO getExecutionResult(String processInstanceId);

    /**
     * 查询单个节点执行详情
     *
     * @param processInstanceId Flowable 流程实例 ID
     * @param activityId        活动 ID（BPMN 元素 ID）
     * @return 节点详情（含解密快照）
     */
    NodeDetailVO getNodeDetail(String processInstanceId, String activityId);

    /**
     * 分页查询执行历史记录
     * <p>支持多条件组合搜索：流程名称模糊匹配、执行状态、时间范围。</p>
     *
     * @param pageNum          页码（从 1 开始）
     * @param pageSize         每页条数
     * @param flowDefinitionId 流程定义 ID（可空）
     * @param status           执行状态（可空）
     * @param flowName         流程名称模糊匹配关键词（可空）
     * @param startTime        开始时间下界（可空，含）
     * @param endTime          开始时间上界（可空，含）
     * @return 分页结果
     */
    IPage<FlowExecutionHistoryVO> listExecutionHistory(Integer pageNum, Integer pageSize,
                                                       String flowDefinitionId, Integer status,
                                                       String flowName, String startTime, String endTime);

    /**
     * 终止流程执行（节点失败时调用，停止后续节点执行）
     *
     * @param processInstanceId Flowable 流程实例 ID
     */
    void terminateExecution(String processInstanceId);

    /**
     * 重试流程执行（重新启动流程）
     *
     * @param processInstanceId 原流程实例 ID（用于定位流程定义）
     * @return 新的执行启动响应
     */
    FlowExecuteResponseVO retryExecution(String processInstanceId);

    /**
     * 清理历史"执行中"脏数据
     * <p>
     * 将 flow_execution 表中 status=0（执行中）但对应 Flowable 流程实例已结束的记录，
     * 同步更新为正确的终态（成功=1 / 失败=2）。
     * </p>
     * <p>用于一次性修复历史遗留的僵尸"执行中"记录。</p>
     *
     * @return 被修复的记录数
     */
    int cleanupZombieExecutingRecords();
}
