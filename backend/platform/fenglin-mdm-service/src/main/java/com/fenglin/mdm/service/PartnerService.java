package com.fenglin.mdm.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.mdm.MdmErrorCode;
import com.fenglin.mdm.mapper.PartnerMapper;
import com.fenglin.mdm.support.MdmContext;
import com.fenglin.mdm.support.Rows;
import com.fenglin.mdm.web.dto.MdmDtos.AliasView;
import com.fenglin.mdm.web.dto.MdmDtos.MergeRequest;
import com.fenglin.mdm.web.dto.MdmDtos.MergeView;
import com.fenglin.mdm.web.dto.MdmDtos.PartnerCandidate;
import com.fenglin.mdm.web.dto.MdmDtos.PartnerCreateRequest;
import com.fenglin.mdm.web.dto.MdmDtos.PartnerCreateResult;
import com.fenglin.mdm.web.dto.MdmDtos.PartnerView;
import com.fenglin.mdm.web.dto.MdmDtos.ResolveView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 往来单位主数据服务：创建判重、别名、合并与撤销。
 *
 * 引用完整性：三表之间及与业务域之间不建外键，由本服务保证；业务域只存 partner_id，
 * 合并/撤销只置标记、不改业务外键；读取一律走 resolvePartner。
 */
@Service
@Transactional(readOnly = true)
public class PartnerService {

    private static final int MAX_MERGE_DEPTH = 8;

    private final PartnerMapper partnerMapper;

    public PartnerService(PartnerMapper partnerMapper) {
        this.partnerMapper = partnerMapper;
    }

    @Transactional
    public PartnerCreateResult create(PartnerCreateRequest request) {
        long tenantId = MdmContext.tenantId();
        long userId = MdmContext.userId();
        if (request == null || isBlank(request.name()) || isBlank(request.sourceDomain())
                || isBlank(request.sourceRef())) {
            throw new BizException(MdmErrorCode.PARAM_INVALID);
        }
        String normalized = PartnerNameNormalizer.normalize(request.name());
        if (normalized.isEmpty()) {
            throw new BizException(MdmErrorCode.PARAM_INVALID);
        }
        String dedupKey = PartnerNameNormalizer.dedupKey(tenantId, normalized);
        Map<String, Object> existing = partnerMapper.findByDedup(tenantId, dedupKey);
        if (existing != null) {
            return duplicateResult(tenantId, existing);
        }
        if (!isBlank(request.unifiedCreditCode())) {
            Map<String, Object> credit = partnerMapper.findByCreditCode(tenantId,
                    request.unifiedCreditCode().trim());
            if (credit != null) {
                return duplicateResult(tenantId, credit);
            }
        }
        List<PartnerCandidate> candidates = candidates(tenantId, 0L, normalized);
        long id = IdWorker.getId();
        String partnerType = isBlank(request.partnerType()) ? "BOTH" : request.partnerType();
        for (int attempt = 0; attempt < 3; attempt++) {
            String partnerCode = nextCode(tenantId, attempt);
            try {
                partnerMapper.insertPartner(id, tenantId, partnerCode, request.name().trim(),
                        request.shortName(), trimToNull(request.unifiedCreditCode()), request.legalRep(),
                        request.contactName(), request.contactPhone(), request.contactEmail(),
                        partnerType, request.sourceDomain().trim(), request.sourceRef().trim(),
                        normalized, dedupKey, request.remark(), userId);
                return new PartnerCreateResult(view(partnerMapper.findPartner(tenantId, id)), false,
                        candidates);
            } catch (DuplicateKeyException e) {
                Map<String, Object> raced = partnerMapper.findByDedup(tenantId, dedupKey);
                if (raced != null) {
                    return duplicateResult(tenantId, raced);
                }
                if (attempt == 2) {
                    throw new BizException(MdmErrorCode.STATE_CONFLICT);
                }
            }
        }
        throw new BizException(MdmErrorCode.STATE_CONFLICT);
    }

    public List<PartnerView> list(String keyword, String status, int limit, int offset) {
        long tenantId = MdmContext.tenantId();
        String normalizedKeyword = isBlank(keyword) ? null : PartnerNameNormalizer.normalize(keyword);
        List<PartnerView> views = new ArrayList<>();
        for (Map<String, Object> row : partnerMapper.listPartners(tenantId, trimToNull(keyword),
                normalizedKeyword, trimToNull(status), limit, offset)) {
            views.add(view(row));
        }
        return views;
    }

