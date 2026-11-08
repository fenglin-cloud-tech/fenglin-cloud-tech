package com.fenglin.talent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.mapper.OrgMapper;
import com.fenglin.talent.support.Rows;
import com.fenglin.talent.support.TalentAudit;
import com.fenglin.talent.support.TalentContext;
import com.fenglin.talent.web.dto.TalentDtos.NameRef;
import com.fenglin.talent.web.dto.TalentDtos.OrgCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.OrgCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.OrgDeleteResult;
import com.fenglin.talent.web.dto.TalentDtos.OrgDetail;
import com.fenglin.talent.web.dto.TalentDtos.OrgMoveRequest;
import com.fenglin.talent.web.dto.TalentDtos.OrgMoveResult;
import com.fenglin.talent.web.dto.TalentDtos.OrgNode;
import com.fenglin.talent.web.dto.TalentDtos.OrgUpdateRequest;
import com.fenglin.talent.web.dto.TalentDtos.OrgUpdateResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 组织架构主链：树查询、详情、新建、更新、移动（级联重算 path/level）、撤销前置校验。
 */
@Service
public class OrgService {

    private static final Pattern ORG_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*$");
    private static final Set<String> ORG_TYPES = Set.of("COMPANY", "DEPT", "TEAM");
    private static final Set<String> ORG_STATUS = Set.of("ENABLED", "DISABLED");

    private final OrgMapper orgMapper;
    private final TalentAudit audit;

