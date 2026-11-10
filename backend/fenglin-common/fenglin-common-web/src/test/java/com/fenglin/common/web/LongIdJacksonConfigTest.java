package com.fenglin.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class LongIdJacksonConfigTest {

    private final ObjectMapper mapper = buildMapper();

    private static ObjectMapper buildMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new LongIdJacksonConfig().longIdAsStringCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void serializesIdFieldsAsStringsButKeepsCountersNumeric() throws Exception {
        String json = mapper.writeValueAsString(new PlainId(9001L, 42L));

        assertThat(json).contains("\"id\":\"9001\"").contains("\"total\":42");
    }

    @Test
    void keepsFieldsWithExplicitToStringSerializerWorking() throws Exception {
        String json = mapper.writeValueAsString(new AnnotatedId(9101L));

        assertThat(json).contains("\"userId\":\"9101\"");
    }

    record PlainId(long id, long total) {
    }

    record AnnotatedId(@JsonSerialize(using = ToStringSerializer.class) long userId) {
    }
}