    public PartnerView get(long id) {
        long tenantId = MdmContext.tenantId();
        return view(requirePartner(tenantId, id));
    }

    public PartnerView disable(long id) {
        return changeStatus(id, "DISABLED");
    }

    public PartnerView enable(long id) {
        return changeStatus(id, "ENABLED");
    }

    @Transactional
    public MergeView merge(MergeRequest request) {
        long tenantId = MdmContext.tenantId();
        long userId = MdmContext.userId();
        if (request == null || request.sourceId() == null || request.targetId() == null) {
            throw new BizException(MdmErrorCode.PARAM_INVALID);
        }
        long sourceId = request.sourceId();
        long targetId = request.targetId();
        if (sourceId == targetId) {
            throw new BizException(MdmErrorCode.MERGE_SELF);
        }
        Map<String, Object> source = requirePartner(tenantId, sourceId);
        Map<String, Object> target = requirePartner(tenantId, targetId);
        assertNoCycle(tenantId, sourceId, targetId);
        if ("MERGED".equals(Rows.str(source, "status")) || "MERGED".equals(Rows.str(target, "status"))) {
            throw new BizException(MdmErrorCode.STATE_CONFLICT);
        }
        long mergeId = IdWorker.getId();
        partnerMapper.insertMerge(mergeId, tenantId, sourceId, targetId, request.reason(), userId);
        if (partnerMapper.markMerged(tenantId, sourceId, targetId, userId) == 0) {
            throw new BizException(MdmErrorCode.STATE_CONFLICT);
        }
        insertAlias(tenantId, targetId, "NAME", Rows.str(source, "name"),
                Rows.str(source, "normalizedName"), mergeId, userId);
        insertAlias(tenantId, targetId, "CODE", Rows.str(source, "partnerCode"), null, mergeId, userId);
        if (!isBlank(Rows.str(source, "unifiedCreditCode"))) {
            insertAlias(tenantId, targetId, "CREDIT_CODE", Rows.str(source, "unifiedCreditCode"), null,
                    mergeId, userId);
        }
        return mergeView(partnerMapper.findMerge(tenantId, mergeId));
    }

    @Transactional
    public MergeView revoke(long mergeId) {
        long tenantId = MdmContext.tenantId();
        long userId = MdmContext.userId();
        Map<String, Object> merge = partnerMapper.findMerge(tenantId, mergeId);
        if (merge == null || !"ACTIVE".equals(Rows.str(merge, "status"))) {
            throw new BizException(MdmErrorCode.RESOURCE_NOT_FOUND);
        }
        if (partnerMapper.revokeMerge(tenantId, mergeId, userId) == 0) {
            throw new BizException(MdmErrorCode.STATE_CONFLICT);
        }
        long sourceId = Rows.lngVal(merge, "sourcePartnerId");
        if (partnerMapper.restoreMerged(tenantId, sourceId, userId) == 0) {
            throw new BizException(MdmErrorCode.STATE_CONFLICT);
        }
        partnerMapper.deactivateAliases(tenantId, mergeId, userId);
        return mergeView(partnerMapper.findMerge(tenantId, mergeId));
    }

    public ResolveView resolve(long id) {
        long tenantId = MdmContext.tenantId();
        long cursor = id;
        boolean merged = false;
        for (int depth = 0; depth < MAX_MERGE_DEPTH; depth++) {
            Map<String, Object> row = requirePartner(tenantId, cursor);
            if (!"MERGED".equals(Rows.str(row, "status"))) {
                return new ResolveView(id, cursor, Rows.str(row, "partnerCode"), Rows.str(row, "name"),
                        merged);
            }
            Long next = Rows.lng(row, "mergedInto");
            if (next == null) {
                throw new BizException(MdmErrorCode.STATE_CONFLICT);
            }
            cursor = next;
            merged = true;
        }
        throw new BizException(MdmErrorCode.MERGE_CYCLE);
    }