    public OrgService(OrgMapper orgMapper, TalentAudit audit) {
        this.orgMapper = orgMapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OrgNode> tree(Long rootId, Integer depth, Boolean includeDisabled) {
        long tenantId = TalentContext.tenantId();
        String rootPath = null;
        if (rootId != null) {
            Map<String, Object> root = orgMapper.findOrg(tenantId, rootId);
            if (root == null) {
                throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "组织不存在");
            }
            rootPath = Rows.str(root, "path");
        }
        List<Map<String, Object>> rows = orgMapper.listOrgs(tenantId, rootPath,
                Boolean.TRUE.equals(includeDisabled));
        Set<Long> ids = rows.stream().map(row -> Rows.lngVal(row, "id")).collect(Collectors.toSet());
        Map<Long, List<Map<String, Object>>> childrenByParent = rows.stream()
                .filter(row -> {
                    Long parentId = Rows.lng(row, "parentId");
                    return parentId != null && ids.contains(parentId);
                })
                .collect(Collectors.groupingBy(row -> Rows.lngVal(row, "parentId")));
        int maxDepth = depth == null || depth < 1 ? Integer.MAX_VALUE : depth;
        return rows.stream()
                .filter(row -> {
                    Long parentId = Rows.lng(row, "parentId");
                    return parentId == null || !ids.contains(parentId);
                })
                .map(row -> toNode(row, childrenByParent, maxDepth, 1))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrgDetail detail(long id) {
        long tenantId = TalentContext.tenantId();
        Map<String, Object> row = requireOrg(tenantId, id);
        Long leaderId = Rows.lng(row, "leaderId");
        NameRef leader = leaderId == null ? null : new NameRef(leaderId, Rows.str(row, "leaderName"));
        return new OrgDetail(Rows.lngVal(row, "id"), Rows.str(row, "orgCode"), Rows.str(row, "orgName"),
                Rows.lng(row, "parentId"), Rows.str(row, "path"), Rows.intVal(row, "level"),
                Rows.str(row, "orgType"), leader, orgMapper.countActiveEmployees(tenantId, id),
                Rows.str(row, "status"));
    }

    @Transactional
    public OrgCreateResult create(OrgCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.orgCode() == null || !ORG_CODE_PATTERN.matcher(request.orgCode()).matches()) {
            throw new BizException(TalentErrorCode.ORG_STRUCTURE_INVALID, "组织编码须为层级点分格式，如 FL.SALES");
        }
        if (request.orgName() == null || request.orgName().isBlank()) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "组织名称不能为空");
        }
        String orgType = request.orgType() == null ? "DEPT" : request.orgType();
        if (!ORG_TYPES.contains(orgType)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "组织类型必须为 COMPANY/DEPT/TEAM");
        }
        if (orgMapper.findOrgByCode(tenantId, request.orgCode()) != null) {
            throw new BizException(TalentErrorCode.ORG_STRUCTURE_INVALID, "组织编码已存在");
        }
        Long parentId = request.parentId();
        String path;
        int level;
        if (parentId == null) {
            path = null;
            level = 1;
        } else {
            Map<String, Object> parent = requireOrg(tenantId, parentId);
            path = Rows.str(parent, "path");
            level = Rows.intVal(parent, "level") + 1;
        }
        long id = IdWorker.getId();
        String finalPath = path == null ? "/" + id + "/" : path + id + "/";
        orgMapper.insertOrg(id, tenantId, request.orgCode(), request.orgName(), parentId, finalPath, level,
                orgType, request.leaderId(), request.sortNo() == null ? 0 : request.sortNo(), "ENABLED", userId);
        audit.publish("TALENT_ORG_CREATED", "CREATE", "talent_org", id, "LOW");
        return new OrgCreateResult(id, request.orgCode(), finalPath, level);
    }

    @Transactional
    public OrgUpdateResult update(long id, OrgUpdateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> current = requireOrg(tenantId, id);
        if (request == null || request.version() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "version 不能为空");
        }
        String orgName = request.orgName() == null ? Rows.str(current, "orgName") : request.orgName();
        String status = request.status() == null ? Rows.str(current, "status") : request.status();
        if (!ORG_STATUS.contains(status)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "组织状态必须为 ENABLED/DISABLED");
        }
        Long leaderId = request.leaderId() == null ? Rows.lng(current, "leaderId") : request.leaderId();
        int sortNo = request.sortNo() == null ? Rows.intVal(current, "sortNo") : request.sortNo();
        int updated = orgMapper.updateOrg(tenantId, id, orgName, leaderId, sortNo, status, request.version(), userId);
        if (updated == 0) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "组织已被并发修改，请刷新后重试");
        }
        audit.publish("TALENT_ORG_UPDATED", "UPDATE", "talent_org", id, "LOW");
        return new OrgUpdateResult(id, Instant.now());
    }

    @Transactional
    public OrgMoveResult move(long id, OrgMoveRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> current = requireOrg(tenantId, id);
        String oldPath = Rows.str(current, "path");
        int oldLevel = Rows.intVal(current, "level");
        Long targetParentId = request == null ? null : request.targetParentId();
        String newPath;
        int newLevel;
        if (targetParentId == null) {
            newPath = "/" + id + "/";
            newLevel = 1;
        } else {
            Map<String, Object> target = requireOrg(tenantId, targetParentId);
            if (targetParentId == id || Rows.str(target, "path").startsWith(oldPath)) {
                throw new BizException(TalentErrorCode.ORG_STRUCTURE_INVALID, "组织移动形成环");
            }
            newPath = Rows.str(target, "path") + id + "/";
            newLevel = Rows.intVal(target, "level") + 1;
        }
        orgMapper.updateSubtreePath(tenantId, oldPath, newPath, newLevel - oldLevel, userId);
        orgMapper.updateParent(tenantId, id, targetParentId, userId);
        audit.publish("TALENT_ORG_MOVED", "UPDATE", "talent_org", id, "MEDIUM");
        return new OrgMoveResult(id, newPath, newLevel);
    }

    @Transactional
    public OrgDeleteResult delete(long id) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        requireOrg(tenantId, id);
        if (orgMapper.countActiveEmployees(tenantId, id) > 0
                || orgMapper.countChildren(tenantId, id) > 0
                || orgMapper.countOpenRequisitions(tenantId, id) > 0) {
            throw new BizException(TalentErrorCode.ORG_NOT_EMPTY);
        }
        orgMapper.disableOrg(tenantId, id, userId);
        audit.publish("TALENT_ORG_DISABLED", "DELETE", "talent_org", id, "MEDIUM");
        return new OrgDeleteResult(id, "DISABLED");
    }

    @Transactional(readOnly = true)
    public String pathOf(long orgId) {
        return Rows.str(requireOrg(TalentContext.tenantId(), orgId), "path");
    }

    private Map<String, Object> requireOrg(long tenantId, long id) {
        Map<String, Object> row = orgMapper.findOrg(tenantId, id);
        if (row == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "组织不存在");
        }
        return row;
    }

    private OrgNode toNode(Map<String, Object> row, Map<Long, List<Map<String, Object>>> childrenByParent,
                           int maxDepth, int currentDepth) {
        long id = Rows.lngVal(row, "id");
        List<OrgNode> children = currentDepth >= maxDepth
                ? List.of()
                : childrenByParent.getOrDefault(id, List.of()).stream()
                        .map(child -> toNode(child, childrenByParent, maxDepth, currentDepth + 1))
                        .toList();
        return new OrgNode(id, Rows.str(row, "orgCode"), Rows.str(row, "orgName"), Rows.lng(row, "parentId"),
                Rows.intVal(row, "level"), Rows.str(row, "orgType"), Rows.lng(row, "leaderId"), children);
    }
}
