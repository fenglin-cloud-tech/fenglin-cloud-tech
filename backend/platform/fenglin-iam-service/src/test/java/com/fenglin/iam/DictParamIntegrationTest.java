package com.fenglin.iam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 字典与参数集成测试：内嵌真实 PostgreSQL + 完整迁移，验证平台默认值继承、租户覆盖与 RLS 隔离。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DictParamIntegrationTest {

    private static final long TENANT_A = 1201L;
    private static final long TENANT_B = 1202L;
    private static final String JDBC_URL;

    static {
        EmbeddedPostgresSupport postgres = EmbeddedPostgresSupport.start();
        JDBC_URL = postgres.jdbcUrl();
        Flyway.configure()
                .dataSource(postgres.dataSource())
                .schemas("fenglin_hub")
                .defaultSchema("fenglin_hub")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> JDBC_URL + (JDBC_URL.contains("?") ? "&" : "?") + "currentSchema=fenglin_hub");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_app");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetData() throws Exception {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE fenglin_hub.hub_dict_item, fenglin_hub.hub_dict_type, fenglin_hub.hub_param
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_dict_type (id, code, name, scope, status, deleted)
                    VALUES (9101, 'biz_status', '业务状态', 'PLATFORM', 1, 0)
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_dict_item (id, tenant_id, dict_code, item_value, item_label, sort, status, deleted)
                    VALUES (9111, 0, 'biz_status', 'NEW', '新建', 1, 1, 0),
                           (9112, 0, 'biz_status', 'DONE', '完成', 2, 1, 0)
                    """);
            statement.execute("""
                    INSERT INTO fenglin_hub.hub_param (id, tenant_id, param_key, param_value, scope, value_type, deleted)
                    VALUES (9121, 0, 'file.max_size_mb', '100', 'PLATFORM', 'NUMBER', 0)
                    """);
        }
    }

    @Test
    void tenantOverridesPlatformDictItemAndFallsBackAfterDelete() {
        HttpResult initial = get("/api/hub/v1/admin/dicts/biz_status/items", TENANT_A, null);
        assertThat(initial.status()).isEqualTo(200);
        assertThat(initial.body().get("data")).hasSize(2);
        assertThat(labelOf(initial, "NEW")).isEqualTo("新建");
        assertThat(overriddenOf(initial, "NEW")).isFalse();

        HttpResult saved = exchange(HttpMethod.PUT, "/api/hub/v1/admin/dicts/biz_status/items/NEW",
                Map.of("item_label", "新建（租户）", "sort", 9), TENANT_A, "tenant:iam:dict:manage");
        assertThat(saved.status()).isEqualTo(200);
        assertThat(saved.body().get("data").get("overridden").asBoolean()).isTrue();

        HttpResult overridden = get("/api/hub/v1/admin/dicts/biz_status/items", TENANT_A, null);
        assertThat(labelOf(overridden, "NEW")).isEqualTo("新建（租户）");
        assertThat(overriddenOf(overridden, "NEW")).isTrue();
        assertThat(overriddenOf(overridden, "DONE")).isFalse();

        HttpResult tenantB = get("/api/hub/v1/admin/dicts/biz_status/items", TENANT_B, null);
        assertThat(labelOf(tenantB, "NEW")).isEqualTo("新建");

        HttpResult deleted = exchange(HttpMethod.DELETE, "/api/hub/v1/admin/dicts/biz_status/items/NEW",
                null, TENANT_A, "tenant:iam:dict:manage");
        assertThat(deleted.status()).isEqualTo(200);

        HttpResult fallback = get("/api/hub/v1/admin/dicts/biz_status/items", TENANT_A, null);
        assertThat(labelOf(fallback, "NEW")).isEqualTo("新建");

        HttpResult deletePlatform = exchange(HttpMethod.DELETE, "/api/hub/v1/admin/dicts/biz_status/items/DONE",
                null, TENANT_A, "tenant:iam:dict:manage");
        assertThat(deletePlatform.status()).isEqualTo(404);
    }

    @Test
    void dictWriteRequiresManagementPermission() {
        HttpResult forbidden = exchange(HttpMethod.PUT, "/api/hub/v1/admin/dicts/biz_status/items/NEW",
                Map.of("item_label", "x"), TENANT_A, null);
        assertThat(forbidden.status()).isEqualTo(403);
    }

    @Test
    void paramInheritsPlatformValueThenTenantOverrideWins() {
        HttpResult platform = get("/api/hub/v1/admin/params/file.max_size_mb", TENANT_A, null);
        assertThat(platform.status()).isEqualTo(200);
        assertThat(platform.body().get("data").get("param_value").asText()).isEqualTo("100");
        assertThat(platform.body().get("data").get("source").asText()).isEqualTo("PLATFORM");

        HttpResult saved = exchange(HttpMethod.PUT, "/api/hub/v1/admin/params/file.max_size_mb",
                Map.of("value", "200", "scope", "TENANT", "value_type", "NUMBER"),
                TENANT_A, "tenant:iam:param:manage");
        assertThat(saved.status()).isEqualTo(200);
        assertThat(saved.body().get("data").get("source").asText()).isEqualTo("TENANT");

        HttpResult effective = get("/api/hub/v1/admin/params/file.max_size_mb", TENANT_A, null);
        assertThat(effective.body().get("data").get("param_value").asText()).isEqualTo("200");

        HttpResult tenantB = get("/api/hub/v1/admin/params/file.max_size_mb", TENANT_B, null);
        assertThat(tenantB.body().get("data").get("param_value").asText()).isEqualTo("100");

        HttpResult list = get("/api/hub/v1/admin/params?scope=", TENANT_A, null);
        assertThat(list.body().get("data")).hasSize(1);

        HttpResult invalid = exchange(HttpMethod.PUT, "/api/hub/v1/admin/params/file.max_size_mb",
                Map.of("value", "abc", "value_type", "NUMBER"), TENANT_A, "tenant:iam:param:manage");
        assertThat(invalid.status()).isEqualTo(400);

        HttpResult deleted = exchange(HttpMethod.DELETE, "/api/hub/v1/admin/params/file.max_size_mb",
                null, TENANT_A, "tenant:iam:param:manage");
        assertThat(deleted.status()).isEqualTo(200);

        HttpResult fallback = get("/api/hub/v1/admin/params/file.max_size_mb", TENANT_A, null);
        assertThat(fallback.body().get("data").get("param_value").asText()).isEqualTo("100");
        assertThat(fallback.body().get("data").get("source").asText()).isEqualTo("PLATFORM");
    }

    @Test
    void dictTypesAreListedFromPlatformCatalog() {
        HttpResult response = get("/api/hub/v1/admin/dicts?scope=PLATFORM", TENANT_A, null);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("data")).hasSize(1);
        assertThat(response.body().get("data").get(0).get("code").asText()).isEqualTo("biz_status");
    }

    private String labelOf(HttpResult result, String itemValue) {
        for (JsonNode node : result.body().get("data")) {
            if (itemValue.equals(node.get("item_value").asText())) {
                return node.get("item_label").asText();
            }
        }
        return null;
    }

    private boolean overriddenOf(HttpResult result, String itemValue) {
        for (JsonNode node : result.body().get("data")) {
            if (itemValue.equals(node.get("item_value").asText())) {
                return node.get("overridden").asBoolean();
            }
        }
        return false;
    }

    private HttpHeaders headers(long tenantId, String permissions) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", String.valueOf(tenantId));
        if (permissions != null) {
            headers.set("X-Permissions", permissions);
        }
        return headers;
    }

    private HttpResult get(String path, long tenantId, String permissions) {
        return exchange(HttpMethod.GET, path, null, tenantId, permissions);
    }

    private HttpResult exchange(HttpMethod method, String path, Object body, long tenantId, String permissions) {
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + path, method,
                new HttpEntity<>(body, headers(tenantId, permissions)), String.class);
        return new HttpResult(response.getStatusCode().value(), read(response.getBody()));
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + body, e);
        }
    }

    private Connection ownerConnection() throws Exception {
        return DriverManager.getConnection(JDBC_URL, "postgres", "");
    }

    private record HttpResult(int status, JsonNode body) {
    }
}
