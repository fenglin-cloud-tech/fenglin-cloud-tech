package com.fenglin.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

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

    @Test
    void serializesOversizedLongAsStringEvenWhenNameIsNotId() throws Exception {
        String json = mapper.writeValueAsString(new LargeValue(2120408424215543810L, 123L));

        assertThat(json).contains("\"fileSize\":\"2120408424215543810\"");
        assertThat(json).contains("\"count\":123");
    }

    @Test
    void serializesOversizedLongInsideCollectionAsString() throws Exception {
        String json = mapper.writeValueAsString(new IdCollection(List.of(2120408424215543810L, 7L)));

        assertThat(json).contains("\"items\":[\"2120408424215543810\",7]");
    }

    @Test
    void deserializesIdFromStringAndFromNumber() throws Exception {
        PlainId fromString = mapper.readValue("{\"id\":\"2120408424215543810\",\"total\":42}", PlainId.class);
        PlainId fromNumber = mapper.readValue("{\"id\":2120408424215543810,\"total\":42}", PlainId.class);

        assertThat(fromString.id()).isEqualTo(2120408424215543810L);
        assertThat(fromNumber.id()).isEqualTo(2120408424215543810L);
        assertThat(fromString.total()).isEqualTo(42L);
    }

    record PlainId(long id, long total) {
    }

    record AnnotatedId(@JsonSerialize(using = ToStringSerializer.class) long userId) {
    }

    record LargeValue(long fileSize, long count) {
    }

    record IdCollection(List<Long> items) {
    }
}
