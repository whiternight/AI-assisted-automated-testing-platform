package com.example.testplatform.app.service.impl;

import com.example.testplatform.app.domain.dto.ApplicationCreateDTO;
import com.example.testplatform.app.domain.po.Application;
import com.example.testplatform.app.domain.vo.DepartmentAppTreeVO;
import com.example.testplatform.app.mapper.ApplicationMapper;
import com.example.testplatform.app.service.IApplicationService;
import com.example.testplatform.auth.domain.po.Department;
import com.example.testplatform.auth.service.IDepartmentService;
import com.example.testplatform.common.util.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 应用表 服务实现类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Slf4j
@Service
public class ApplicationServiceImpl extends ServiceImpl<ApplicationMapper, Application> implements IApplicationService {

    @Resource
    private IDepartmentService departmentService;

    @Override
    public List<Application> listByDepartmentIds(List<String> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return List.of();
        }
        // @TableLogic 自动过滤 deleted=1
        return this.list(
                new LambdaQueryWrapper<Application>()
                        .in(Application::getDepartmentId, departmentIds)
                        .orderByDesc(Application::getUpdatedAt)
        );
    }

    /**
     * 新增应用
     * <p>
     * 将 ApplicationCreateDTO 转换为 Application 实体并持久化，
     * 自动填充创建时间、更新时间和逻辑删除标志。
     * 若未指定负责人，则默认为当前登录用户。
     *
     * @param dto 应用创建参数
     * @return 新创建的应用ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createApplication(ApplicationCreateDTO dto) {
        log.info("开始创建应用: name={}, departmentId={}", dto.getName(), dto.getDepartmentId());

        Application application = new Application();
        application.setName(dto.getName());
        application.setDepartmentId(dto.getDepartmentId());
        application.setBusinessDomainId(dto.getBusinessDomainId());
        // 若未指定负责人，则默认为当前登录用户
        application.setOwnerId(dto.getOwnerId() != null ? dto.getOwnerId() : SecurityUtil.getCurrentUserId());
        application.setDescription(dto.getDescription());
        application.setCreatedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());
        application.setDeleted(0);

        this.save(application);
        log.info("应用创建成功: id={}, name={}", application.getId(), application.getName());
        return application.getId();
    }

    /**
     * 逻辑删除应用
     * <p>
     * 通过 MyBatis-Plus 的 removeById 实现逻辑删除（@TableLogic 启用后自动转为 UPDATE ... SET deleted=1）
     *
     * @param id 应用ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApplication(String id) {
        log.info("开始删除应用: id={}", id);

        Application application = this.getById(id);
        if (application == null) {
            throw new IllegalArgumentException("应用不存在: " + id);
        }

        // MyBatis-Plus 逻辑删除：UPDATE application SET deleted=1 WHERE id=? AND deleted=0
        boolean removed = this.removeById(id);
        if (!removed) {
            throw new IllegalStateException("应用已被删除，无法重复操作");
        }

        log.info("应用删除成功: id={}, name={}", id, application.getName());
    }

    /**
     * 查询部门-应用树状结构
     * <p>
     * 先查所有部门，再查所有应用，按 departmentId 分组后在内存中组装树。
     * 两次查询，避免 N+1 问题。
     *
     * @return 部门-应用树状列表
     */
    @Override
    public List<DepartmentAppTreeVO> listDepartmentAppTree() {
        // 1. 查询所有未删除的部门（@TableLogic 自动过滤 deleted=1）
        List<Department> departments = departmentService.list();

        // 2. 查询所有未删除的应用（@TableLogic 自动过滤 deleted=1）
        List<Application> applications = this.list(
                new LambdaQueryWrapper<Application>()
                        .orderByDesc(Application::getUpdatedAt));

        // 3. 按 departmentId 分组
        Map<String, List<Application>> appGroupByDept = applications.stream()
                .filter(app -> app.getDepartmentId() != null)
                .collect(Collectors.groupingBy(Application::getDepartmentId));

        // 4. 组装树状结构
        return departments.stream().map(dept -> {
            List<Application> deptApps = appGroupByDept.getOrDefault(dept.getId(), List.of());
            List<DepartmentAppTreeVO.AppItem> items = deptApps.stream()
                    .map(app -> DepartmentAppTreeVO.AppItem.builder()
                            .id(app.getId())
                            .name(app.getName())
                            .description(app.getDescription())
                            .businessDomainId(app.getBusinessDomainId())
                            .ownerId(app.getOwnerId())
                            .build())
                    .collect(Collectors.toList());

            return DepartmentAppTreeVO.builder()
                    .departmentId(dept.getId())
                    .departmentName(dept.getName())
                    .applications(items)
                    .build();
        }).collect(Collectors.toList());
    }

}
