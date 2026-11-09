package com.fenglin.crm.config;

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
 * CRM 服务 ID 序列化配置。
 *
 * <p>CRM 主键与租户外键均为雪花 ID（19 位），超出 JavaScript Number 的安全整数范围
 * （2^53-1）。若按 JSON number 输出，前端 JSON.parse 后低位数会被舍入，导致后续
 * 按 id 的分配、跟进、转客户等请求命中“记录不存在”。
 * 这里仅把以 Id 结尾的 Long 字段序列化为字符串，分页 total 等计数字段仍保持 number。</p>
 */
@Configuration
public class CrmJacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer crmLongIdAsStringCustomizer() {
        return builder -> builder.postConfigurer(mapper -> mapper.registerModule(
                new SimpleModule("crm-long-id-as-string")
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
