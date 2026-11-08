package com.fenglin.report.service;

import com.fenglin.common.core.BizException;
import com.fenglin.report.ReportErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * 复用文件服务：导出产物通过 HUB-API-701 POST /api/hub/v1/files 上传，返回 fileId。
 */
@Component
public class FileServiceClient {

    private final RestClient restClient;
    private final String baseUrl;

    public FileServiceClient(@Value("${fenglin.report.file-service-url:http://localhost:18086}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    public String upload(String filename, byte[] content, String contentType,
                         long tenantId, Long userId) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        parts.add("bizType", "REPORT_EXPORT");
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/api/hub/v1/files")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("X-Tenant-Id", String.valueOf(tenantId))
                    .header("X-Client-Type", "ADMIN")
                    .header("X-Permissions", "biz:file:upload")
                    .header("X-User-Id", userId == null ? "" : String.valueOf(userId))
                    .body(parts)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null || response.get("data") == null) {
                throw new BizException(ReportErrorCode.DEPENDENCY_UNAVAILABLE, "文件服务返回为空");
            }
            Map<?, ?> data = (Map<?, ?>) response.get("data");
            Object fileId = data.get("file_id");
            if (fileId == null) {
                throw new BizException(ReportErrorCode.DEPENDENCY_UNAVAILABLE, "文件服务未返回 file_id");
            }
            return String.valueOf(fileId);
        } catch (BizException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BizException(ReportErrorCode.DEPENDENCY_UNAVAILABLE, "文件服务不可用: " + ex.getMessage());
        }
    }

    public String downloadUrl(String fileId) {
        return baseUrl + "/api/hub/v1/files/" + fileId + "/download";
    }

    public String contentType(String format) {
        return switch (format) {
            case "EXCEL" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "PDF" -> "application/pdf";
            case "PNG" -> "image/png";
            default -> "text/csv;charset=UTF-8";
        };
    }
}
