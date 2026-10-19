package com.example.testplatform.execution.engine.executor;

import com.example.testplatform.app.domain.dto.DirectTestRequestDTO;
import com.example.testplatform.apicase.domain.po.ApiCase;
import com.example.testplatform.execution.domain.vo.HttpResponse;
import com.example.testplatform.execution.engine.ExecuteContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 用例执行器
 *
 * @author TestPlatform
 */
@Slf4j
@Component
public class HttpCaseExecutor {

    /**
     * 直接执行 HTTP 请求（不走 task/node_instance 创建逻辑）
     *
     * @param dto 直接测试请求参数
     * @return HTTP 响应
     */
    public HttpResponse executeDirect(DirectTestRequestDTO dto) {
        int timeout = dto.getTimeout() != null ? dto.getTimeout() : 30000;

        HttpMethod method = (dto.getRequestType() != null && !dto.getRequestType().isEmpty())
                ? HttpMethod.valueOf(dto.getRequestType().toUpperCase())
                : HttpMethod.GET;

        return doExecute(
                dto.getAddress(),
                dto.getRequestParams(),
                dto.getRequestHeaders(),
                dto.getRequestBody(),
                method,
                timeout,
                "[Direct]");
    }

    /**
     * 执行 HTTP 用例（任务引擎调用）
     *
     * @param context 执行上下文
     * @param apiCase 用例定义
     * @param headers 请求头
     * @param params  请求参数
     * @param body    请求体
     * @param timeout 超时时间（毫秒）
     * @return HTTP 响应
     */
    public HttpResponse execute(ExecuteContext context, ApiCase apiCase,
                                Map<String, String> headers,
                                Map<String, Object> params,
                                Object body,
                                int timeout) {
        HttpMethod method = HttpMethod.valueOf(apiCase.getRequestType().name());

        return doExecute(
                apiCase.getAddress(),
                params,
                headers,
                body,
                method,
                timeout,
                "");
    }

    // ==================== 核心执行逻辑 ====================

    /**
     * 统一 HTTP 请求执行核心
     * <p>
     * 两条入口（executeDirect / execute）共享此方法，
     * 负责 RestTemplate 创建、URL 拼接、异步执行 + 超时控制、响应解析
     *
     * @param address    请求地址
     * @param params     查询参数
     * @param headers    请求头
     * @param body       请求体
     * @param method     HTTP 方法
     * @param timeout    超时（毫秒）
     * @param logPrefix  日志前缀（"[Direct]" 或 ""）
     * @return HTTP 响应
     */
    private HttpResponse doExecute(String address,
                                    Map<String, Object> params,
                                    Map<String, String> headers,
                                    Object body,
                                    HttpMethod method,
                                    int timeout,
                                    String logPrefix) {
        LocalDateTime startTime = LocalDateTime.now();
        CompletableFuture<ResponseEntity<String>> future = null;

        try {
            RestTemplate restTemplate = createRestTemplate(timeout);
            String url = buildUrl(address, params);
            HttpHeaders httpHeaders = buildHttpHeaders(headers);
            HttpEntity<Object> entity = new HttpEntity<>(body, httpHeaders);

            log.info("{}发送 HTTP 请求: method={}, url={}, timeout={}ms", logPrefix, method, url, timeout);

            // 异步执行 + 超时控制
            future = CompletableFuture.supplyAsync(() -> {
                try {
                    return restTemplate.exchange(url, method, entity, String.class);
                } catch (Exception e) {
                    throw new RuntimeException("HTTP 请求失败: " + e.getMessage(), e);
                }
            });

            ResponseEntity<String> response = future.get(timeout, TimeUnit.MILLISECONDS);
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();

            HttpResponse httpResponse = parseResponse(response, duration);

            log.info("{}HTTP 请求完成: statusCode={}, duration={}ms, size={}bytes",
                    logPrefix, httpResponse.getStatusCode(), duration, httpResponse.getSize());

            return httpResponse;

        } catch (java.util.concurrent.TimeoutException e) {
            // 超时后取消底层 HTTP 调用，释放 ForkJoinPool 线程
            if (future != null) {
                future.cancel(true);
            }
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            log.error("{}HTTP 请求超时: url={}, timeout={}ms, duration={}ms",
                    logPrefix, address, timeout, duration);
            throw new RuntimeException("HTTP 请求超时(" + timeout + "ms): " + address, e);
        } catch (Exception e) {
            // 其他异常也取消底层调用
            if (future != null) {
                future.cancel(true);
            }
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            log.error("{}HTTP 请求失败: url={}, duration={}ms, error={}",
                    logPrefix, address, duration, e.getMessage(), e);
            throw new RuntimeException("HTTP 请求失败: " + e.getMessage(), e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建带超时配置的 RestTemplate
     */
    private RestTemplate createRestTemplate(int timeout) {
        RestTemplate restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        restTemplate.setRequestFactory(factory);
        return restTemplate;
    }

    /**
     * 构建完整 URL（baseUrl + queryString）
     */
    private String buildUrl(String baseUrl, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return baseUrl;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
        }
        return builder.build().toUriString();
    }

    /**
     * 构建 HTTP 请求头（默认 Content-Type: application/json）
     */
    private HttpHeaders buildHttpHeaders(Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                httpHeaders.add(entry.getKey(), entry.getValue());
            }
        }
        if (!httpHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        }
        return httpHeaders;
    }

    /**
     * 解析 Spring ResponseEntity 为 HttpResponse VO
     */
    private HttpResponse parseResponse(ResponseEntity<String> response, long duration) {
        Map<String, String> responseHeaders = new java.util.HashMap<>();
        if (response.getHeaders() != null) {
            response.getHeaders().forEach((key, values) -> {
                if (values != null && !values.isEmpty()) {
                    responseHeaders.put(key, values.get(0));
                }
            });
        }

        String responseBody = response.getBody();
        long size = responseBody != null ? responseBody.getBytes().length : 0;

        return HttpResponse.builder()
                .statusCode(response.getStatusCodeValue())
                .headers(responseHeaders)
                .body(responseBody)
                .format("JSON")
                .size(size)
                .duration(duration)
                .build();
    }
}
