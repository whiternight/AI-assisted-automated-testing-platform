package com.example.testplatform.originalservice.service.impl;

import com.example.testplatform.apicase.domain.po.ApiCase;
import com.example.testplatform.apicase.mapper.ApiCaseMapper;
import com.example.testplatform.app.domain.po.Application;
import com.example.testplatform.app.mapper.ApplicationMapper;
import com.example.testplatform.common.exception.BusinessException;
import com.example.testplatform.common.util.SecurityUtil;
import com.example.testplatform.enums.BusinessExceptionEnum;
import com.example.testplatform.originalservice.domain.po.OriginalService;
import com.example.testplatform.originalservice.domain.dto.*;
import com.example.testplatform.originalservice.domain.vo.AvailableCaseVO;
import com.example.testplatform.originalservice.domain.vo.OriginalServiceVO;
import com.example.testplatform.originalservice.mapper.OriginalServiceMapper;
import com.example.testplatform.originalservice.service.IOriginalServiceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 原服务表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-06-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OriginalServiceServiceImpl extends ServiceImpl<OriginalServiceMapper, OriginalService> implements IOriginalServiceService {

    private final OriginalServiceMapper originalServiceMapper;
    private final ApiCaseMapper apiCaseMapper;
    private final ApplicationMapper applicationMapper;

    @Override
    public Page<OriginalServiceVO> listOriginalServices(OriginalServiceQueryDTO queryDTO) {
        Page<OriginalServiceVO> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        return originalServiceMapper.selectOriginalServicePage(
                page,
                queryDTO.getKeyword(),
                queryDTO.getApplicationId(),
                queryDTO.getIsAvailable());
    }

    @Override
    public Page<AvailableCaseVO> listAvailableCases(AvailableCaseQueryDTO queryDTO) {
        // 先查询已经在原服务中的用例ID列表
        List<String> excludeCaseIds = getExistingCaseIds();

        Page<AvailableCaseVO> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        return originalServiceMapper.selectAvailableCasePage(
                page,
                queryDTO.getKeyword(),
                queryDTO.getApplicationId(),
                queryDTO.getProtocolType(),
                excludeCaseIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addCases(OriginalServiceAddDTO addDTO) {
        List<String> caseIds = addDTO.getCaseIds();
        Long currentUserId = SecurityUtil.getCurrentUserId();

        // 校验应用是否存在
        Application application = applicationMapper.selectById(addDTO.getApplicationId());
        if (application == null) {
            throw new BusinessException(BusinessExceptionEnum.APP_NOT_FOUND,
                    "应用不存在: " + addDTO.getApplicationId());
        }

        // 查询已经在原服务中的用例ID，避免重复添加
        List<String> existingCaseIds = getExistingCaseIds();
        List<String> duplicateIds = caseIds.stream()
                .filter(existingCaseIds::contains)
                .collect(Collectors.toList());
        if (!duplicateIds.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.ALREADY_EXISTS,
                    "以下用例已存在于原服务中: " + String.join(", ", duplicateIds));
        }

        // 验证所有用例是否存在（ApiCase 已启用 @TableLogic，selectById 默认不会过滤 deleted，需要手动加）
        for (String caseId : caseIds) {
            ApiCase apiCase = apiCaseMapper.selectById(caseId);
            if (apiCase == null) {
                throw new BusinessException(BusinessExceptionEnum.CASE_NOT_FOUND,
                        "用例不存在: " + caseId);
            }
        }

        // 批量创建原服务记录
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (String caseId : caseIds) {
            ApiCase apiCase = apiCaseMapper.selectById(caseId);

            OriginalService service = new OriginalService()
                    .setCaseId(caseId)
                    .setName(apiCase.getName())
                    .setProtocolType(apiCase.getProtocolType())
                    .setRequestType(apiCase.getRequestType() != null ? apiCase.getRequestType().getMethod() : null)
                    .setAddress(apiCase.getAddress())
                    .setRequestHeaders(apiCase.getRequestHeaders())
                    .setRequestBody(apiCase.getRequestBody())
                    .setIsAvailable(1)
                    .setInputParams(apiCase.getRequestParams())
                    .setOutputParams(null)
                    .setAssertions(apiCase.getResponseAssertions())
                    .setServiceType(null) // 预留字段，待后续扩展
                    .setApplicationId(addDTO.getApplicationId())
                    .setCreatorId(currentUserId)
                    .setOwnerId(currentUserId)
                    .setUpdaterId(currentUserId)
                    .setCreatedAt(now)
                    .setUpdatedAt(now);
            originalServiceMapper.insert(service);
            count++;
        }
        log.info("批量添加原服务成功，共添加 {} 条", count);
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OriginalServiceVO createOriginalService(OriginalServiceCreateDTO createDTO) {
        Long currentUserId = SecurityUtil.getCurrentUserId();

        // 校验应用是否存在
        Application application = applicationMapper.selectById(createDTO.getApplicationId());
        if (application == null) {
            throw new BusinessException(BusinessExceptionEnum.APP_NOT_FOUND,
                    "应用不存在: " + createDTO.getApplicationId());
        }

        LocalDateTime now = LocalDateTime.now();
        OriginalService service = new OriginalService()
                .setCaseId(createDTO.getCaseId())
                .setName(createDTO.getName())
                .setProtocolType(createDTO.getProtocolType() != null ? createDTO.getProtocolType() : 1)
                .setRequestType(createDTO.getRequestType() != null ? createDTO.getRequestType() : "POST")
                .setAddress(createDTO.getAddress())
                .setRequestHeaders(createDTO.getRequestHeaders())
                .setRequestBody(createDTO.getRequestBody())
                .setIsAvailable(1)
                .setInputParams(createDTO.getRequestParams())
                .setOutputParams(createDTO.getOutputParams())
                .setAssertions(createDTO.getAssertions())
                .setServiceType(createDTO.getServiceType())
                .setApplicationId(createDTO.getApplicationId())
                .setCreatorId(currentUserId)
                .setOwnerId(currentUserId)
                .setUpdaterId(currentUserId)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        originalServiceMapper.insert(service);
        log.info("手动创建原服务成功，id={}", service.getId());

        return originalServiceMapper.selectOriginalServiceById(service.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOriginalService(String id, OriginalServiceUpdateDTO updateDTO) {
        OriginalService service = originalServiceMapper.selectById(id);
        if (service == null) {
            throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "原服务不存在: " + id);
        }

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (updateDTO.getName() != null) {
            service.setName(updateDTO.getName());
        }
        if (updateDTO.getIsAvailable() != null) {
            service.setIsAvailable(updateDTO.getIsAvailable());
        }
        if (updateDTO.getProtocolType() != null) {
            service.setProtocolType(updateDTO.getProtocolType());
        }
        if (updateDTO.getRequestType() != null) {
            service.setRequestType(updateDTO.getRequestType());
        }
        if (updateDTO.getAddress() != null) {
            service.setAddress(updateDTO.getAddress());
        }
        if (updateDTO.getRequestHeaders() != null) {
            service.setRequestHeaders(updateDTO.getRequestHeaders());
        }
        if (updateDTO.getInputParams() != null) {
            service.setInputParams(updateDTO.getInputParams());
        }
        if (updateDTO.getRequestBody() != null) {
            service.setRequestBody(updateDTO.getRequestBody());
        }
        if (updateDTO.getOutputParams() != null) {
            service.setOutputParams(updateDTO.getOutputParams());
        }
        if (updateDTO.getAssertions() != null) {
            service.setAssertions(updateDTO.getAssertions());
        }
        if (updateDTO.getServiceType() != null) {
            service.setServiceType(updateDTO.getServiceType());
        }
        service.setUpdaterId(currentUserId);
        service.setUpdatedAt(LocalDateTime.now());
        originalServiceMapper.updateById(service);
        log.info("更新原服务成功，id={}", id);
    }

    @Override
    public OriginalServiceVO getDetail(String id) {
        OriginalServiceVO vo = originalServiceMapper.selectOriginalServiceById(id);
        if (vo == null) {
            throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "原服务不存在: " + id);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOriginalService(String id) {
        // OriginalService 已启用 @TableLogic，getById 自动过滤 deleted=1
        OriginalService service = this.getById(id);
        if (service == null) {
            throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "原服务不存在: " + id);
        }

        // MyBatis-Plus 逻辑删除：UPDATE original_service SET deleted=1 WHERE id=? AND deleted=0
        boolean removed = this.removeById(id);
        if (!removed) {
            throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "原服务已被删除: " + id);
        }
        log.info("删除原服务成功，id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteOriginalServices(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.PARAM_ERROR, "原服务ID列表不能为空");
        }

        // MyBatis-Plus 批量逻辑删除：自动过滤已删除，仅 UPDATE 未删除的
        boolean success = this.removeBatchByIds(ids);
        if (!success) {
            throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "原服务不存在或已全部被删除");
        }
        log.info("批量删除原服务成功，共删除 {} 条", ids.size());
    }

    /**
     * 获取已在原服务表中的用例ID列表
     */
    private List<String> getExistingCaseIds() {
        LambdaQueryWrapper<OriginalService> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(OriginalService::getCaseId);
        List<OriginalService> existingServices = originalServiceMapper.selectList(wrapper);
        return existingServices.stream()
                .map(OriginalService::getCaseId)
                .distinct()
                .collect(Collectors.toList());
    }
}
