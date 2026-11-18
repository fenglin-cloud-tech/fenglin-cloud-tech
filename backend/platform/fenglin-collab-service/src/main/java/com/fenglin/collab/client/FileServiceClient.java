package com.fenglin.collab.client;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * 文件服务内部调用（COL-07）：图片密文按不透明对象上传，读取时取短期预签名地址后由云协代理输出。
 *
 * <p>只复用现有文件上传/下载接口（multipart + 预签名 URL），不新增文件服务端点，
 * 也不在文件服务里做加解密，避免影响平台其他域已存的明文对象。</p>
 */
@Component
public class FileServiceClient {

    private static final String UPLOAD_PERMISSION = "biz:file:upload";
    private static final String DOWNLOAD_PERMISSION = "biz:file:download";

    private final RestClient restClient;
    private final HttpClient httpClient;
    private final String baseUrl;

    public FileServiceClient(@Value("${fenglin.collab.file-service-url:http://localhost:18086}") String serviceUrl) {
        this.baseUrl = serviceUrl.endsWith("/") ? serviceUrl.substring(0, serviceUrl.length() - 1) : serviceUrl;
        this.restClient = RestClient.create();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * 上传密文对象，返回文件服务 file id。
     */
    public long upload(long tenantId, Long userId, String fileName, Path cipherFile, String docId) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new FileSystemResource(cipherFile))
                .filename(fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        try {
            Map<String, Object> response = restClient.post()
                    .uri(baseUrl + "/api/hub/v1/files?bizType=COLLAB_DOC_IMAGE&bizId={docId}&secLevel=4", docId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(headers -> applyIdentity(headers, tenantId, userId, UPLOAD_PERMISSION))
                    .body(builder.build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Object data = response == null ? null : response.get("data");
            if (data instanceof Map<?, ?> map) {
                Object id = map.get("file_id") != null ? map.get("file_id") : map.get("fileId");
                if (id != null) {
                    return Long.parseLong(String.valueOf(id));
                }
            }
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "文件服务未返回文件 ID");
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "图片写入文件服务失败: " + ex.getMessage());
        }
    }

    /**
     * 获取文件的短期预签名下载地址（文件服务仍按自己的策略签发，云协只用它取字节）。
     */
    public String downloadUrl(long tenantId, Long userId, long fileId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(baseUrl + "/api/hub/v1/files/" + fileId + "/download")
                    .headers(headers -> applyIdentity(headers, tenantId, userId, DOWNLOAD_PERMISSION))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Object data = response == null ? null : response.get("data");
            if (data instanceof Map<?, ?> map) {
                Object url = map.get("download_url") != null ? map.get("download_url") : map.get("downloadUrl");
                if (url == null) {
                    url = map.get("url");
                }
                if (url != null) {
                    return String.valueOf(url);
                }
            }
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "文件服务未返回下载地址");
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "图片读取失败: " + ex.getMessage());
        }
    }

    public InputStream openStream(String url) {
        try {
            HttpResponse<InputStream> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(30)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new BizException(CommonErrorCode.INTERNAL_ERROR,
                        "图片对象读取失败（HTTP " + response.statusCode() + "）");
            }
            return response.body();
        } catch (BizException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "图片对象读取失败: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "图片对象读取被中断");
        }
    }

    private void applyIdentity(org.springframework.http.HttpHeaders headers, long tenantId, Long userId,
                               String permission) {
        headers.set("X-Tenant-Id", String.valueOf(tenantId));
        if (userId != null) {
            headers.set("X-User-Id", String.valueOf(userId));
        }
        headers.set("X-Client-Type", "SYSTEM");
        headers.set("X-Permissions", permission);
    }
}
