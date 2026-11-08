package com.fenglin.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.test.EmbeddedPostgresSupport;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件中心集成测试：真实 PostgreSQL（全量迁移）+ 真实 MinIO 容器，覆盖上传/秒传/分片/下载/预览/版本/策略/配额/租户隔离与审计。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FileFlowIntegrationTest.AuditConfig.class)
class FileFlowIntegrationTest {

    private static final long TENANT_MAIN = 1401L;
    private static final long TENANT_OTHER = 1402L;
    private static final long TENANT_POLICY = 1403L;
    private static final long TENANT_QUOTA = 1404L;
    private static final long USER_ID = 5401L;
    private static final String BUCKET = "fenglin-files-test";
    private static final String MINIO_ENDPOINT = "http://localhost:9000";
    private static final String MINIO_ACCESS_KEY = "minioadmin";
    private static final String MINIO_SECRET_KEY = "minioadmin123";
    private static final String JDBC_URL;

    static final List<AuditEvent> AUDIT_EVENTS = new CopyOnWriteArrayList<>();
    private static final MinioClient MINIO = MinioClient.builder()
            .endpoint(MINIO_ENDPOINT)
            .credentials(MINIO_ACCESS_KEY, MINIO_SECRET_KEY)
            .build();

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
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> JDBC_URL + (JDBC_URL.contains("?") ? "&" : "?") + "currentSchema=fenglin_hub");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE fl_app");
        registry.add("fenglin.file.endpoint", () -> MINIO_ENDPOINT);
        registry.add("fenglin.file.access-key", () -> MINIO_ACCESS_KEY);
        registry.add("fenglin.file.secret-key", () -> MINIO_SECRET_KEY);
        registry.add("fenglin.file.bucket", () -> BUCKET);
        registry.add("fenglin.file.presign-minutes", () -> "15");
        registry.add("fenglin.audit.endpoint", () -> "http://127.0.0.1:1/internal/v1/audit/logs");
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
        AUDIT_EVENTS.clear();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE fenglin_hub.hub_file, fenglin_hub.hub_file_version");
            statement.execute("DELETE FROM fenglin_hub.hub_param WHERE tenant_id IN (1401, 1402, 1403, 1404)");
            for (long tenant : List.of(TENANT_MAIN, TENANT_OTHER, TENANT_POLICY, TENANT_QUOTA)) {
                statement.execute("INSERT INTO fenglin_hub.hub_user (id, tenant_id, username, real_name, status) "
                        + "VALUES (" + USER_ID + ", " + tenant + ", 'zhangfl', '张丰麟', 'ACTIVE') "
                        + "ON CONFLICT (id) DO NOTHING");
            }
        }
    }

    @Test
    void singleUploadThenFastCheckDownloadPreviewAndVersions() throws Exception {
        byte[] content = "丰麟云枢文件中心-单文件上传".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String md5 = md5Hex(content);

        HttpResult upload = upload(TENANT_MAIN, "report.pdf", content, md5, null, null, 1);
        assertThat(upload.status()).isEqualTo(200);
        JsonNode data = upload.body().get("data");
        assertThat(data.get("status").asText()).isEqualTo("AVAILABLE");
        long fileId = data.get("file_id").asLong();
        String fileNo = data.get("file_no").asText();
        assertThat(fileNo).startsWith("F");
        assertThat(data.get("object_key").asText()).contains(String.valueOf(TENANT_MAIN));

        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT status, virus_scanned, size, md5 FROM fenglin_hub.hub_file WHERE id = " + fileId)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("status")).isEqualTo("AVAILABLE");
            assertThat(rs.getInt("virus_scanned")).isZero();
            assertThat(rs.getLong("size")).isEqualTo(content.length);
            assertThat(rs.getString("md5")).isEqualTo(md5);
        }

        HttpResult fastCheck = postJson("/api/hub/v1/files:fast-check",
                Map.of("md5", md5, "size", content.length, "name", "report.pdf"), TENANT_MAIN);
        assertThat(fastCheck.status()).isEqualTo(200);
        assertThat(fastCheck.body().get("data").get("exists").asBoolean()).isTrue();
        assertThat(fastCheck.body().get("data").get("file_id").asLong()).isEqualTo(fileId);

        HttpResult download = get("/api/hub/v1/files/" + fileId + "/download", TENANT_MAIN,
                "biz:file:download");
        assertThat(download.status()).isEqualTo(200);
        String url = download.body().get("data").get("download_url").asText();
        assertThat(url).contains(BUCKET);
        assertThat(url).contains("X-Amz-Expires=900");
        assertThat(download.body().get("data").get("watermark").asBoolean()).isFalse();
        assertThat(fetch(url)).isEqualTo(content);

        HttpResult preview = get("/api/hub/v1/files/" + fileId + "/preview", TENANT_MAIN, "biz:file:preview");
        assertThat(preview.status()).isEqualTo(200);
        assertThat(preview.body().get("data").get("preview_type").asText()).isEqualTo("PDF");
        assertThat(preview.body().get("data").get("preview_url").asText()).contains(BUCKET);
        assertThat(preview.body().get("data").has("watermark_text")).isFalse();

        HttpResult versions = get("/api/hub/v1/files/" + fileId + "/versions", TENANT_MAIN, null);
        assertThat(versions.status()).isEqualTo(200);
        assertThat(versions.body().get("data").get("records")).hasSize(1);
        assertThat(versions.body().get("data").get("records").get(0).get("ver_no").asInt()).isEqualTo(1);

        assertThat(eventTypes()).contains("FILE_UPLOADED", "FILE_DOWNLOADED", "FILE_PREVIEWED");
    }

    @Test
    void chunkedUploadAssemblesObjectAndIgnoresDuplicateChunk() throws Exception {
        byte[] part1 = "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] part2 = "ABCDEFGHIJKLMN".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] whole = concat(part1, part2);
        String md5 = md5Hex(whole);

        HttpResult first = upload(TENANT_MAIN, "archive.txt", part1, md5, 0, 2, 1);
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().get("data").get("status").asText()).isEqualTo("UPLOADING");

        HttpResult retry = upload(TENANT_MAIN, "archive.txt", part1, md5, 0, 2, 1);
        assertThat(retry.body().get("data").get("status").asText()).isEqualTo("UPLOADING");

        HttpResult second = upload(TENANT_MAIN, "archive.txt", part2, md5, 1, 2, 1);
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.body().get("data").get("status").asText()).isEqualTo("AVAILABLE");
        long fileId = second.body().get("data").get("file_id").asLong();

        String objectKey;
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT object_key, size FROM fenglin_hub.hub_file WHERE id = " + fileId)) {
            rs.next();
            objectKey = rs.getString("object_key");
            assertThat(rs.getLong("size")).isEqualTo(whole.length);
        }
        try (var stream = MINIO.getObject(GetObjectArgs.builder()
                .bucket(BUCKET).object(objectKey).build())) {
            assertThat(stream.readAllBytes()).isEqualTo(whole);
        }
    }

    @Test
    void policyBlocksExecutableOversizeAndRequiresPermission() {
        HttpResult forbidden = exchangeJson(HttpMethod.PUT, "/api/hub/v1/admin/file/policies",
                Map.of("allowed_ext", List.of("txt"), "max_size_mb", 1), TENANT_POLICY, null);
        assertThat(forbidden.status()).isEqualTo(403);

        HttpResult updated = exchangeJson(HttpMethod.PUT, "/api/hub/v1/admin/file/policies",
                Map.of("allowed_ext", List.of("txt"),
                        "max_size_mb", 1,
                        "watermark", Map.of("enabled", true, "sec_level_from", 3),
                        "storage_quota_gb", 0), TENANT_POLICY, "tenant:file:policy:manage");
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body().get("data").get("allowed_ext").get(0).asText()).isEqualTo("txt");
        assertThat(updated.body().get("data").get("max_size_mb").asInt()).isEqualTo(1);

        HttpResult pdf = upload(TENANT_POLICY, "report.pdf", new byte[]{1, 2, 3}, null, null, null, 1);
        assertThat(pdf.status()).isEqualTo(400);
        assertThat(pdf.body().get("message").asText()).contains("0006002");

        byte[] big = new byte[1024 * 1024 + 1024];
        HttpResult oversize = upload(TENANT_POLICY, "big.txt", big, null, null, null, 1);
        assertThat(oversize.status()).isEqualTo(400);
        assertThat(oversize.body().get("message").asText()).contains("0006001");
    }

    @Test
    void storageQuotaIsEnforcedFromTenantParam() throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "postgres", "");
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO fenglin_hub.hub_param (id, tenant_id, param_key, param_value, scope, "
                    + "value_type, created_at, updated_at, deleted, version) VALUES (900001, " + TENANT_QUOTA
                    + ", 'file.policy.storage_quota_gb', '0.000001', 'TENANT', 'NUMBER', now(), now(), 0, 0)");
        }
        byte[] content = new byte[4096];
        HttpResult result = upload(TENANT_QUOTA, "note.txt", content, null, null, null, 1);
        assertThat(result.status()).isEqualTo(400);
        assertThat(result.body().get("message").asText()).contains("0000101");
    }

    @Test
    void watermarkIsForcedForHighSecurityLevel() {
        byte[] content = "密级文件".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HttpResult upload = upload(TENANT_MAIN, "secret.pdf", content, null, null, null, 3);
        assertThat(upload.status()).isEqualTo(200);
        long fileId = upload.body().get("data").get("file_id").asLong();

        HttpResult download = get("/api/hub/v1/files/" + fileId + "/download", TENANT_MAIN, "biz:file:download");
        assertThat(download.body().get("data").get("watermark").asBoolean()).isTrue();

        HttpResult preview = get("/api/hub/v1/files/" + fileId + "/preview", TENANT_MAIN, "biz:file:preview");
        String text = preview.body().get("data").get("watermark_text").asText();
        assertThat(text).contains("张丰麟");
        assertThat(text).contains("T" + TENANT_MAIN);
    }

    @Test
    void tenantIsolationAndMd5MismatchAreRejected() {
        byte[] content = "租户隔离".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HttpResult upload = upload(TENANT_MAIN, "isolation.txt", content, null, null, null, 1);
        long fileId = upload.body().get("data").get("file_id").asLong();

        HttpResult otherTenant = get("/api/hub/v1/files/" + fileId + "/download", TENANT_OTHER,
                "biz:file:download");
        assertThat(otherTenant.status()).isEqualTo(404);

        HttpResult mismatch = upload(TENANT_MAIN, "isolation.txt", content,
                md5Hex("different-content".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                null, null, 1);
        assertThat(mismatch.status()).isEqualTo(400);
        assertThat(mismatch.body().get("message").asText()).contains("MD5 校验失败");
    }

    private HttpResult upload(long tenantId, String filename, byte[] content, String md5,
                              Integer chunkIndex, Integer totalChunks, Integer secLevel) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        if (chunkIndex != null) {
            body.add("chunkIndex", String.valueOf(chunkIndex));
        }
        if (totalChunks != null) {
            body.add("totalChunks", String.valueOf(totalChunks));
        }
        if (md5 != null) {
            body.add("md5", md5);
        }
        if (secLevel != null) {
            body.add("secLevel", String.valueOf(secLevel));
        }
        HttpHeaders headers = headers(tenantId, USER_ID, "biz:file:upload");
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + "/api/hub/v1/files",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        return new HttpResult(response.getStatusCode().value(), read(response.getBody()));
    }

    private HttpResult get(String path, long tenantId, String permissions) {
        HttpHeaders headers = headers(tenantId, USER_ID, permissions);
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + path,
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return new HttpResult(response.getStatusCode().value(), read(response.getBody()));
    }

    private HttpResult postJson(String path, Object body, long tenantId) {
        return exchangeJson(HttpMethod.POST, path, body, tenantId, null);
    }

    private HttpResult exchangeJson(HttpMethod method, String path, Object body,
                                    long tenantId, String permissions) {
        HttpHeaders headers = headers(tenantId, USER_ID, permissions);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + path,
                method, new HttpEntity<>(body, headers), String.class);
        return new HttpResult(response.getStatusCode().value(), read(response.getBody()));
    }

    private HttpHeaders headers(long tenantId, long userId, String permissions) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", String.valueOf(tenantId));
        headers.set("X-User-Id", String.valueOf(userId));
        if (permissions != null) {
            headers.set("X-Permissions", permissions);
        }
        return headers;
    }

    private byte[] fetch(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + body, e);
        }
    }

    private List<String> eventTypes() {
        List<String> types = new ArrayList<>();
        for (AuditEvent event : AUDIT_EVENTS) {
            types.add(event.eventType());
        }
        return types;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] merged = new byte[first.length + second.length];
        System.arraycopy(first, 0, merged, 0, first.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        return merged;
    }

    private static String md5Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record HttpResult(int status, JsonNode body) {
    }

    @TestConfiguration
    static class AuditConfig {

        @Bean
        @Primary
        AuditSink recordingAuditSink() {
            return AUDIT_EVENTS::add;
        }
    }
}
