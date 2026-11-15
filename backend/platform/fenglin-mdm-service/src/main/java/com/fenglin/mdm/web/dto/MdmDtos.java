package com.fenglin.mdm.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * MDM 往来单位接口 DTO（JSON 输出 SNAKE_CASE，ID 以字符串传输）。
 */
public final class MdmDtos {

    private MdmDtos() {
    }

    public record PartnerCreateRequest(String name, String shortName, String unifiedCreditCode,
                                       String legalRep, String contactName, String contactPhone,
                                       String contactEmail, String partnerType, String sourceDomain,
                                       String sourceRef, String remark) {
    }

    public record PartnerView(long id, String partnerCode, String name, String shortName,
                              String unifiedCreditCode, String partnerType, String status,
                              String sourceDomain, String sourceRef, String normalizedName,
                              Long mergedInto, Instant createdAt) {
    }

    public record PartnerCandidate(long id, String partnerCode, String name, String normalizedName,
                                   String status) {
    }

    public record PartnerCreateResult(PartnerView partner, boolean duplicated,
                                      List<PartnerCandidate> candidates) {
    }

    public record MergeRequest(Long sourceId, Long targetId, String reason) {
    }

    public record MergeView(long id, long sourcePartnerId, long targetPartnerId, String status,
                            String reason, Instant mergedAt) {
    }

    public record ResolveView(long requestedId, long partnerId, String partnerCode, String name,
                              boolean merged) {
    }

    public record AliasView(long id, String aliasType, String aliasValue, boolean active) {
    }
}
