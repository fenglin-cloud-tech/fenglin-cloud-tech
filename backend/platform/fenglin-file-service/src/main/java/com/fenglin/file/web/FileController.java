package com.fenglin.file.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.file.service.FileService;
import com.fenglin.file.web.dto.DownloadResult;
import com.fenglin.file.web.dto.FastCheckRequest;
import com.fenglin.file.web.dto.FastCheckResult;
import com.fenglin.file.web.dto.PreviewResult;
import com.fenglin.file.web.dto.UploadResult;
import com.fenglin.file.web.dto.VersionListResult;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FileController {

    private static final String UPLOAD_PERMISSION = "biz:file:upload";
    private static final String DOWNLOAD_PERMISSION = "biz:file:download";
    private static final String PREVIEW_PERMISSION = "biz:file:preview";

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/api/hub/v1/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResult> upload(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "chunk", required = false) MultipartFile chunk,
            @RequestParam(required = false) Integer chunkIndex,
            @RequestParam(required = false) Integer totalChunks,
            @RequestParam(required = false) String md5,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizId,
            @RequestParam(required = false) Integer secLevel) {
        PermissionGuard.require(permissions, UPLOAD_PERMISSION);
        MultipartFile part = file != null ? file : chunk;
        if (part == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "缺少文件分片: file/chunk");
        }
        return ApiResponse.success(fileService.upload(part, chunkIndex, totalChunks, md5, bizType, bizId, secLevel));
    }

    @PostMapping("/api/hub/v1/files:fast-check")
    public ApiResponse<FastCheckResult> fastCheck(@Valid @RequestBody FastCheckRequest request) {
        return ApiResponse.success(fileService.fastCheck(request.md5(), request.size(), request.name()));
    }

    @GetMapping("/api/hub/v1/files/{id}/download")
    public ApiResponse<DownloadResult> download(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestParam(required = false) Integer version) {
        PermissionGuard.require(permissions, DOWNLOAD_PERMISSION);
        return ApiResponse.success(fileService.download(id, version));
    }

    @GetMapping("/api/hub/v1/files/{id}/preview")
    public ApiResponse<PreviewResult> preview(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, PREVIEW_PERMISSION);
        return ApiResponse.success(fileService.preview(id));
    }

    @GetMapping("/api/hub/v1/files/{id}/versions")
    public ApiResponse<VersionListResult> versions(@PathVariable long id) {
        return ApiResponse.success(fileService.versions(id));
    }
}
