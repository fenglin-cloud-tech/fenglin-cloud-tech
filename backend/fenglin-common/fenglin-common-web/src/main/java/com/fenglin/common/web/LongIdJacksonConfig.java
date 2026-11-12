package com.fenglin.common.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;

/**
 * 平台统一的 Long 型序列化策略。
 *
 * <p>策略分两层：</p>
 * <ol>
 *   <li>字段名以 id 结尾的 Long 字段一律输出字符串（保持既有契约，雪花 ID 默认走这里）；</li>
 *   <li>其余 Long/long 字段按值判断：绝对值超过 JavaScript 安全整数（2^53-1）时输出字符串，
 *       否则仍输出数字。这样分页 total、count、明细行数等统计字段保持 number，前端可直接加减；
 *       而长度、字节数等可能超限的数值也不会被浏览器截断。</li>
 * </ol>
 *
 * <p>反序列化侧不做限制：Jackson 默认即可把 JSON 字符串或数字解析为 Long，
 * 因此 ID 以字符串传入、计数以数字传入都能正常接收。</p>
 */
@Configuration(proxyBeanMethods = false)
public class LongIdJacksonConfig {

    /** JavaScript Number.MAX_SAFE_INTEGER。 */
    static final long MAX_SAFE_INTEGER = 9007199254740991L;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longIdAsStringCustomizer() {
        return builder -> builder.postConfigurer(mapper -> {
            SimpleModule module = new SimpleModule("common-long-as-string");
            module.addSerializer(Long.class, SafeLongSerializer.INSTANCE);
            module.addSerializer(Long.TYPE, SafeLongSerializer.INSTANCE);
            module.setSerializerModifier(new BeanSerializerModifier() {
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
            });
            mapper.registerModule(module);
        });
    }

    /**
     * 超出 JS 安全整数范围时序列化为字符串，否则保持数字。
     */
    static final class SafeLongSerializer extends StdSerializer<Long> {

        static final SafeLongSerializer INSTANCE = new SafeLongSerializer();

        private SafeLongSerializer() {
            super(Long.class);
        }

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            if (value > MAX_SAFE_INTEGER || value < -MAX_SAFE_INTEGER) {
                gen.writeString(value.toString());
            } else {
                gen.writeNumber(value);
            }
        }
    }
}
