package com.example.testplatform.app.service.impl;

import com.example.testplatform.app.domain.dto.DirectTestRequestDTO;
import com.example.testplatform.apicase.domain.vo.*;
import com.example.testplatform.execution.domain.vo.*;
import com.example.testplatform.execution.engine.assertion.AssertionEngine;
import com.example.testplatform.execution.engine.executor.DubboCaseExecutor;
import com.example.testplatform.execution.engine.executor.HttpCaseExecutor;
import com.example.testplatform.enums.ProtocolTypeEnum;
import com.example.testplatform.enums.ResponseFormatEnum;
import com.example.testplatform.app.service.DirectTestService;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DirectTestServiceImpl implements DirectTestService {

    @Resource
    private HttpCaseExecutor httpExecutor;

    @Resource
    private DubboCaseExecutor dubboExecutor;

    @Resource
    private AssertionEngine assertionEngine;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public DirectTestResponseVO test(DirectTestRequestDTO dto) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 协议路由执行
            Object response;
            if (dto.getProtocolType() == ProtocolTypeEnum.HTTP) {
                response = httpExecutor.executeDirect(dto);
            } else {
                response = dubboExecutor.executeDirect(dto);
            }

            // 2. 断言校验
            List<AssertionResultVO> assertions = null;
            if (CollectionUtils.isNotEmpty(dto.getResponseAssertions())) {
                assertions = assertionEngine.executeDirect(
                        dto.getResponseAssertions(), response);
            }

            // 3. 生成原始出参快照（使用解析后的真实响应体）
            String rawSnapshot = null;
            try {
                Object snapshotTarget;
                if (response instanceof HttpResponse) {
                    HttpResponse httpResp = (HttpResponse) response;
                    java.util.Map<String, Object> snapshotMap = new java.util.LinkedHashMap<>();
                    snapshotMap.put("statusCode", httpResp.getStatusCode());
                    snapshotMap.put("headers", httpResp.getHeaders());
                    snapshotMap.put("body", parseBody(httpResp.getBody()));
                    snapshotTarget = snapshotMap;
                } else if (response instanceof DubboResponse) {
                    DubboResponse dubboResp = (DubboResponse) response;
                    java.util.Map<String, Object> snapshotMap = new java.util.LinkedHashMap<>();
                    snapshotMap.put("success", dubboResp.getSuccess());
                    snapshotMap.put("result", dubboResp.getResult());
                    snapshotMap.put("errorMsg", dubboResp.getErrorMsg());
                    snapshotTarget = snapshotMap;
                } else {
                    snapshotTarget = response;
                }
                rawSnapshot = objectMapper.writeValueAsString(snapshotTarget);
            } catch (Exception ex) {
                log.warn("[Direct] 序列化出参快照失败: {}", ex.getMessage());
            }

            // 4. 协议级成功判定
            boolean protocolSuccess;
            if (response instanceof HttpResponse) {
                HttpResponse httpResp = (HttpResponse) response;
                protocolSuccess = httpResp.getStatusCode() != null && httpResp.getStatusCode() == 200;
            } else if (response instanceof DubboResponse) {
                DubboResponse dubboResp = (DubboResponse) response;
                protocolSuccess = dubboResp.getSuccess() != null && dubboResp.getSuccess();
            } else {
                protocolSuccess = false;
            }

            // 5. 断言校验（如果配置了断言，需同时通过才算成功）
            boolean assertionPassed = assertions == null || assertions.stream().allMatch(AssertionResultVO::getPassed);
            boolean finalSuccess = protocolSuccess && assertionPassed;

            // 6. 组装返回
            return DirectTestResponseVO.builder()
                    .success(finalSuccess)
                    .duration(System.currentTimeMillis() - startTime)
                    .response(parseResponse(response, dto.getResponseFormat()))
                    .assertions(assertions)
                    .responseSnapshot(rawSnapshot)
                    .build();

        } catch (Exception e) {
            // 异常也正常返回，不抛 500
            log.warn("[Direct] 测试执行异常: {}", e.getMessage(), e);

            // 异常路径也生成出参快照（记录错误信息，方便存储）
            String errorSnapshot = null;
            try {
                java.util.Map<String, Object> errorInfo = new java.util.LinkedHashMap<>();
                errorInfo.put("success", false);
                errorInfo.put("errorType", classifyError(e));
                errorInfo.put("errorMsg", e.getMessage());
                errorInfo.put("duration", System.currentTimeMillis() - startTime);
                errorSnapshot = objectMapper.writeValueAsString(errorInfo);
            } catch (Exception ex) {
                log.warn("[Direct] 序列化异常快照失败: {}", ex.getMessage());
            }

            return DirectTestResponseVO.builder()
                    .success(false)
                    .duration(System.currentTimeMillis() - startTime)
                    .errorType(classifyError(e))
                    .errorMsg(e.getMessage())
                    .responseSnapshot(errorSnapshot)
                    .build();
        }
    }

    /**
     * 将执行器返回的响应对象转换为 ResponseInfoVO
     *
     * @param response HttpResponse 或 DubboResponse
     * @param format 响应格式
     * @return 响应信息 VO
     */
    private ResponseInfoVO parseResponse(Object response, ResponseFormatEnum format) {
        if (response instanceof HttpResponse) {
            HttpResponse httpResp = (HttpResponse) response;
            return ResponseInfoVO.builder()
                    .statusCode(httpResp.getStatusCode())
                    .headers(httpResp.getHeaders())
                    .body(parseBody(httpResp.getBody()))
                    .format(format != null ? format.name() : "JSON")
                    .size(httpResp.getSize())
                    .duration(httpResp.getDuration())
                    .build();
        } else if (response instanceof DubboResponse) {
            DubboResponse dubboResp = (DubboResponse) response;
            return ResponseInfoVO.builder()
                    .statusCode(dubboResp.getSuccess() != null && dubboResp.getSuccess() ? 200 : 500)
                    .body(dubboResp.getResult())
                    .format(format != null ? format.name() : "JSON")
                    .size(0L)
                    .duration(dubboResp.getDuration())
                    .build();
        }
        return ResponseInfoVO.builder()
                .body(response != null ? response.toString() : null)
                .format(format != null ? format.name() : "JSON")
                .build();
    }

    /**
     * 尝试将原始响应体字符串解析为 JSON 对象，失败则回退为原始字符串
     *
     * @param body 原始响应体（通常是 JSON 字符串）
     * @return 解析后的 JSON 对象，或原始字符串
     */
    private Object parseBody(Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof String) {
            String str = ((String) body).trim();
            if (str.isEmpty()) {
                return str;
            }
            try {
                return objectMapper.readTree(str);
            } catch (Exception ex) {
                log.debug("[Direct] 响应体非 JSON 格式，保留原始字符串");
                return str;
            }
        }
        return body;
    }

    /**
     * 异常分类，返回错误类型标识
     *
     * @param e 异常对象
     * @return 错误类型字符串
     */
    private String classifyError(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (message.contains("timeout") || message.contains("timed out")) {
            return "TIMEOUT";
        } else if (message.contains("connect") || message.contains("refused")
                || message.contains("unreachable") || message.contains("network")) {
            return "ENV_ERROR";
        } else if (message.contains("assertion") || message.contains("断言")) {
            return "ASSERTION_FAIL";
        }
        return "PLATFORM_ERROR";
    }
}
