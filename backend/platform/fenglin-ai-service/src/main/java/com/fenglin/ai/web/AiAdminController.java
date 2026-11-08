package com.fenglin.ai.web;

import com.fenglin.ai.service.AiConfigService;
import com.fenglin.ai.service.AiGovernanceService;
import com.fenglin.ai.web.dto.AiDtos.GrantRequest;
import com.fenglin.ai.web.dto.AiDtos.GrantResult;
import com.fenglin.ai.web.dto.AiDtos.MeteringResult;
import com.fenglin.ai.web.dto.AiDtos.ModeValidateResult;
import com.fenglin.ai.web.dto.AiDtos.ModelCreateRequest;
import com.fenglin.ai.web.dto.AiDtos.ModelUpdateRequest;
import com.fenglin.ai.web.dto.AiDtos.ModelView;
import com.fenglin.ai.web.dto.AiDtos.PromptCreateRequest;
import com.fenglin.ai.web.dto.AiDtos.PromptView;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.common.tenant.TenantContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class AiAdminController {

    private static final String MODEL_PERMISSION = "platform:ai:model:manage";
    private static final String PROMPT_PERMISSION = "tenant:ai:prompt:manage";
    private static final String GRANT_PERMISSION = "tenant:ai:grant:manage";

    private final AiConfigService configService;
    private final AiGovernanceService governanceService;

    public AiAdminController(AiConfigService configService, AiGovernanceService governanceService) {
        this.configService = configService;
        this.governanceService = governanceService;
    }

    /** HUB-API-1301 模型配置（API Key 加密存储，仅回显指纹）。 */
    @PostMapping("/api/hub/v1/admin/ai/models")
    public ApiResponse<ModelView> createModel(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody ModelCreateRequest request) {
        PermissionGuard.require(permissions, MODEL_PERMISSION);
        return ApiResponse.success(configService.createModel(request, operator(userId)));
    }

    /** HUB-API-1302 提示词管理（内容相同幂等返回当前版本）。 */
    @PostMapping("/api/hub/v1/admin/ai/prompts")
    public ApiResponse<PromptView> createPrompt(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody PromptCreateRequest request) {
        PermissionGuard.require(permissions, PROMPT_PERMISSION);
        return ApiResponse.success(configService.createPrompt(request, operator(userId)));
    }

    /** HUB-API-1306 能力授权配置（按 hostProduct+capability 幂等 upsert）。 */
    @PutMapping("/api/hub/v1/admin/ai/capability-grants")
    public ApiResponse<GrantResult> putGrants(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody GrantRequest request) {
        PermissionGuard.require(permissions, GRANT_PERMISSION);
        return ApiResponse.success(governanceService.putGrants(
                request == null ? null : request.items(), operator(userId)));
    }

    /** 扩展：模型列表（总纲未定义，最小可用语义）。 */
    @GetMapping("/api/hub/v1/admin/ai/models")
    public ApiResponse<PageResult<ModelView>> listModels(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String providerType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        PermissionGuard.require(permissions, MODEL_PERMISSION);
        return ApiResponse.success(configService.listModels(modelType, providerType, page, size));
    }

    /** 扩展：模型更新（api_key 为空表示保留原密钥）。 */
    @PutMapping("/api/hub/v1/admin/ai/models/{id}")
    public ApiResponse<ModelView> updateModel(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable long id,
            @RequestBody ModelUpdateRequest request) {
        PermissionGuard.require(permissions, MODEL_PERMISSION);
        return ApiResponse.success(configService.updateModel(id, request, operator(userId)));
    }

    /** 扩展：模型软删除。 */
    @DeleteMapping("/api/hub/v1/admin/ai/models/{id}")
    public ApiResponse<Void> deleteModel(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable long id) {
        PermissionGuard.require(permissions, MODEL_PERMISSION);
        configService.deleteModel(id, operator(userId));
        return ApiResponse.success(null);
    }

    /** 扩展：提示词列表（每个 code 取最新版本）。 */
    @GetMapping("/api/hub/v1/admin/ai/prompts")
    public ApiResponse<PageResult<PromptView>> listPrompts(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) String scene,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        PermissionGuard.require(permissions, PROMPT_PERMISSION);
        return ApiResponse.success(configService.listPrompts(scene, page, size));
    }

    /** 扩展：提示词更新（内容变化时追加新版本）。 */
    @PutMapping("/api/hub/v1/admin/ai/prompts/{code}")
    public ApiResponse<PromptView> updatePrompt(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable String code,
            @RequestBody PromptCreateRequest request) {
        PermissionGuard.require(permissions, PROMPT_PERMISSION);
        return ApiResponse.success(configService.updatePrompt(code, request, operator(userId)));
    }

    /** 扩展：提示词软删除（该 code 全部版本）。 */
    @DeleteMapping("/api/hub/v1/admin/ai/prompts/{code}")
    public ApiResponse<Void> deletePrompt(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable String code) {
        PermissionGuard.require(permissions, PROMPT_PERMISSION);
        configService.deletePrompt(code, operator(userId));
        return ApiResponse.success(null);
    }

    /** 扩展：授权列表（1306 仅定义写入）。 */
    @GetMapping("/api/hub/v1/admin/ai/capability-grants")
    public ApiResponse<GrantResult> listGrants(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) String hostProduct) {
        PermissionGuard.require(permissions, GRANT_PERMISSION);
        return ApiResponse.success(governanceService.listGrants(hostProduct));
    }

    /** 扩展：授权撤销（软删除）。 */
    @DeleteMapping("/api/hub/v1/admin/ai/capability-grants/{hostProduct}/{capability}")
    public ApiResponse<Void> deleteGrant(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable String hostProduct,
            @PathVariable String capability) {
        PermissionGuard.require(permissions, GRANT_PERMISSION);
        governanceService.deleteGrant(hostProduct, capability, operator(userId));
        return ApiResponse.success(null);
    }

    /** HUB-API-1307 AI 计量查询（文档未给权限码，按 ADMIN + 租户 RLS 收敛）。 */
    @GetMapping("/api/hub/v1/admin/ai/metering")
    public ApiResponse<MeteringResult> metering(
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String hostProduct,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(governanceService.metering(mode, hostProduct, from, to));
    }

    /** HUB-API-1308 双模式一致性校验（不一致返回 0000005）。 */
    @PostMapping("/api/hub/v1/admin/ai/mode:validate")
    public ApiResponse<ModeValidateResult> validateMode() {
        return ApiResponse.success(governanceService.validateMode());
    }

    private Long operator(Long userId) {
        if (userId != null) {
            return userId;
        }
        TenantContext.Snapshot context = TenantContext.get();
        return context == null ? null : context.userId();
    }
}
