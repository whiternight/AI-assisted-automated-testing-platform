package com.example.testplatform.apicase.service;

import com.example.testplatform.apicase.domain.dto.*;
import com.example.testplatform.app.domain.dto.*;
import com.example.testplatform.apicase.domain.po.ApiCase;
import com.example.testplatform.apicase.domain.vo.ApiCaseStatisticsVO;
import com.example.testplatform.apicase.domain.vo.ApiCaseVO;
import com.example.testplatform.apicase.domain.vo.BatchDebugResultVO;
import com.example.testplatform.apicase.domain.vo.CaseDebugResponseVO;
import com.example.testplatform.apicase.domain.vo.DuplicateCheckResultVO;
import com.example.testplatform.enums.ProtocolTypeEnum;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 用例主表 服务类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
public interface IApiCaseService extends IService<ApiCase> {

    /**
     * 单用例调试执行
     */
    CaseDebugResponseVO debug(String caseId, CaseDebugRequestDTO requestDTO);

    /**
     * 根据ID查询用例详情（含完整参数）
     */
    ApiCase getCaseDetail(String caseId);

    /**
     * 重试执行历史用例
     */
    CaseDebugResponseVO retry(String historyId);

    /**
     * 检查用例是否重复
     * @param applicationId 应用ID
     * @param address 请求地址
     * @param protocolType 协议类型
     * @param excludeId 排除的用例ID（更新时使用）
     * @return 重复校验结果
     */
    DuplicateCheckResultVO checkDuplicate(String applicationId, String address,
                                          ProtocolTypeEnum protocolType, String excludeId);

    /**
     * 新增HTTP用例
     */
    String createHttpCase(HttpCaseCreateDTO dto);

    /**
     * 新增Dubbo用例
     */
    String createDubboCase(DubboCaseCreateDTO dto);

    /**
     * 更新HTTP用例
     */
    void updateHttpCase(HttpCaseUpdateDTO dto);

    /**
     * 更新Dubbo用例
     */
    void updateDubboCase(DubboCaseUpdateDTO dto);

    /**
     * 根据ID查询用例详情
     */
    ApiCaseVO getCaseById(String id);

    /**
     * 分页查询用例列表
     */
    Page<ApiCaseVO> listCases(ApiCaseQueryDTO queryDTO);

    /**
     * 逻辑删除用例
     */
    void deleteCase(String id);

    /**
     * 批量删除用例
     */
    void batchDeleteCases(List<String> ids);

    /**
     * 更新自动化开关（单条）
     */
    void updateAutomationFlag(String id, Boolean isAutomationEnabled);

    /**
     * 批量更新自动化开关
     */
    void batchUpdateAutomationFlag(List<String> ids, Boolean isAutomationEnabled);

    /**
     * 批量变更负责人
     */
    void batchUpdateOwner(List<String> ids, Long ownerId);

    /**
     * 根据应用ID分页查询用例列表
     *
     * @param applicationId 应用ID（UUID字符串）
     * @param pageNum       当前页码
     * @param pageSize      每页条数
     * @return 分页用例VO列表
     */
    Page<ApiCaseVO> listByApplicationId(String applicationId, Long pageNum, Long pageSize);

    /**
     * 批量调试执行用例
     * <p>
     * 接收用例 ID 列表，逐个执行调试，返回每个用例的执行结果。
     * 单个用例执行失败不会影响其他用例，异常信息会填充到 errorMsg 字段。
     *
     * @param caseIds    用例 ID 列表
     * @param requestDTO 调试请求参数（环境、请求头、请求体等覆盖参数）
     * @return 每个用例的调试结果列表
     */
    List<BatchDebugResultVO> batchDebug(List<String> caseIds, CaseDebugRequestDTO requestDTO);

    /**
     * 从 cURL 命令导入创建 HTTP 用例
     *
     * @param dto cURL 导入参数
     * @return 新创建的用例ID
     */
    String importCurl(CurlImportDTO dto);

    /**
     * 获取首页用例统计概览
     * <p>统计总用例数、HTTP 用例数、Dubbo 用例数及最近一次执行成功率</p>
     *
     * @return 用例统计概览 VO
     */
    ApiCaseStatisticsVO getStatistics();

}
