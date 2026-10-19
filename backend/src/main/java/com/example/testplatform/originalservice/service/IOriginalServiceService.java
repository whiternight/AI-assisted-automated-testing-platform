package com.example.testplatform.originalservice.service;

import com.example.testplatform.originalservice.domain.po.OriginalService;
import com.example.testplatform.originalservice.domain.dto.*;
import com.example.testplatform.originalservice.domain.vo.AvailableCaseVO;
import com.example.testplatform.originalservice.domain.vo.OriginalServiceVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 原服务表 服务类
 * </p>
 *
 * @author author
 * @since 2026-06-17
 */
public interface IOriginalServiceService extends IService<OriginalService> {

    /**
     * 分页查询原服务列表
     *
     * @param queryDTO 查询参数
     * @return 分页结果
     */
    Page<OriginalServiceVO> listOriginalServices(OriginalServiceQueryDTO queryDTO);

    /**
     * 查询可选用例列表（未在原服务中的用例）
     *
     * @param queryDTO 查询参数
     * @return 分页可选用例列表
     */
    Page<AvailableCaseVO> listAvailableCases(AvailableCaseQueryDTO queryDTO);

    /**
     * 批量添加用例到原服务
     *
     * @param addDTO 添加参数
     * @return 添加成功的数量
     */
    int addCases(OriginalServiceAddDTO addDTO);

    /**
     * 手动创建原服务
     *
     * @param createDTO 创建参数
     * @return 创建后的原服务VO
     */
    OriginalServiceVO createOriginalService(OriginalServiceCreateDTO createDTO);

    /**
     * 更新原服务（修改请求参数等）
     *
     * @param id        原服务ID
     * @param updateDTO 更新参数
     */
    void updateOriginalService(String id, OriginalServiceUpdateDTO updateDTO);

    /**
     * 查询原服务详情
     *
     * @param id 原服务ID
     * @return 原服务VO
     */
    OriginalServiceVO getDetail(String id);

    /**
     * 删除原服务（逻辑删除）
     *
     * @param id 原服务ID
     */
    void deleteOriginalService(String id);

    /**
     * 批量删除原服务（逻辑删除）
     *
     * @param ids 原服务ID列表
     */
    void batchDeleteOriginalServices(List<String> ids);
}