    public List<AliasView> aliases(long id) {
        long tenantId = MdmContext.tenantId();
        requirePartner(tenantId, id);
        List<AliasView> views = new ArrayList<>();
        for (Map<String, Object> row : partnerMapper.listAliases(tenantId, id)) {
            views.add(new AliasView(Rows.lngVal(row, "id"), Rows.str(row, "aliasType"),
                    Rows.str(row, "aliasValue"), Rows.intVal(row, "active") == 1));
        }
        return views;
    }

    private PartnerView changeStatus(long id, String status) {
        long tenantId = MdmContext.tenantId();
        requirePartner(tenantId, id);
        if (partnerMapper.updateStatus(tenantId, id, status, MdmContext.userId()) == 0) {
            throw new BizException(MdmErrorCode.STATE_CONFLICT);
        }
        return view(partnerMapper.findPartner(tenantId, id));
    }

    private void assertNoCycle(long tenantId, long sourceId, long targetId) {
        long cursor = targetId;
        for (int depth = 0; depth < MAX_MERGE_DEPTH; depth++) {
            if (cursor == sourceId) {
                throw new BizException(MdmErrorCode.MERGE_CYCLE);
            }
            Map<String, Object> row = partnerMapper.findPartner(tenantId, cursor);
            if (row == null || !"MERGED".equals(Rows.str(row, "status"))) {
                return;
            }
            Long next = Rows.lng(row, "mergedInto");
            if (next == null) {
                throw new BizException(MdmErrorCode.STATE_CONFLICT);
            }
            cursor = next;
        }
        throw new BizException(MdmErrorCode.MERGE_CYCLE);
    }

    private void insertAlias(long tenantId, long partnerId, String type, String value,
                             String normalizedValue, long mergeId, long userId) {
        if (isBlank(value)) {
            return;
        }
        partnerMapper.insertAlias(IdWorker.getId(), tenantId, partnerId, type, value, normalizedValue,
                mergeId, 1, userId);
    }

    private PartnerCreateResult duplicateResult(long tenantId, Map<String, Object> existing) {
        List<PartnerCandidate> candidates = new ArrayList<>();
        candidates.add(candidate(existing));
        long existingId = Rows.lngVal(existing, "id");
        candidates.addAll(candidates(tenantId, existingId, Rows.str(existing, "normalizedName")));
        return new PartnerCreateResult(view(existing), true, candidates);
    }

    private List<PartnerCandidate> candidates(long tenantId, long excludeId, String normalized) {
        if (normalized == null || normalized.length() < 2) {
            return List.of();
        }
        String prefix = normalized.substring(0, Math.min(4, normalized.length()));
        List<PartnerCandidate> candidates = new ArrayList<>();
        for (Map<String, Object> row : partnerMapper.findCandidates(tenantId, excludeId, normalized,
                prefix, 5)) {
            candidates.add(candidate(row));
        }
        return candidates;
    }

    private String nextCode(long tenantId, int attempt) {
        long sequence = partnerMapper.countPartners(tenantId) + 1 + attempt;
        return "BP" + String.format("%06d", sequence);
    }

    private Map<String, Object> requirePartner(long tenantId, long id) {
        Map<String, Object> row = partnerMapper.findPartner(tenantId, id);
        if (row == null) {
            throw new BizException(MdmErrorCode.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    private static PartnerView view(Map<String, Object> row) {
        return new PartnerView(Rows.lngVal(row, "id"), Rows.str(row, "partnerCode"), Rows.str(row, "name"),
                Rows.str(row, "shortName"), Rows.str(row, "unifiedCreditCode"), Rows.str(row, "partnerType"),
                Rows.str(row, "status"), Rows.str(row, "sourceDomain"), Rows.str(row, "sourceRef"),
                Rows.str(row, "normalizedName"), Rows.lng(row, "mergedInto"), Rows.instant(row, "createdAt"));
    }

    private static PartnerCandidate candidate(Map<String, Object> row) {
        return new PartnerCandidate(Rows.lngVal(row, "id"), Rows.str(row, "partnerCode"),
                Rows.str(row, "name"), Rows.str(row, "normalizedName"), Rows.str(row, "status"));
    }

    private static MergeView mergeView(Map<String, Object> row) {
        return new MergeView(Rows.lngVal(row, "id"), Rows.lngVal(row, "sourcePartnerId"),
                Rows.lngVal(row, "targetPartnerId"), Rows.str(row, "status"), Rows.str(row, "reason"),
                Rows.instant(row, "mergedAt"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
