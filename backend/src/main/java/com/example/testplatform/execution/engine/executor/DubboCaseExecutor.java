package com.example.testplatform.execution.engine.executor;

import com.example.testplatform.app.domain.dto.DirectTestRequestDTO;
import com.example.testplatform.apicase.domain.po.ApiCase;
import com.example.testplatform.execution.domain.vo.DubboResponse;
import com.example.testplatform.execution.engine.ExecuteContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.service.GenericService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Dubbo 用例执行器
 * <p>
 * 支持两种连接模式：
 * 1. 直连模式：提供 dubboUrl（如 dubbo://host:port），直接连接目标服务
 * 2. 注册中心模式：不传 dubboUrl，通过 Nacos 注册中心发现服务
 *
 * @author TestPlatform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DubboCaseExecutor {

    @Qualifier("asyncTaskExecutor")
    private final Executor asyncTaskExecutor;

    /** Dubbo 应用名称（与 application.yaml 中 dubbo.application.name 对齐） */
    @Value("${dubbo.application.name:TestPlatform}")
    private String dubboAppName;

    /** Nacos 注册中心地址（如 nacos://127.0.0.1:8848） */
    @Value("${dubbo.registry.address:}")
    private String registryAddress;

    /** Nacos 注册中心参数（如 namespace、group 等） */
    @Value("${dubbo.registry.parameters.namespace:}")
    private String registryNamespace;

    @Value("${dubbo.registry.parameters.group:}")
    private String registryGroup;

    /**
     * 直接执行 Dubbo 泛化调用（不走 task/node_instance 创建逻辑）
     *
     * @param dto 直接测试请求参数
     * @return Dubbo 响应
     */
    public DubboResponse executeDirect(DirectTestRequestDTO dto) {
        String[] parameterTypes = dto.getParamTypes() != null
                ? dto.getParamTypes().toArray(new String[0])
                : new String[0];
        Object[] parameterValues = dto.getParamValues() != null
                ? dto.getParamValues().toArray(new Object[0])
                : new Object[0];

        return doExecute(
                dto.getServiceName(),
                dto.getMethodName(),
                dto.getVersion() != null ? dto.getVersion() : "1.0.0",
                dto.getGroup(),
                dto.getDubboUrl(),
                parameterTypes,
                parameterValues,
                dto.getTimeout() != null ? dto.getTimeout() : 30000,
                "[Direct]");
    }

    /**
     * 执行 Dubbo 用例（泛化调用，任务引擎调用）
     *
     * @param context 执行上下文
     * @param apiCase 用例定义
     * @param headers 请求头（Dubbo 中通常不使用）
     * @param params  请求参数
     * @param body    请求体
     * @param timeout 超时时间（毫秒）
     * @return Dubbo 响应
     */
    public DubboResponse execute(ExecuteContext context, ApiCase apiCase,
                                 Map<String, String> headers,
                                 Map<String, Object> params,
                                 Object body,
                                 int timeout) {
        DubboInterfaceInfo info = parseInterfaceInfo(apiCase.getAddress());

        return doExecute(
                info.interfaceName,
                info.methodName,
                info.version,
                info.group,
                info.directUrl,
                extractParameterTypes(body),
                extractParameterValues(body),
                timeout,
                "");
    }

    // ==================== 核心执行逻辑 ====================

    /**
     * 统一 Dubbo 泛化调用核心
     * <p>
     * 两条入口（executeDirect / execute）共享此方法，
     * 负责 ReferenceConfig 创建、GenericService 获取、泛化调用、结果封装
     *
     * @param interfaceName   接口全名
     * @param methodName      方法名
     * @param version         版本号
     * @param group           服务分组（可选）
     * @param directUrl       直连地址（可选，如 dubbo://host:port），为空则走注册中心
     * @param parameterTypes  参数类型数组
     * @param parameterValues 参数值数组
     * @param timeout         超时（毫秒）
     * @param logPrefix       日志前缀
     * @return Dubbo 响应
     */
    private DubboResponse doExecute(String interfaceName,
                                     String methodName,
                                     String version,
                                     String group,
                                     String directUrl,
                                     String[] parameterTypes,
                                     Object[] parameterValues,
                                     int timeout,
                                     String logPrefix) {
        LocalDateTime startTime = LocalDateTime.now();

        try {
            String mode = (directUrl != null && !directUrl.isEmpty()) ? "直连" : "注册中心";
            log.info("{}发起 Dubbo 泛化调用[{}]: interface={}, method={}, version={}, timeout={}ms",
                    logPrefix, mode, interfaceName, methodName, version, timeout);

            // 创建 ReferenceConfig + 获取泛化服务
            GenericService genericService = createGenericService(interfaceName, version, group, directUrl, timeout);

            // 执行泛化调用（包装 CompletableFuture 实现超时保护）
            CompletableFuture<Object> future = CompletableFuture.supplyAsync(() ->
                    genericService.$invoke(methodName, parameterTypes, parameterValues),
                    asyncTaskExecutor);
            Object result = future.get(timeout, TimeUnit.MILLISECONDS);

            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            log.info("{}Dubbo 调用成功: method={}, duration={}ms", logPrefix, methodName, duration);

            return DubboResponse.builder()
                    .result(result)
                    .success(true)
                    .duration(duration)
                    .build();

        } catch (Exception e) {
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            log.error("{}Dubbo 调用失败: interface={}, duration={}ms, error={}",
                    logPrefix, interfaceName, duration, e.getMessage(), e);

            return DubboResponse.builder()
                    .result(null)
                    .success(false)
                    .errorMsg(e.getMessage())
                    .duration(duration)
                    .build();
        }
    }

    /**
     * 创建泛化服务引用
     * <p>
     * 支持两种模式：
     * 1. 直连模式：directUrl 不为空，直接连接目标 provider
     * 2. 注册中心模式：directUrl 为空，通过 Nacos 注册中心发现服务
     *
     * @param interfaceName 接口全名
     * @param version       版本号
     * @param group         服务分组（可选）
     * @param directUrl     直连地址（可选），如 dubbo://192.168.1.100:20880
     * @param timeout       超时时间（毫秒）
     * @return GenericService 泛化服务代理
     */
    private GenericService createGenericService(String interfaceName, String version,
                                                 String group, String directUrl, int timeout) {
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
        reference.setInterface(interfaceName);
        reference.setVersion(version);
        reference.setTimeout(timeout);
        reference.setGeneric(true);

        // 设置服务分组
        if (group != null && !group.isEmpty()) {
            reference.setGroup(group);
        }

        if (directUrl != null && !directUrl.isEmpty()) {
            // ===== 直连模式 =====
            log.info("Dubbo 直连模式: url={}", directUrl);
            reference.setUrl(directUrl);
        } else {
            // ===== 注册中心模式 =====
            log.info("Dubbo 注册中心模式: registry={}", registryAddress);

            ApplicationConfig applicationConfig = new ApplicationConfig();
            applicationConfig.setName(dubboAppName);
            reference.setApplication(applicationConfig);

            if (registryAddress != null && !registryAddress.isEmpty()) {
                RegistryConfig registryConfig = new RegistryConfig();
                registryConfig.setAddress(registryAddress);
                java.util.Map<String, String> regParams = new java.util.HashMap<>();
                if (registryNamespace != null && !registryNamespace.isEmpty()) {
                    regParams.put("namespace", registryNamespace);
                }
                if (registryGroup != null && !registryGroup.isEmpty()) {
                    regParams.put("group", registryGroup);
                }
                if (!regParams.isEmpty()) {
                    registryConfig.setParameters(regParams);
                }
                reference.setRegistry(registryConfig);
            }
        }

        return reference.get();
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 address 解析接口信息
     * 地址格式：com.example.Service.methodName:version[:group]
     * 直连格式：dubbo://host:port/com.example.Service.methodName:version[:group]
     */
    private DubboInterfaceInfo parseInterfaceInfo(String address) {
        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("Dubbo 接口地址不能为空");
        }

        DubboInterfaceInfo info = new DubboInterfaceInfo();

        // 检测是否为直连 URL（dubbo://host:port/xxx）
        if (address.startsWith("dubbo://")) {
            info.directUrl = address.contains("/") ? address.substring(0, address.indexOf("/", 8)) : address;
            String remainder = address.contains("/") ? address.substring(address.indexOf("/", 8) + 1) : "";
            if (remainder.isEmpty()) {
                throw new IllegalArgumentException("直连 URL 缺少接口路径: " + address);
            }
            parseServicePath(remainder, info);
        } else {
            parseServicePath(address, info);
        }

        return info;
    }

    /**
     * 解析 service.method:version[:group] 格式的路径
     */
    private void parseServicePath(String path, DubboInterfaceInfo info) {
        String[] parts = path.split(":");

        String interfaceAndMethod = parts[0];
        int lastDotIndex = interfaceAndMethod.lastIndexOf('.');
        if (lastDotIndex > 0) {
            info.interfaceName = interfaceAndMethod.substring(0, lastDotIndex);
            info.methodName = interfaceAndMethod.substring(lastDotIndex + 1);
        } else {
            throw new IllegalArgumentException("无效的 Dubbo 接口地址: " + path);
        }

        info.version = (parts.length >= 2) ? parts[1] : "1.0.0";
        info.group = (parts.length >= 3) ? parts[2] : null;
    }

    /**
     * 从请求体提取参数类型（任务引擎路径）
     */
    private String[] extractParameterTypes(Object body) {
        if (body == null) return new String[0];
        if (body instanceof Map) {
            Map<String, Object> bodyMap = (Map<String, Object>) body;
            return bodyMap.keySet().toArray(new String[0]);
        }
        return new String[]{"java.lang.Object"};
    }

    /**
     * 从请求体提取参数值（任务引擎路径）
     */
    private Object[] extractParameterValues(Object body) {
        if (body == null) return new Object[0];
        if (body instanceof Map) {
            Map<String, Object> bodyMap = (Map<String, Object>) body;
            return bodyMap.values().toArray(new Object[0]);
        }
        return new Object[]{body};
    }

    /**
     * Dubbo 接口信息（内部数据载体）
     */
    private static class DubboInterfaceInfo {
        String interfaceName;
        String methodName;
        String version;
        String group;
        String directUrl;
    }
}
