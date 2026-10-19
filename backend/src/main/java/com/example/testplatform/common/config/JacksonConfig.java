package com.example.testplatform.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Jackson 全局配置
 * <p>
 * 解决前端 String 与后端 Long 类型不一致的问题：
 * - 序列化（后端→前端）：Long 输出为 String，避免 JS 精度丢失
 * - 反序列化（前端→后端）：String 自动转为 Long
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longStringCustomizer() {
        return builder -> {
            // Long 包装类型
            builder.serializerByType(Long.class, new LongToStringSerializer());
            builder.deserializerByType(Long.class, new StringToLongDeserializer());
            // long 基本类型
            builder.serializerByType(Long.TYPE, new LongToStringSerializer());
            builder.deserializerByType(Long.TYPE, new StringToLongDeserializer());
        };
    }

    /**
     * Long → String 序列化器
     */
    private static class LongToStringSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeString(value.toString());
            }
        }
    }

    /**
     * String → Long 反序列化器
     * 兼容前端传 String "123" 或直接传数字 123
     */
    private static class StringToLongDeserializer extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getText();
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                throw new IOException("无法将值 '" + text + "' 转换为 Long 类型", e);
            }
        }

        @Override
        public Long getNullValue(DeserializationContext ctxt) {
            return null;
        }
    }
}
