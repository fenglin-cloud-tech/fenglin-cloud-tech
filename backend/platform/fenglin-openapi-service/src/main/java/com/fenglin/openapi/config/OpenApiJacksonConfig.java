package com.fenglin.openapi.config;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 开放平台 ID 序列化配置。
 *
 * <p>应用 ID、密钥 ID 均为雪花 ID（19 位），超出 JavaScript Number 安全整数范围。
 * 若按 JSON number 输出，门户在前端路由与查询中会因精度丢失命中错误应用，
 * 因此这里仅把以 Id 结尾的 Long 字段序列化为字符串，分页 total 等计数仍保持 number。</p>
 */
@Configuration
public class OpenApiJacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer openApiLongIdAsStringCustomizer() {
        return builder -> builder.postConfigurer(mapper -> mapper.registerModule(
                new SimpleModule("openapi-long-id-as-string")
                        .setSerializerModifier(new BeanSerializerModifier() {
                            @Override
                            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                                             BeanDescription beanDesc,
                                                                             List<BeanPropertyWriter> beanProperties) {
                                for (BeanPropertyWriter writer : beanProperties) {
                                    Class<?> raw = writer.getType().getRawClass();
                                    if ((raw == Long.class || raw == long.class)
                                            && writer.getName().toLowerCase().endsWith("id")) {
                                        writer.assignSerializer(ToStringSerializer.instance);
                                    }
                                }
                                return beanProperties;
                            }
                        })));
    }
}
