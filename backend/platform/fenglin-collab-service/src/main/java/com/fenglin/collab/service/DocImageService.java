package com.fenglin.collab.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.collab.CollabErrorCode;
import com.fenglin.collab.client.FileServiceClient;
import com.fenglin.collab.support.CollabAudit;
import com.fenglin.collab.support.CollabContext;
import com.fenglin.collab.support.ImageAssetTokenService;
import com.fenglin.collab.support.ImageAssetTokenService.AssetToken;
import com.fenglin.collab.support.ImageCrypto;
import com.fenglin.collab.support.ImageProcessor;
import com.fenglin.collab.support.ImageProcessor.ProcessedImage;
import com.fenglin.collab.support.Rows;
import com.fenglin.collab.web.dto.CollabDtos.DocImageUploadView;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 文档图片主链（COL-07）：上传 → 剥 EXIF → 分块加密 → 文件服务存密文 → 元数据落库；
 * 读取时校验短期令牌与文件归属，由本服务解密后代理输出，不把 MinIO 直链暴露给浏览器。
 */
@Service
public class DocImageService {

    private static final Logger log = LoggerFactory.getLogger(DocImageService.class);
    private static final String AUTH_IMAGE_PATH = "/api/collab/v1/docs/images/";

    private final DocImageStore imageStore;
    private final ImageProcessor processor;
    private final ImageCrypto imageCrypto;
    private final FileServiceClient fileClient;
    private final ImageAssetTokenService tokenService;
    private final CollabAudit audit;
    private final CollabSettingService settingService;

    public DocImageService(DocImageStore imageStore, ImageProcessor processor, ImageCrypto imageCrypto,
                           FileServiceClient fileClient, ImageAssetTokenService tokenService,
                           CollabAudit audit, CollabSettingService settingService) {
        this.imageStore = imageStore;
        this.processor = processor;
        this.imageCrypto = imageCrypto;
        this.fileClient = fileClient;
        this.tokenService = tokenService;
        this.audit = audit;
        this.settingService = settingService;
    }

    public DocImageUploadView upload(String docId, MultipartFile file) {
        long tenantId = CollabContext.tenantId();
        long userId = CollabContext.userId();
        if (file == null || file.isEmpty()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "图片不能为空");
        }
        if (file.getSize() > ImageProcessor.MAX_BYTES) {
            throw new BizException(CollabErrorCode.PARAM_INVALID,
                    "图片超过 10 MB 上限（当前 " + file.getSize() + " 字节，上限 "
                            + ImageProcessor.MAX_BYTES + " 字节）");
        }
        imageStore.requireEdit(tenantId, userId, docId);
        Path plain = null;
        Path cipher = null;
        try {
            ProcessedImage image = processor.process(file.getInputStream(), file.getSize());
            plain = image.file();
            cipher = Files.createTempFile("collab-image-cipher-", ".bin");
            try (InputStream input = Files.newInputStream(plain);
                 OutputStream out = Files.newOutputStream(cipher)) {
                imageCrypto.encrypt(tenantId, input, image.size(), out);
            }
            long fileId = fileClient.upload(tenantId, userId, "doc-image" + extension(image.mime()), cipher, docId);
            imageStore.insert(IdWorker.getId(), tenantId, docId, fileId, userId, image.mime(), image.size(),
                    image.sha256(), image.width(), image.height());
            audit.publish("COLLAB_DOC_IMAGE_UPLOADED", "CREATE", "collab_doc_image", String.valueOf(fileId), "LOW");
            String token = tokenService.issue(tenantId, docId, fileId, tokenService.userSubject(userId));
            return new DocImageUploadView(String.valueOf(fileId), docId, image.mime(), image.size(),
                    image.width(), image.height(), image.sha256(),
                    AUTH_IMAGE_PATH + fileId + "?asset_token=" + token);
        } catch (IOException ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "图片处理失败: " + ex.getMessage());
        } finally {
            deleteQuietly(plain);
            deleteQuietly(cipher);
        }
    }

    /** 登录场景：令牌主体必须是用户，且文档与文件必须匹配。 */
    public void openAuthenticated(AssetToken token, long fileId, HttpServletResponse response) {
        if (!token.subject().startsWith("u")) {
            throw new BizException(CollabErrorCode.DOC_NO_PERMISSION, "图片访问令牌主体不匹配");
        }
        Map<String, Object> image = imageStore.find(token.tenantId(), fileId);
        if (image == null || !token.docId().equals(Rows.str(image, "docId"))) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "图片不属于该文档");
        }
        stream(token.tenantId(), image, response);
    }

    /** 分享场景：令牌主体必须与链接令牌哈希一致，且链接未过期、文件属于该文档。 */
    public void openShared(AssetToken token, String shareToken, long fileId, HttpServletResponse response) {
        if (!tokenService.linkSubject(shareToken).equals(token.subject())) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "图片访问令牌与分享链接不匹配");
        }
        Map<String, Object> doc;
        try {
            doc = imageStore.requireValidLink(token.tenantId(), shareToken);
        } catch (BizException ex) {
            // 图片代理对外统一用 403 表达“链接已失效”，避免把 410 语义暴露给 <img>
            if (ex.getErrorCode() == CollabErrorCode.DOC_LINK_EXPIRED) {
                throw new BizException(CollabErrorCode.NO_PERMISSION, "分享链接已过期");
            }
            throw ex;
        }
        if ("CONFIDENTIAL".equals(Rows.str(doc, "secretLevel"))
                && settingService.docPolicy().confidentialNoLink()) {
            throw new BizException(CollabErrorCode.DOC_CONFIDENTIAL_FORBIDDEN);
        }
        Map<String, Object> image = imageStore.find(token.tenantId(), fileId);
        if (image == null || !token.docId().equals(Rows.str(doc, "docId"))
                || !Rows.str(doc, "docId").equals(Rows.str(image, "docId"))) {
            throw new BizException(CollabErrorCode.NO_PERMISSION, "图片不属于该文档");
        }
        stream(token.tenantId(), image, response);
    }

    private void stream(long tenantId, Map<String, Object> image, HttpServletResponse response) {
        if (!"AVAILABLE".equals(Rows.str(image, "status"))) {
            throw new BizException(CollabErrorCode.RESOURCE_NOT_FOUND, "图片不可用");
        }
        long fileId = Rows.lngVal(image, "fileId");
        long size = Rows.lngVal(image, "sizeBytes");
        String url = fileClient.downloadUrl(tenantId, null, fileId);
        try (InputStream raw = fileClient.openStream(url)) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(Rows.str(image, "mime"));
            response.setContentLengthLong(size);
            response.setHeader("Cache-Control", "private, max-age=300");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", "inline");
            imageCrypto.decrypt(tenantId, raw, response.getOutputStream(), size);
        } catch (IOException ex) {
            if (response.isCommitted()) {
                log.warn("图片流式输出中断 file_id={} 原因={}", fileId, ex.getMessage());
                return;
            }
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "图片输出失败: " + ex.getMessage());
        }
    }

    private String extension(String mime) {
        return switch (mime == null ? "" : mime) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时文件清理失败不影响主流程
        }
    }
}
