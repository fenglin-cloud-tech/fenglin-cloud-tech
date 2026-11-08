package com.fenglin.iam.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.RequestContext;
import com.fenglin.common.security.PasswordPolicy;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.iam.mapper.AccountMapper;
import com.fenglin.iam.mapper.MenuMapper;
import com.fenglin.iam.mapper.PermissionMapper;
import com.fenglin.iam.mapper.RoleMapper;
import com.fenglin.iam.mapper.TenantMapper;
import com.fenglin.iam.mapper.UserMapper;
import com.fenglin.iam.web.dto.AssignRolesRequest;
import com.fenglin.iam.web.dto.CreateRoleRequest;
import com.fenglin.iam.web.dto.CreateUserRequest;
import com.fenglin.iam.web.dto.CreateUserResult;
import com.fenglin.iam.web.dto.GrantPermissionsRequest;
import com.fenglin.iam.web.dto.MenuView;
import com.fenglin.iam.web.dto.PermissionNode;
import com.fenglin.iam.web.dto.RoleView;
import com.fenglin.iam.web.dto.UserView;
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

    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final MenuMapper menuMapper;
    private final TenantMapper tenantMapper;
    private final AuditSink auditSink;

    public IamService(UserMapper userMapper, AccountMapper accountMapper, RoleMapper roleMapper,
                      PermissionMapper permissionMapper, MenuMapper menuMapper, TenantMapper tenantMapper,
                      AuditSink auditSink) {
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
        this.tenantMapper = tenantMapper;
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
        return toUserView(row, roleMapper.listRoleCodes(tenantId, userId));
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
                .map(row -> toUserView(row, roles.getOrDefault(
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
        return toUserView(requireUser(tenantId, userId), roleMapper.listRoleCodes(tenantId, userId));
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
            throw new BizException(CommonErrorCode.NOT_FOUND, "用户不存在: " + userId);
        }
        return row;
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

    private UserView toUserView(Map<String, Object> row, List<String> roles) {
        return new UserView(
                ((Number) row.get("id")).longValue(),
                (String) row.get("username"),
                (String) row.get("employee_no"),
                (String) row.get("real_name"),
                toLong(row.get("primary_org_id")),
                toLong(row.get("primary_position_id")),
                (String) row.get("status"),
                List.copyOf(roles));
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
