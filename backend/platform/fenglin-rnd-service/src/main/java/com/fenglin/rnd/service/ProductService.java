package com.fenglin.rnd.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.rnd.RndErrorCode;
import com.fenglin.rnd.mapper.CommonsMapper;
import com.fenglin.rnd.mapper.ProductMapper;
import com.fenglin.rnd.support.RndAudit;
import com.fenglin.rnd.support.RndContext;
import com.fenglin.rnd.support.Rows;
import com.fenglin.rnd.web.dto.RndDtos.MemberCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.MemberView;
import com.fenglin.rnd.web.dto.RndDtos.ProductCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.ProductView;
import com.fenglin.rnd.web.dto.RndDtos.StatusView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * E1 产品与成员：产品创建时落库默认需求状态方案，负责人自动成为 owner 成员。
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductMapper productMapper;
    private final CommonsMapper commonsMapper;
    private final RndAudit audit;

    public ProductService(ProductMapper productMapper, CommonsMapper commonsMapper, RndAudit audit) {
        this.productMapper = productMapper;
        this.commonsMapper = commonsMapper;
        this.audit = audit;
    }

    @Transactional
    public ProductView create(ProductCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        String code = RndContext.requireText(request.productCode(), "productCode");
        String name = RndContext.requireText(request.name(), "name");
        if (productMapper.countProductCode(tenantId, code) > 0) {
            throw new BizException(RndErrorCode.PRODUCT_CODE_DUPLICATED);
        }
        long id = IdWorker.getId();
        long ownerId = request.ownerId() == null ? userId : request.ownerId();
        productMapper.insertProduct(id, tenantId, code, name, RndContext.text(request.productLine()), ownerId,
                request.visibility() == null ? "private" : request.visibility(),
                request.sprintCycleDays() == null ? 14 : request.sprintCycleDays(),
                RndContext.text(request.description()), userId);
        commonsMapper.insertDefaultRequirementStatus(IdWorker.getId(), tenantId, id, userId);
        productMapper.insertMember(IdWorker.getId(), tenantId, id, ownerId, "owner", "all", userId);
        audit.publish("PRODUCT_CREATED", "create", "devops_product", id, "medium");
        return get(id);
    }

    public ProductView get(long id) {
        long tenantId = RndContext.tenantId();
        Map<String, Object> row = productMapper.findProduct(tenantId, id);
        if (row == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return toView(row);
    }

    public List<ProductView> list(String status, Integer page, Integer size) {
        return productMapper.listProducts(RndContext.tenantId(), RndContext.text(status),
                        RndContext.size(size), RndContext.offset(page, size))
                .stream().map(ProductService::toView).toList();
    }

    @Transactional
    public MemberView addMember(long productId, MemberCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        requireProduct(tenantId, productId);
        long memberUserId = request.userId() == null ? userId : request.userId();
        if (productMapper.countMember(tenantId, productId, memberUserId) > 0) {
            throw new BizException(RndErrorCode.MEMBER_DUPLICATED);
        }
        long id = IdWorker.getId();
        productMapper.insertMember(id, tenantId, productId, memberUserId,
                request.roleCode() == null ? "developer" : request.roleCode(),
                request.dataScope() == null ? "self" : request.dataScope(), userId);
        audit.publish("DEVOPS_MEMBER_ADDED", "create", "devops_product_member", id, "low");
        return listMembers(productId).stream().filter(m -> m.id() == id).findFirst()
                .orElseThrow(() -> new BizException(RndErrorCode.RESOURCE_NOT_FOUND));
    }

    public List<MemberView> listMembers(long productId) {
        long tenantId = RndContext.tenantId();
        requireProduct(tenantId, productId);
        return productMapper.listMembers(tenantId, productId).stream()
                .map(row -> new MemberView(Rows.lng(row, "id"), Rows.lng(row, "productId"), Rows.lng(row, "userId"),
                        Rows.str(row, "roleCode"), Rows.str(row, "dataScope"), Rows.str(row, "createdAt")))
                .toList();
    }

    public List<StatusView> listStatuses(long productId) {
        long tenantId = RndContext.tenantId();
        requireProduct(tenantId, productId);
        return productMapper.listStatuses(tenantId, productId).stream()
                .map(row -> new StatusView(Rows.lng(row, "id"), Rows.str(row, "statusCode"),
                        Rows.str(row, "statusName"), Rows.str(row, "category"), Rows.str(row, "color"),
                        Rows.intg(row, "sortNo")))
                .toList();
    }

    public void requireProduct(long tenantId, long productId) {
        if (productMapper.countProduct(tenantId, productId) == 0) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    public static ProductView toView(Map<String, Object> row) {
        return new ProductView(Rows.lng(row, "id"), Rows.str(row, "productCode"), Rows.str(row, "name"),
                Rows.str(row, "productLine"), Rows.lng(row, "ownerId"), Rows.str(row, "visibility"),
                Rows.str(row, "status"), Rows.intg(row, "sprintCycleDays"), Rows.str(row, "description"),
                Rows.str(row, "createdAt"));
    }
}
