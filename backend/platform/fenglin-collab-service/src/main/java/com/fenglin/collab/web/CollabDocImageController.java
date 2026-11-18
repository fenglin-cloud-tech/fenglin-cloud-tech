package com.fenglin.collab.web;

import com.fenglin.collab.service.DocImageService;
import com.fenglin.collab.support.ImageAssetTokenService;
import com.fenglin.collab.support.ImageAssetTokenService.AssetToken;
import com.fenglin.collab.support.TenantScope;
import com.fenglin.collab.web.dto.CollabDtos.DocImageUploadView;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.BizException;
import com.fenglin.common.security.PermissionGuard;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档图片接口（COL-07）：
 * <ul>
 *   <li>上传：需要文档 EDIT 权限；</li>
 *   <li>登录读图：使用 5 分钟 asset_token，主体为登录用户；</li>
 *   <li>分享读图：额外校验分享链接有效、文件属于该文档，主体为链接令牌哈希；</li>
 *   <li>两个读图端点都经服务端解密代理输出，不返回 MinIO 直链。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/collab/v1/docs")
public class CollabDocImageController {

    private static final String EDIT = "biz:collab:doc:edit";

    private final DocImageService imageService;
    private final ImageAssetTokenService tokenService;

    public CollabDocImageController(DocImageService imageService, ImageAssetTokenService tokenService) {
        this.imageService = imageService;
        this.tokenService = tokenService;
    }

    @PostMapping(value = "/{docId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocImageUploadView> upload(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("docId") String docId,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        PermissionGuard.require(permissions, EDIT);
        return ApiResponse.success(imageService.upload(docId, file));
    }

    @GetMapping("/images/{fileId}")
    public void image(@PathVariable("fileId") long fileId,
                      @RequestParam(value = "asset_token", required = false) String assetToken,
                      HttpServletResponse response) {
        AssetToken token = tokenService.verify(assetToken);
        TenantScope.call(token.tenantId(), () -> {
            imageService.openAuthenticated(token, fileId, response);
            return null;
        });
    }

    @GetMapping("/shared/{token}/images/{fileId}")
    public void sharedImage(@PathVariable("token") String token,
                            @PathVariable("fileId") long fileId,
                            @RequestParam(value = "asset_token", required = false) String assetToken,
                            HttpServletResponse response) {
        AssetToken asset = tokenService.verify(assetToken);
        Long tenantId = TenantScope.tenantOfLinkToken(token);
        if (tenantId == null || tenantId != asset.tenantId()) {
            throw new BizException(com.fenglin.collab.CollabErrorCode.NO_PERMISSION, "分享图片令牌与链接不匹配");
        }
        TenantScope.call(tenantId, () -> {
            imageService.openShared(asset, token, fileId, response);
            return null;
        });
    }
}
