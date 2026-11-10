package com.fenglin.common.web;

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
 * 平台统一的 Long 型主键字符串序列化。
 *
 * <p>雪花 ID 为 19 位，超出 JavaScript Number.MAX_SAFE_INTEGER。若按 JSON number 输出，
 * 浏览器 JSON.parse 会舍入低位，前端回传后导致“资源不存在”或更新错行。
 * 仅把字段名以 Id 结尾的 Long 字段序列化为字符串，分页 total、计数、金额、时间戳等仍保持 number。</p>
 */
@Configuration(proxyBeanMethods = false)
public class LongIdJacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longIdAsStringCustomizer() {
        return builder -> builder.postConfigurer(mapper -> mapper.registerModule(
                new SimpleModule("common-long-id-as-string")
                        .setSerializerModifier(new BeanSerializerModifier() {
                            @Override
                            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                                             BeanDescription beanDesc,
                                                                             List<BeanPropertyWriter> beanProperties) {
                                for (BeanPropertyWriter writer : beanProperties) {
                                    Class<?> raw = writer.getType().getRawClass();
                                    // 已用 @JsonSerialize(using = ToStringSerializer.class) 显式配置的字段跳过，
                                    // 否则 assignSerializer 会因重复赋值抛 IllegalStateException
                                    if (writer.getSerializer() != null) {
                                        continue;
                                    }
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
