package com.fenglin.finance.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.finance.service.AssetService;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.web.dto.FinanceDtos.AssetCardCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.AssetCardView;
import com.fenglin.finance.web.dto.FinanceDtos.AssetCategoryCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.AssetCategoryView;
import com.fenglin.finance.web.dto.FinanceDtos.DepreciationRunRequest;
import com.fenglin.finance.web.dto.FinanceDtos.DepreciationRunResult;
import com.fenglin.finance.web.dto.FinanceDtos.DisposeRequest;
import com.fenglin.finance.web.dto.FinanceDtos.DisposeResult;
import com.fenglin.finance.web.dto.FinanceDtos.ItAssetBorrowRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ItAssetSyncRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ItAssetView;
import com.fenglin.finance.web.dto.FinanceDtos.StocktakingCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.StocktakingDetail;
import com.fenglin.finance.web.dto.FinanceDtos.StocktakingItemRequest;
import com.fenglin.finance.web.dto.FinanceDtos.StocktakingView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 固定资产与 IT 资产接口。
 */
@RestController
@RequestMapping("/api/finance/v1/assets")
public class FinanceAssetController {

    private static final String CATEGORY = "tenant:finance:asset:category:manage";
    private static final String STOCKTAKING_APPROVE = "tenant:finance:asset:stocktaking:approve";
    private static final String IT_MANAGE = "tenant:finance:itasset:manage";
    private static final String ASSET_VIEW = "biz:finance:asset:view";
    private static final String ASSET_CREATE = "biz:finance:asset:create";
    private static final String DEPRECIATE = "biz:finance:asset:depreciate";
    private static final String DISPOSE = "biz:finance:asset:dispose";
    private static final String STOCKTAKING = "biz:finance:asset:stocktaking";
    private static final String IT_BORROW = "biz:finance:itasset:borrow";

    private final AssetService assetService;

    public FinanceAssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping("/categories")
    public ApiResponse<AssetCategoryView> createCategory(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody AssetCategoryCreateRequest request) {
        PermissionGuard.require(permissions, CATEGORY);
        return ApiResponse.success(assetService.createCategory(request));
    }

    @GetMapping("/categories")
    public ApiResponse<List<AssetCategoryView>> listCategories(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, ASSET_VIEW);
        return ApiResponse.success(assetService.listCategories());
    }

    @PostMapping("/cards")
    public ApiResponse<AssetCardView> createCard(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody AssetCardCreateRequest request) {
        PermissionGuard.require(permissions, ASSET_CREATE);
        return ApiResponse.success(assetService.createCard(request));
    }

    @GetMapping("/cards")
    public ApiResponse<List<AssetCardView>> listCards(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, ASSET_VIEW);
        return ApiResponse.success(assetService.listCards(status, FinanceContext.size(size),
                FinanceContext.offset(page, size)));
    }

    @PostMapping("/depreciation/run")
    public ApiResponse<DepreciationRunResult> runDepreciation(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody DepreciationRunRequest request) {
        PermissionGuard.require(permissions, DEPRECIATE);
        return ApiResponse.success(assetService.runDepreciation(request));
    }

    @PostMapping("/{assetId}/dispose")
    public ApiResponse<DisposeResult> dispose(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("assetId") long assetId, @RequestBody(required = false) DisposeRequest request) {
        PermissionGuard.require(permissions, DISPOSE);
        return ApiResponse.success(assetService.dispose(assetId, request));
    }

    @PostMapping("/stocktakings")
    public ApiResponse<StocktakingView> createStocktaking(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody StocktakingCreateRequest request) {
        PermissionGuard.require(permissions, STOCKTAKING);
        return ApiResponse.success(assetService.createStocktaking(request));
    }

    @GetMapping("/stocktakings")
    public ApiResponse<List<StocktakingView>> listStocktakings(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, ASSET_VIEW);
        return ApiResponse.success(assetService.listStocktakings(FinanceContext.size(size),
                FinanceContext.offset(page, size)));
    }

    @GetMapping("/stocktakings/{id}")
    public ApiResponse<StocktakingDetail> stocktakingDetail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, ASSET_VIEW);
        return ApiResponse.success(assetService.stocktakingDetail(id));
    }

    @PostMapping("/stocktakings/{id}/count")
    public ApiResponse<StocktakingDetail> countItem(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody StocktakingItemRequest request) {
        PermissionGuard.require(permissions, STOCKTAKING);
        return ApiResponse.success(assetService.countItem(id, request));
    }

    @PostMapping("/stocktakings/{id}/complete")
    public ApiResponse<StocktakingView> completeStocktaking(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, STOCKTAKING_APPROVE);
        return ApiResponse.success(assetService.completeStocktaking(id));
    }

    @GetMapping("/it")
    public ApiResponse<List<ItAssetView>> listItAssets(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, ASSET_VIEW);
        return ApiResponse.success(assetService.listItAssets(FinanceContext.size(size),
                FinanceContext.offset(page, size)));
    }

    @PostMapping("/it/{id}/borrow")
    public ApiResponse<ItAssetView> borrow(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody ItAssetBorrowRequest request) {
        PermissionGuard.require(permissions, IT_BORROW);
        return ApiResponse.success(assetService.borrowItAsset(id, request));
    }

    @PostMapping("/it/{id}/return")
    public ApiResponse<ItAssetView> returnAsset(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, IT_BORROW);
        return ApiResponse.success(assetService.returnItAsset(id));
    }

    @PostMapping("/it/{id}/sync")
    public ApiResponse<ItAssetView> sync(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody ItAssetSyncRequest request) {
        PermissionGuard.require(permissions, IT_MANAGE);
        return ApiResponse.success(assetService.syncItAsset(id, request));
    }
}
