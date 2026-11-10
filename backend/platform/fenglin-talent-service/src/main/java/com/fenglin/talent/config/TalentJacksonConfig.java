package com.fenglin.talent.config;

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
 * 云才服务 ID 序列化配置。
 *
 * <p>组织、员工、单据等主键为雪花 ID（19 位），超出 JavaScript Number 的安全整数范围。
 * 若按 JSON number 输出，前端 JSON.parse 后低位数被舍入，详情/更新会命中“资源不存在”或更新错行。
 * 仅把以 Id 结尾的 Long 字段序列化为字符串，分页 total、计数、时间戳等字段仍保持 number。</p>
 */
@Configuration
public class TalentJacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer talentLongIdAsStringCustomizer() {
        return builder -> builder.postConfigurer(mapper -> mapper.registerModule(
                new SimpleModule("talent-long-id-as-string")
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
