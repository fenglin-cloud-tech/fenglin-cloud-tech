package com.fenglin.finance.support;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额序列化：04_丰麟云财_05 接口文档约定金额/比率以字符串返回，不使用浮点数。
 */
@JsonComponent
public class BigDecimalStringSerializer extends JsonSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider serializers)
            throws IOException {
        generator.writeString(value.setScale(4, RoundingMode.HALF_UP).toPlainString());
    }
}
