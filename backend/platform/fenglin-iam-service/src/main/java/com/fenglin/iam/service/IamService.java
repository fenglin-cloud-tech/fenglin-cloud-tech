package com.fenglin.iam.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.RequestContext;
import com.fenglin.common.security.PasswordPolicy;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.iam.IamErrorCode;
import com.fenglin.iam.client.TalentEmployeeClient;
import com.fenglin.iam.mapper.AccountMapper;
import com.fenglin.iam.mapper.MenuMapper;
import com.fenglin.iam.mapper.OrgMapper;
import com.fenglin.iam.mapper.PermissionMapper;
import com.fenglin.iam.mapper.RoleMapper;
import com.fenglin.iam.mapper.TenantMapper;
import com.fenglin.iam.mapper.UserMapper;
import com.fenglin.iam.support.IamCrypto;
import com.fenglin.iam.web.dto.AssignRolesRequest;
import com.fenglin.iam.web.dto.CreateOrgRequest;
import com.fenglin.iam.web.dto.CreateRoleRequest;
import com.fenglin.iam.web.dto.CreateUserRequest;
import com.fenglin.iam.web.dto.CreateUserResult;
import com.fenglin.iam.web.dto.GrantPermissionsRequest;
import com.fenglin.iam.web.dto.MenuView;
import com.fenglin.iam.web.dto.OrgTreeNode;
import com.fenglin.iam.web.dto.PermissionNode;
import com.fenglin.iam.web.dto.RoleView;
import com.fenglin.iam.web.dto.UpdateOrgRequest;
import com.fenglin.iam.web.dto.UpdateUserProfileRequest;
import com.fenglin.iam.web.dto.UserView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class IamService {

    private static final Set<String> ROLE_TYPES = Set.of("SYS", "BIZ", "APP");
    private static final Set<String> DATA_SCOPES =
            Set.of("ALL", "DEPT_AND_CHILD", "DEPT", "SELF", "CUSTOM");
    private static final Set<String> ORG_STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Set<String> USER_STATUSES = Set.of("ACTIVE", "DISABLED", "LOCKED");

    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final MenuMapper menuMapper;
    private final TenantMapper tenantMapper;
    private final OrgMapper orgMapper;
    private final IamCrypto iamCrypto;
    private final TalentEmployeeClient talentEmployeeClient;
    private final AuditSink auditSink;

    public IamService(UserMapper userMapper, AccountMapper accountMapper, RoleMapper roleMapper,
                      PermissionMapper permissionMapper, MenuMapper menuMapper, TenantMapper tenantMapper,
                      OrgMapper orgMapper, IamCrypto iamCrypto, TalentEmployeeClient talentEmployeeClient,
                      AuditSink auditSink) {
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
        this.tenantMapper = tenantMapper;
        this.orgMapper = orgMapper;
        this.iamCrypto = iamCrypto;
        this.talentEmployeeClient = talentEmployeeClient;
        this.auditSink = auditSink;
    }

    @Transactional
    public CreateUserResult createUser(CreateUserRequest request) {
        long tenantId = TenantContext.requireTenantId();
        if (userMapper.countByUsername(tenantId, request.username()) > 0) {
            throw new BizException(CommonErrorCode.CONFLICT, "用户名已存在: " + request.username());
        }
        String employeeNo = normalize(request.employeeNo());
        if (employeeNo == null) {
            employeeNo = generateEmployeeNo(tenantId);
        } else if (userMapper.countByEmployeeNo(tenantId, employeeNo) > 0) {
            throw new BizException(CommonErrorCode.CONFLICT, "工号已存在: " + employeeNo);
        }

        List<Long> roleIds = request.roleIds() == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(request.roleIds()));
        for (Long roleId : roleIds) {
            requireRole(tenantId, roleId);
        }

        long userId = IdWorker.getId();
        userMapper.insert(userId, tenantId, employeeNo, request.username(), request.realName(),
                request.primaryOrgId(), request.primaryPositionId());

        String rawPassword = normalize(request.password());
        String initialPassword = null;
        if (rawPassword == null) {
            initialPassword = PasswordPolicy.generate();
            rawPassword = initialPassword;
        }
        accountMapper.insert(IdWorker.getId(), tenantId, userId, PasswordPolicy.encode(rawPassword));

        for (Long roleId : roleIds) {
            roleMapper.grantUserRole(IdWorker.getId(), tenantId, userId, roleId, null);
        }
        audit("IAM_USER_CREATED", "USER", String.valueOf(userId));
        return new CreateUserResult(userId, employeeNo, "ACTIVE", initialPassword);
    }

    @Transactional(readOnly = true)
    public UserView getUser(long userId) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> row = requireUser(tenantId, userId);
        return toUserView(tenantId, row, roleMapper.listRoleCodes(tenantId, userId));
    }

    @Transactional(readOnly = true)
    public List<UserView> listUsers(Long orgId, String status, String keyword, int page, int size) {
        long tenantId = TenantContext.requireTenantId();
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 200);
        List<Map<String, Object>> rows = userMapper.findPage(tenantId, orgId, normalize(status),
                normalize(keyword), normalizedSize, (normalizedPage - 1) * normalizedSize);
        Map<Long, List<String>> roles = new HashMap<>();
        if (!rows.isEmpty()) {
            List<Long> ids = rows.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
            for (Map<String, Object> roleRow : roleMapper.listRoleCodesByUsers(tenantId, ids)) {
                long userId = ((Number) roleRow.get("user_id")).longValue();
                roles.computeIfAbsent(userId, key -> new ArrayList<>()).add((String) roleRow.get("code"));
            }
        }
        return rows.stream()
                .map(row -> toUserView(tenantId, row, roles.getOrDefault(
                        ((Number) row.get("id")).longValue(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countUsers(Long orgId, String status, String keyword) {
        return userMapper.count(TenantContext.requireTenantId(), orgId, normalize(status), normalize(keyword));
    }

    @Transactional
    public RoleView createRole(CreateRoleRequest request) {
        long tenantId = TenantContext.requireTenantId();
        if (roleMapper.findByCode(tenantId, request.code()) != null) {
            throw new BizException(CommonErrorCode.CONFLICT, "角色编码已存在: " + request.code());
        }
        String roleType = normalize(request.roleType()) == null ? "BIZ" : request.roleType().toUpperCase();
        if (!ROLE_TYPES.contains(roleType)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "roleType 仅支持 SYS/BIZ/APP");
        }
        String dataScope = normalize(request.dataScope()) == null ? "SELF" : request.dataScope().toUpperCase();
        if (!DATA_SCOPES.contains(dataScope)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "dataScope 不合法: " + request.dataScope());
        }
        long roleId = IdWorker.getId();
        roleMapper.insert(roleId, tenantId, request.code(), request.name(), roleType, dataScope,
                request.parentId());
        audit("IAM_ROLE_CREATED", "ROLE", String.valueOf(roleId));
        return new RoleView(roleId, request.code(), request.name(), roleType, dataScope, "ENABLED", List.of());
    }

    @Transactional(readOnly = true)
    public List<RoleView> listRoles(String roleType, String keyword, int page, int size) {
        long tenantId = TenantContext.requireTenantId();
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 200);
        return roleMapper.findPage(tenantId, normalize(roleType), normalize(keyword), normalizedSize,
                        (normalizedPage - 1) * normalizedSize)
                .stream()
                .map(row -> toRoleView(tenantId, row))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countRoles(String roleType, String keyword) {
        return roleMapper.count(TenantContext.requireTenantId(), normalize(roleType), normalize(keyword));
    }

    @Transactional
    public RoleView grantPermissions(long roleId, GrantPermissionsRequest request) {
        long tenantId = TenantContext.requireTenantId();
        requireRole(tenantId, roleId);
        List<String> codes = request.permissionCodes() == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(request.permissionCodes()));
        if (!codes.isEmpty() && permissionMapper.countByCodes(codes) != codes.size()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "存在未定义的权限码");
        }
        roleMapper.clearPermissions(tenantId, roleId);
        for (String code : codes) {
            roleMapper.grantPermission(IdWorker.getId(), tenantId, roleId, code);
        }
        audit("IAM_ROLE_PERMISSIONS_GRANTED", "ROLE", String.valueOf(roleId));
        return toRoleView(tenantId, roleMapper.findById(tenantId, roleId));
    }

    @Transactional
    public UserView assignRoles(long userId, AssignRolesRequest request) {
        long tenantId = TenantContext.requireTenantId();
        requireUser(tenantId, userId);
        List<AssignRolesRequest.Item> items = request.items() == null ? List.of() : request.items();
        for (AssignRolesRequest.Item item : items) {
            requireRole(tenantId, item.roleId());
        }
        roleMapper.clearUserRoles(tenantId, userId);
        for (AssignRolesRequest.Item item : items) {
            roleMapper.grantUserRole(IdWorker.getId(), tenantId, userId, item.roleId(), item.expireAt());
        }
        audit("IAM_USER_ROLES_ASSIGNED", "USER", String.valueOf(userId));
        return toUserView(tenantId, requireUser(tenantId, userId), roleMapper.listRoleCodes(tenantId, userId));
    }

    @Transactional(readOnly = true)
    public List<OrgTreeNode> orgTree() {
        long tenantId = TenantContext.requireTenantId();
        return buildOrgTree(orgMapper.list(tenantId));
    }

    @Transactional
    public OrgTreeNode createOrg(CreateOrgRequest request) {
        long tenantId = TenantContext.requireTenantId();
        String name = normalize(request.name());
        if (name == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "组织名称不能为空");
        }
        Long parentId = request.parentId();
        Map<String, Object> parent = parentId == null ? null : requireOrg(tenantId, parentId);
        String orgType = normalize(request.orgType());
        orgType = orgType == null ? "DEPT" : orgType.toUpperCase();
        int sort = request.sort() == null ? 0 : request.sort();
        long orgId = IdWorker.getId();
        String pathCode = (parent == null ? "" : orgPathPrefix(parent)) + orgId + "/";
        orgMapper.insert(orgId, tenantId, parentId, pathCode, name, orgType, request.leaderId(), sort,
                operatorId());
        audit("IAM_ORG_CREATED", "ORG", String.valueOf(orgId));
        return toOrgTreeNode(requireOrg(tenantId, orgId));
    }

    @Transactional
    public OrgTreeNode updateOrg(long orgId, UpdateOrgRequest request) {
        long tenantId = TenantContext.requireTenantId();
        requireOrg(tenantId, orgId);
        String status = normalize(request.status());
        if (status != null) {
            status = status.toUpperCase();
            if (!ORG_STATUSES.contains(status)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "status 仅支持 ACTIVE/DISABLED");
            }
        }
        String name = normalize(request.name());
        boolean changed = name != null || request.sort() != null
                || request.leaderId() != null || status != null;
        if (changed) {
            int updated = orgMapper.update(tenantId, orgId, name, request.sort(), request.leaderId(),
                    status, request.version(), operatorId());
            if (updated == 0) {
                if (request.version() != null) {
                    throw new BizException(IamErrorCode.IAM_ORG_VERSION_CONFLICT);
                }
                throw new BizException(IamErrorCode.IAM_ORG_NOT_FOUND);
            }
        }
        audit("IAM_ORG_UPDATED", "ORG", String.valueOf(orgId));
        return toOrgTreeNode(requireOrg(tenantId, orgId));
    }

    @Transactional
    public OrgTreeNode moveOrg(long orgId, Long parentId) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> org = requireOrg(tenantId, orgId);
        Map<String, Object> parent = parentId == null ? null : requireActiveOrg(tenantId, parentId);
        String oldPrefix = orgPathPrefix(org);
        String newPrefix = parent == null ? "/" : orgPathPrefix(parent);
        if (parentId != null && (parentId == orgId || newPrefix.startsWith(oldPrefix))) {
            throw new BizException(IamErrorCode.IAM_ORG_MOVE_CYCLE);
        }
        orgMapper.updatePath(tenantId, orgId, parentId, newPrefix + orgId + "/", operatorId());
        orgMapper.replaceDescendantPathPrefix(tenantId, orgId, oldPrefix, newPrefix, operatorId());
        audit("IAM_ORG_MOVED", "ORG", String.valueOf(orgId));
        return toOrgTreeNode(requireOrg(tenantId, orgId));
    }

    @Transactional
    public void deleteOrg(long orgId) {
        long tenantId = TenantContext.requireTenantId();
        requireOrg(tenantId, orgId);
        if (orgMapper.countChildren(tenantId, orgId) > 0) {
            throw new BizException(IamErrorCode.IAM_ORG_HAS_CHILDREN);
        }
        if (orgMapper.countMembers(tenantId, orgId) > 0) {
            throw new BizException(IamErrorCode.IAM_ORG_HAS_MEMBERS);
        }
        orgMapper.softDelete(tenantId, orgId, operatorId());
        audit("IAM_ORG_DELETED", "ORG", String.valueOf(orgId));
    }

    @Transactional
    public void addOrgMember(long orgId, long userId) {
        long tenantId = TenantContext.requireTenantId();
        requireOrg(tenantId, orgId);
        Map<String, Object> user = requireUser(tenantId, userId);
        Long currentPrimary = toLong(user.get("primary_org_id"));
        boolean makePrimary = currentPrimary == null;
        int isPrimary = makePrimary || currentPrimary == orgId ? 1 : 0;
        orgMapper.upsertMember(IdWorker.getId(), tenantId, userId, orgId, isPrimary, operatorId());
        if (makePrimary) {
            orgMapper.clearOtherPrimary(tenantId, userId, orgId, operatorId());
            userMapper.updatePrimaryOrg(tenantId, userId, orgId, operatorId());
        }
        audit("IAM_ORG_MEMBER_ADDED", "ORG", orgId + ":" + userId);
    }

    @Transactional
    public void removeOrgMember(long orgId, long userId) {
        long tenantId = TenantContext.requireTenantId();
        requireOrg(tenantId, orgId);
        Map<String, Object> user = requireUser(tenantId, userId);
        Map<String, Object> member = orgMapper.findMember(tenantId, userId, orgId);
        if (member == null || ((Number) member.get("deleted")).intValue() != 0) {
            return;
        }
        boolean wasPrimary = toLong(user.get("primary_org_id")) != null
                && toLong(user.get("primary_org_id")) == orgId;
        orgMapper.softDeleteMember(tenantId, userId, orgId, operatorId());
        if (wasPrimary) {
            userMapper.updatePrimaryOrg(tenantId, userId, null, operatorId());
            orgMapper.clearOtherPrimary(tenantId, userId, orgId, operatorId());
        }
        audit("IAM_ORG_MEMBER_REMOVED", "ORG", orgId + ":" + userId);
    }

    @Transactional
    public UserView updateUserProfile(long userId, UpdateUserProfileRequest request) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> user = requireUser(tenantId, userId);

        boolean nicknameSet = request.nickname() != null;
        String nickname = nicknameSet ? normalize(request.nickname()) : null;

        boolean employeeNoSet = request.employeeNo() != null;
        String employeeNo = employeeNoSet ? normalize(request.employeeNo()) : null;
        if (employeeNo != null && userMapper.countByEmployeeNoExcluding(tenantId, employeeNo, userId) > 0) {
            throw new BizException(IamErrorCode.IAM_USER_EMPLOYEE_NO_DUPLICATED);
        }

        boolean mobileSet = request.mobile() != null;
        String mobileEnc = null;
        String mobileHash = null;
        if (mobileSet && !request.mobile().isBlank()) {
            String normalizedMobile = iamCrypto.normalizePhone(request.mobile());
            mobileHash = iamCrypto.hashMobile(tenantId, normalizedMobile);
            if (userMapper.countByMobileHashExcluding(tenantId, mobileHash, userId) > 0) {
                throw new BizException(IamErrorCode.IAM_USER_MOBILE_DUPLICATED);
            }
            mobileEnc = iamCrypto.encryptMobile(tenantId, normalizedMobile);
        }

        boolean emailSet = request.email() != null;
        String emailEnc = null;
        String emailHash = null;
        if (emailSet && !request.email().isBlank()) {
            String normalizedEmail = iamCrypto.normalizeEmail(request.email());
            emailHash = iamCrypto.hashEmail(tenantId, normalizedEmail);
            if (userMapper.countByEmailHashExcluding(tenantId, emailHash, userId) > 0) {
                throw new BizException(IamErrorCode.IAM_USER_EMAIL_DUPLICATED);
            }
            emailEnc = iamCrypto.encryptEmail(tenantId, normalizedEmail);
        }

        String status = normalize(request.status());
        if (status != null) {
            status = status.toUpperCase();
            if (!USER_STATUSES.contains(status)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "status 仅支持 ACTIVE/DISABLED/LOCKED");
            }
        }

        boolean primaryOrgIdSet = request.primaryOrgId() != null;
        if (primaryOrgIdSet) {
            requireActiveOrg(tenantId, request.primaryOrgId());
        }

        boolean unbindEmployee = Boolean.TRUE.equals(request.unbindEmployeeId());
        boolean bindEmployee = request.employeeId() != null;
        if (unbindEmployee && bindEmployee) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "employee_id 与 unbind_employee_id 不能同时传入");
        }
        boolean employeeIdSet = unbindEmployee || bindEmployee;
        Long employeeId = null;
        if (bindEmployee) {
            if (request.employeeId() <= 0) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "employee_id 必须为正整数");
            }
            if (!talentEmployeeClient.exists(tenantId, request.employeeId())) {
                throw new BizException(IamErrorCode.IAM_EMPLOYEE_NOT_FOUND);
            }
            if (userMapper.countByEmployeeIdExcluding(tenantId, request.employeeId(), userId) > 0) {
                throw new BizException(IamErrorCode.IAM_USER_EMPLOYEE_BOUND);
            }
            employeeId = request.employeeId();
        }

        boolean changed = nicknameSet || employeeNoSet || mobileSet || emailSet
                || primaryOrgIdSet || employeeIdSet || status != null;
        if (changed) {
            int updated;
            try {
                updated = userMapper.updateProfile(tenantId, userId, nicknameSet, nickname,
                        employeeNoSet, employeeNo, mobileSet, mobileEnc, mobileHash,
                        emailSet, emailEnc, emailHash, primaryOrgIdSet, request.primaryOrgId(),
                        employeeIdSet, employeeId, status, request.version(), operatorId());
            } catch (DuplicateKeyException ex) {
                // 唯一索引兜底并发绑定：另一个请求刚刚占用了同一员工
                throw new BizException(IamErrorCode.IAM_USER_EMPLOYEE_BOUND);
            }
            if (updated == 0) {
                if (request.version() != null) {
                    throw new BizException(IamErrorCode.IAM_USER_VERSION_CONFLICT);
                }
                throw new BizException(IamErrorCode.IAM_USER_NOT_FOUND);
            }
        }

        if (primaryOrgIdSet) {
            orgMapper.clearOtherPrimary(tenantId, userId, request.primaryOrgId(), operatorId());
            orgMapper.upsertMember(IdWorker.getId(), tenantId, userId, request.primaryOrgId(), 1,
                    operatorId());
        }
        audit("IAM_USER_UPDATED", "USER", String.valueOf(userId));
        return toUserView(tenantId, requireUser(tenantId, userId), roleMapper.listRoleCodes(tenantId, userId));
    }

    @Transactional(readOnly = true)
    public List<PermissionNode> permissionTree(String domain) {
        List<Map<String, Object>> rows = permissionMapper.list(normalize(domain));
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Node node = new Node((String) row.get("code"), (String) row.get("name"),
                    (String) row.get("type"), (String) row.get("domain"), (String) row.get("parent_code"));
            nodes.put(node.code, node);
        }
        List<Node> roots = new ArrayList<>();
        for (Node node : nodes.values()) {
            Node parent = node.parentCode == null ? null : nodes.get(node.parentCode);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }
        return roots.stream().map(this::toPermissionNode).toList();
    }

    /**
     * 菜单按 X-Permissions 过滤：未绑定权限码的菜单始终可见，绑定权限码的需已授权或持有通配符。
     */
    @Transactional(readOnly = true)
    public List<MenuView> menus(String clientType, String permissionsHeader) {
        Set<String> granted = parsePermissions(permissionsHeader);
        boolean wildcard = granted.contains("*");
        return menuMapper.list(normalize(clientType)).stream()
                .filter(row -> {
                    String permissionCode = (String) row.get("permission_code");
                    return permissionCode == null || permissionCode.isBlank()
                            || wildcard || granted.contains(permissionCode);
                })
                .map(row -> new MenuView(
                        (String) row.get("code"),
                        (String) row.get("name"),
                        (String) row.get("path"),
                        (String) row.get("component"),
                        (String) row.get("parent_code"),
                        (String) row.get("icon"),
                        ((Number) row.get("sort")).intValue(),
                        (String) row.get("permission_code")))
                .toList();
    }

    private Set<String> parsePermissions(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
    }

    private Map<String, Object> requireUser(long tenantId, long userId) {
        Map<String, Object> row = userMapper.findById(tenantId, userId);
        if (row == null) {
            throw new BizException(IamErrorCode.IAM_USER_NOT_FOUND);
        }
        return row;
    }

    private Map<String, Object> requireOrg(long tenantId, long orgId) {
        Map<String, Object> row = orgMapper.findById(tenantId, orgId);
        if (row == null) {
            throw new BizException(IamErrorCode.IAM_ORG_NOT_FOUND);
        }
        return row;
    }

    private Map<String, Object> requireActiveOrg(long tenantId, long orgId) {
        Map<String, Object> row = requireOrg(tenantId, orgId);
        if (!"ACTIVE".equals(row.get("status"))) {
            throw new BizException(IamErrorCode.IAM_ORG_NOT_FOUND, "组织不存在或已停用: " + orgId);
        }
        return row;
    }

    private List<OrgTreeNode> buildOrgTree(List<Map<String, Object>> rows) {
        Map<Long, OrgNode> nodes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            OrgNode node = new OrgNode(row);
            nodes.put(node.id, node);
        }
        List<OrgNode> roots = new ArrayList<>();
        for (OrgNode node : nodes.values()) {
            OrgNode parent = node.parentId == null ? null : nodes.get(node.parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }
        return roots.stream().map(this::toOrgTreeNode).toList();
    }

    private OrgTreeNode toOrgTreeNode(Map<String, Object> row) {
        return new OrgTreeNode(
                ((Number) row.get("id")).longValue(),
                toLong(row.get("parent_id")),
                (String) row.get("name"),
                (String) row.get("org_type"),
                toLong(row.get("leader_id")),
                ((Number) row.get("sort")).intValue(),
                (String) row.get("status"),
                (String) row.get("path_code"),
                ((Number) row.get("member_count")).longValue(),
                List.of());
    }

    private OrgTreeNode toOrgTreeNode(OrgNode node) {
        return new OrgTreeNode(node.id, node.parentId, node.name, node.orgType, node.leaderId, node.sort,
                node.status, node.pathCode, node.memberCount,
                node.children.stream().map(this::toOrgTreeNode).toList());
    }

    private String orgPathPrefix(Map<String, Object> org) {
        String pathCode = (String) org.get("path_code");
        if (pathCode != null && !pathCode.isBlank()) {
            return pathCode;
        }
        return "/" + ((Number) org.get("id")).longValue() + "/";
    }

    private Long operatorId() {
        TenantContext.Snapshot snapshot = TenantContext.get();
        return snapshot == null ? null : snapshot.userId();
    }

    private Map<String, Object> requireRole(long tenantId, long roleId) {
        Map<String, Object> row = roleMapper.findById(tenantId, roleId);
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "角色不存在: " + roleId);
        }
        return row;
    }

    private String generateEmployeeNo(long tenantId) {
        String tenantCode = tenantMapper.findCodeById(tenantId);
        String prefix = tenantCode == null || tenantCode.length() < 4
                ? (tenantCode == null ? "FL" : tenantCode)
                : tenantCode.substring(tenantCode.length() - 4).toUpperCase();
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = prefix + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
            if (userMapper.countByEmployeeNo(tenantId, candidate) == 0) {
                return candidate;
            }
        }
        throw new BizException(CommonErrorCode.CONFLICT, "工号生成冲突，请重试");
    }

    private UserView toUserView(long tenantId, Map<String, Object> row, List<String> roles) {
        String mobile = iamCrypto.decryptMobile(tenantId, (String) row.get("mobile_enc"));
        return new UserView(
                ((Number) row.get("id")).longValue(),
                (String) row.get("username"),
                (String) row.get("employee_no"),
                (String) row.get("real_name"),
                toLong(row.get("primary_org_id")),
                toLong(row.get("primary_position_id")),
                (String) row.get("status"),
                List.copyOf(roles),
                (String) row.get("nickname"),
                iamCrypto.maskPhone(mobile),
                (String) row.get("primary_org_name"),
                toLong(row.get("employee_id")));
    }

    /** 内部接口：返回账号绑定的员工 ID，未绑定返回 null。 */
    @Transactional(readOnly = true)
    public Long getEmployeeId(long userId) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> row = requireUser(tenantId, userId);
        return toLong(row.get("employee_id"));
    }

    private RoleView toRoleView(long tenantId, Map<String, Object> row) {
        long roleId = ((Number) row.get("id")).longValue();
        return new RoleView(
                roleId,
                (String) row.get("code"),
                (String) row.get("name"),
                (String) row.get("role_type"),
                (String) row.get("data_scope"),
                (String) row.get("status"),
                roleMapper.listPermissionCodes(tenantId, roleId));
    }

    private PermissionNode toPermissionNode(Node node) {
        return new PermissionNode(node.code, node.name, node.type, node.domain, node.parentCode,
                node.children.stream().map(this::toPermissionNode).toList());
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void audit(String eventType, String resourceType, String resourceId) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        auditSink.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                eventType,
                "iam",
                eventType,
                "HIGH",
                snapshot == null ? null : snapshot.tenantId(),
                snapshot == null ? null : snapshot.userId(),
                snapshot == null ? null : snapshot.clientType(),
                RequestContext.requestId(),
                resourceType,
                resourceId,
                Instant.now()));
    }

    private static final class OrgNode {
        private final long id;
        private final Long parentId;
        private final String name;
        private final String orgType;
        private final Long leaderId;
        private final int sort;
        private final String status;
        private final String pathCode;
        private final long memberCount;
        private final List<OrgNode> children = new ArrayList<>();

        private OrgNode(Map<String, Object> row) {
            this.id = ((Number) row.get("id")).longValue();
            Object parent = row.get("parent_id");
            this.parentId = parent == null ? null : ((Number) parent).longValue();
            this.name = (String) row.get("name");
            this.orgType = (String) row.get("org_type");
            Object leader = row.get("leader_id");
            this.leaderId = leader == null ? null : ((Number) leader).longValue();
            this.sort = ((Number) row.get("sort")).intValue();
            this.status = (String) row.get("status");
            this.pathCode = (String) row.get("path_code");
            this.memberCount = ((Number) row.get("member_count")).longValue();
        }
    }

    private static final class Node {
        private final String code;
        private final String name;
        private final String type;
        private final String domain;
        private final String parentCode;
        private final List<Node> children = new ArrayList<>();

        private Node(String code, String name, String type, String domain, String parentCode) {
            this.code = code;
            this.name = name;
            this.type = type;
            this.domain = domain;
            this.parentCode = parentCode;
        }
    }
}
