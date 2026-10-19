package com.example.testplatform.workflow.controller;

import com.example.testplatform.common.result.Result;
import com.example.testplatform.workflow.domain.dto.FlowDeployRequestDTO;
import com.example.testplatform.workflow.domain.dto.FlowSaveDraftDTO;
import com.example.testplatform.workflow.domain.dto.FlowSaveRequestDTO;
import com.example.testplatform.workflow.domain.dto.FlowUpdateRequestDTO;
import com.example.testplatform.workflow.domain.vo.FlowDefinitionDetailVO;
import com.example.testplatform.workflow.domain.vo.FlowDefinitionVO;
import com.example.testplatform.workflow.domain.vo.FlowDeployResponseVO;
import com.example.testplatform.workflow.service.IFlowDefinitionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 流程定义管理 前端控制器
 *
 * @author TestPlatform
 */
@Tag(name = "流程定义管理", description = "流程定义查询、删除、状态更新等接口")
@RestController
@RequestMapping("/api-flow-definition")
public class FlowDefinitionController {

    @Resource
    private IFlowDefinitionService flowDefinitionService;

    @Operation(summary = "部署流程定义", description = "将 BPMN XML 部署到 Flowable 引擎，状态变为 2=已发布。支持两种方式：1) 传 id 部署已保存的未部署流程；2) 同时传 id+bpmnXml 覆盖保存并部署")
    @PostMapping("/deploy")
    public Result<FlowDeployResponseVO> deploy(@Valid @RequestBody FlowDeployRequestDTO dto) {
        FlowDeployResponseVO response = flowDefinitionService.deploy(dto);
        return Result.success(response);
    }

    @Operation(summary = "分页查询流程列表", description = "仅返回当前用户创建或拥有的流程，可按应用和名称过滤")
    @GetMapping("/list")
    public Result<Page<FlowDefinitionVO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String applicationId) {
        Page<FlowDefinitionVO> page = flowDefinitionService.listFlows(pageNum, pageSize, name, applicationId);
        return Result.success(page);
    }

    @Operation(summary = "查询流程定义详情", description = "返回流程基础信息、BPMN XML、节点列表、连线列表、最近执行记录")
    @GetMapping("/{id}")
    public Result<FlowDefinitionDetailVO> getDetail(@PathVariable String id) {
        FlowDefinitionDetailVO detail = flowDefinitionService.getDetail(id);
        return Result.success(detail);
    }

    @Operation(summary = "删除流程定义", description = "逻辑删除流程定义，同时删除 Flowable 部署（有活跃实例时不允许删除）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteFlow(@PathVariable String id) {
        flowDefinitionService.deleteFlow(id);
        return Result.success();
    }

    @Operation(summary = "更新流程状态", description = "更新流程定义状态：0=草稿 1=未部署 2=已发布 3=已停用")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable String id,
            @RequestParam Integer status) {
        flowDefinitionService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "更新流程定义", description = "编辑已部署的流程定义，支持更新 BPMN XML（重新部署）、节点配置及基础信息")
    @PutMapping("/update")
    public Result<FlowDeployResponseVO> update(@Valid @RequestBody FlowUpdateRequestDTO dto) {
        FlowDeployResponseVO response = flowDefinitionService.update(dto);
        return Result.success(response);
    }

    @Operation(summary = "保存流程草稿", description = "仅保存基础信息（名称、应用、业务域），状态 0=草稿，不保存 BPMN 和节点配置。id 可空，传入则更新，不传则新建")
    @PostMapping("/draft")
    public Result<FlowDefinitionVO> saveDraft(@Valid @RequestBody FlowSaveDraftDTO dto) {
        FlowDefinitionVO vo = flowDefinitionService.saveDraft(dto);
        return Result.success(vo);
    }

    @Operation(summary = "保存流程设计", description = "保存 BPMN XML 和 serviceTask 节点配置，状态 1=未部署，不部署到 Flowable。id 可空，传入则更新，不传则新建")
    @PostMapping("/save")
    public Result<FlowDefinitionVO> saveFlowDesign(@Valid @RequestBody FlowSaveRequestDTO dto) {
        FlowDefinitionVO vo = flowDefinitionService.saveFlowDesign(dto);
        return Result.success(vo);
    }
}
