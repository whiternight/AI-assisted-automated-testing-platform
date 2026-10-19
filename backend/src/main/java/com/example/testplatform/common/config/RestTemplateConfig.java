package com.example.testplatform.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置类
 *
 * @author TestPlatform
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @ConfigurationProperties(prefix = "http.client")
    public HttpClientProperties httpClientProperties() {
        return new HttpClientProperties();
    }

    /**
     * 创建 RestTemplate Bean
     * 使用配置的超时时间
     *
     * @return RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate(HttpClientProperties properties) {
        ClientHttpRequestFactory factory = createRequestFactory(properties);
        return new RestTemplate(factory);
    }

    /**
     * 创建请求工厂
     *
     * @param properties HTTP 客户端配置
     * @return ClientHttpRequestFactory
     */
    private ClientHttpRequestFactory createRequestFactory(HttpClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }

    /**
     * HTTP 客户端配置属性
     */
    @Data
    public static class HttpClientProperties {
        /**
         * 连接超时（毫秒）
         */
        private int connectTimeout = 5000;

        /**
         * 读取超时（毫秒）
         */
        private int readTimeout = 30000;

        /**
         * 写入超时（毫秒）
         */
        private int writeTimeout = 30000;
    }
}
