package com.fenglin.talent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.mapper.EmployeeMapper;
import com.fenglin.talent.mapper.OrgMapper;
import com.fenglin.talent.support.Rows;
import com.fenglin.talent.support.TalentAudit;
import com.fenglin.talent.support.TalentContext;
import com.fenglin.talent.support.TalentCrypto;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeDetail;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeListItem;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeImportError;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeImportRequest;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeImportResult;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeImportRow;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeUpdateRequest;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeUpdateResult;
import com.fenglin.talent.web.dto.TalentDtos.HeadcountReport;
import com.fenglin.talent.web.dto.TalentDtos.NameRef;
import com.fenglin.talent.web.dto.TalentDtos.OrgHeadcount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 员工主数据：PII 加密落库（AES-256-GCM + 租户盐哈希），列表与详情仅返回脱敏值。
 */
@Service
public class EmployeeService {

    private static final Set<String> GENDERS = Set.of("M", "F", "UNKNOWN");
    private static final Set<String> EMPLOYMENT_TYPES = Set.of("REGULAR", "INTERN", "OUTSOURCE");

    private final EmployeeMapper employeeMapper;
    private final OrgMapper orgMapper;
    private final TalentCrypto crypto;
    private final TalentAudit audit;

    public EmployeeService(EmployeeMapper employeeMapper, OrgMapper orgMapper, TalentCrypto crypto,
                           TalentAudit audit) {
        this.employeeMapper = employeeMapper;
        this.orgMapper = orgMapper;
        this.crypto = crypto;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResult<EmployeeListItem> list(Long orgId, Boolean includeSubOrg, String empStatus, String keyword,
                                             Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        boolean includeSub = Boolean.TRUE.equals(includeSubOrg);
        String pathPrefix = null;
        Long exactOrgId = orgId;
        if (orgId != null && includeSub) {
            Map<String, Object> org = orgMapper.findOrg(tenantId, orgId);
            if (org == null) {
                throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "组织不存在");
            }
            pathPrefix = Rows.str(org, "path");
            exactOrgId = null;
        }
        List<Map<String, Object>> rows = employeeMapper.listEmployees(tenantId, exactOrgId, pathPrefix,
                blankToNull(empStatus), blankToNull(keyword), pageSize, (pageNo - 1) * pageSize);
        long total = employeeMapper.countEmployees(tenantId, exactOrgId, pathPrefix, blankToNull(empStatus),
                blankToNull(keyword));
        List<EmployeeListItem> records = rows.stream().map(row -> new EmployeeListItem(
                Rows.lngVal(row, "id"), Rows.str(row, "empNo"), Rows.str(row, "name"),
                new NameRef(Rows.lng(row, "orgId"), Rows.str(row, "orgName")),
                Rows.str(row, "positionName"), Rows.str(row, "gradeName"), Rows.str(row, "empStatus"),
                Rows.date(row, "hireDate"), maskedPhone(tenantId, row))).toList();
        return PageResult.of(records, total, pageNo, pageSize);
    }

    @Transactional(readOnly = true)
    public EmployeeDetail detail(long id) {
        long tenantId = TalentContext.tenantId();
        Map<String, Object> row = employeeMapper.findEmployee(tenantId, id);
        if (row == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "员工不存在");
        }
        return new EmployeeDetail(
                Rows.lngVal(row, "id"), Rows.str(row, "empNo"), Rows.str(row, "name"), Rows.str(row, "gender"),
                Rows.date(row, "birthDate"), maskedPhone(tenantId, row), maskedIdCard(tenantId, row),
                Rows.str(row, "email"),
                new NameRef(Rows.lng(row, "orgId"), Rows.str(row, "orgName")),
                ref(Rows.lng(row, "positionId"), Rows.str(row, "positionName")),
                ref(Rows.lng(row, "gradeId"), Rows.str(row, "gradeName")),
                ref(Rows.lng(row, "managerId"), Rows.str(row, "managerName")),
                Rows.str(row, "employmentType"), Rows.date(row, "hireDate"), Rows.date(row, "formalDate"),
                Rows.str(row, "empStatus"), Rows.str(row, "workLocation"));
    }

    @Transactional
    public EmployeeCreateResult create(EmployeeCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.name() == null || request.name().isBlank()
                || request.empNo() == null || request.empNo().isBlank()
                || request.orgId() == null || request.hireDate() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "工号、姓名、组织与入职日期不能为空");
        }
        if (orgMapper.findOrg(tenantId, request.orgId()) == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "组织不存在");
        }
        String gender = request.gender() == null ? "UNKNOWN" : request.gender();
        if (!GENDERS.contains(gender)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "性别必须为 M/F/UNKNOWN");
        }
        String employmentType = request.employmentType() == null ? "REGULAR" : request.employmentType();
        if (!EMPLOYMENT_TYPES.contains(employmentType)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "用工类型必须为 REGULAR/INTERN/OUTSOURCE");
        }
        if (employeeMapper.countByEmpNo(tenantId, request.empNo()) > 0) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "工号已存在");
        }
        String phoneHash = crypto.hash(tenantId, request.phone());
        String idCardHash = crypto.hash(tenantId, request.idCard());
        if (phoneHash != null && employeeMapper.countByPhoneHash(tenantId, phoneHash) > 0) {
            throw new BizException(TalentErrorCode.PII_DUPLICATED, "手机号已存在");
        }
        if (idCardHash != null && employeeMapper.countByIdCardHash(tenantId, idCardHash) > 0) {
            throw new BizException(TalentErrorCode.PII_DUPLICATED, "身份证号已存在");
        }
        long id = IdWorker.getId();
        employeeMapper.insertEmployee(id, tenantId, request.empNo(), request.name(), gender, request.birthDate(),
                crypto.encrypt(tenantId, "talent_employee", "phone", request.phone()), phoneHash,
                crypto.encrypt(tenantId, "talent_employee", "id_card", request.idCard()), idCardHash,
                request.email(), request.orgId(), request.positionId(), request.gradeId(), request.managerId(),
                employmentType, request.hireDate(), request.workLocation(), userId);
        audit.publish("TALENT_EMPLOYEE_CREATED", "CREATE", "talent_employee", id, "MEDIUM");
        return new EmployeeCreateResult(id, request.empNo(), "PENDING");
    }

    @Transactional
    public EmployeeUpdateResult update(long id, EmployeeUpdateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> current = employeeMapper.findEmployee(tenantId, id);
        if (current == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "员工不存在");
        }
        if (request == null || request.version() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "version 不能为空");
        }
        String name = request.name() == null ? Rows.str(current, "name") : request.name();
        String email = request.email() == null ? Rows.str(current, "email") : request.email();
        long orgId = request.orgId() == null ? Rows.lngVal(current, "orgId") : request.orgId();
        Long positionId = request.positionId() == null ? Rows.lng(current, "positionId") : request.positionId();
        Long gradeId = request.gradeId() == null ? Rows.lng(current, "gradeId") : request.gradeId();
        Long managerId = request.managerId() == null ? Rows.lng(current, "managerId") : request.managerId();
        String workLocation = request.workLocation() == null ? Rows.str(current, "workLocation")
                : request.workLocation();
        int updated = employeeMapper.updateEmployee(tenantId, id, name, email, orgId, positionId, gradeId,
                managerId, workLocation, request.version(), userId);
        if (updated == 0) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "员工信息已被并发修改，请刷新后重试");
        }
        audit.publish("TALENT_EMPLOYEE_UPDATED", "UPDATE", "talent_employee", id, "MEDIUM");
        return new EmployeeUpdateResult(id, Instant.now());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> requireEmployee(long tenantId, long id) {
        Map<String, Object> row = employeeMapper.findEmployee(tenantId, id);
        if (row == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "员工不存在");
        }
        return row;
    }

    // ---------- 批量导入 / 导出 / 报表 ----------

    @Transactional
    public EmployeeImportResult importEmployees(EmployeeImportRequest request) {
        if (request == null || request.rows() == null || request.rows().isEmpty()) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "导入数据不能为空");
        }
        if (request.rows().size() > 500) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "单次最多导入 500 行");
        }
        List<EmployeeImportRow> rows = request.rows();
        List<EmployeeImportError> errors = new ArrayList<>();
        int success = 0;
        for (int i = 0; i < rows.size(); i++) {
            EmployeeImportRow row = rows.get(i);
            if (row == null) {
                errors.add(new EmployeeImportError(i, null, TalentErrorCode.PARAM_INVALID.code(), "行数据为空"));
                continue;
            }
            try {
                create(new EmployeeCreateRequest(row.empNo(), row.name(), row.gender(), row.birthDate(),
                        row.phone(), row.idCard(), row.email(), row.orgId(), row.positionId(), row.gradeId(),
                        row.managerId(), row.employmentType(), row.hireDate(), row.workLocation()));
                success++;
            } catch (BizException ex) {
                errors.add(new EmployeeImportError(i, row.empNo(), ex.getErrorCode().code(), ex.getMessage()));
            }
        }
        audit.publish("TALENT_EMPLOYEES_IMPORTED", "IMPORT", "talent_employee", 0L, "MEDIUM");
        return new EmployeeImportResult(rows.size(), success, rows.size() - success, errors);
    }

    @Transactional(readOnly = true)
    public String rosterCsv(Long orgId, Boolean includeSubOrg, String empStatus) {
        long tenantId = TalentContext.tenantId();
        Scope scope = resolveScope(tenantId, orgId, includeSubOrg);
        List<Map<String, Object>> rows = employeeMapper.listEmployees(tenantId, scope.exactOrgId(),
                scope.pathPrefix(), blankToNull(empStatus), null, 100000, 0);
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("工号,姓名,性别,组织,岗位,职级,直属上级,入职日期,员工状态,工作地,脱敏手机号\n");
        for (Map<String, Object> row : rows) {
            LocalDate hireDate = Rows.date(row, "hireDate");
            csv.append(csvCell(Rows.str(row, "empNo"))).append(',')
                    .append(csvCell(Rows.str(row, "name"))).append(',')
                    .append(csvCell(Rows.str(row, "gender"))).append(',')
                    .append(csvCell(Rows.str(row, "orgName"))).append(',')
                    .append(csvCell(Rows.str(row, "positionName"))).append(',')
                    .append(csvCell(Rows.str(row, "gradeName"))).append(',')
                    .append(csvCell(Rows.str(row, "managerName"))).append(',')
                    .append(csvCell(hireDate == null ? null : hireDate.toString())).append(',')
                    .append(csvCell(Rows.str(row, "empStatus"))).append(',')
                    .append(csvCell(Rows.str(row, "workLocation"))).append(',')
                    .append(csvCell(maskedPhone(tenantId, row))).append('\n');
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public HeadcountReport headcountReport(Long orgId, Boolean includeSubOrg) {
        long tenantId = TalentContext.tenantId();
        Scope scope = resolveScope(tenantId, orgId, includeSubOrg);
        List<Map<String, Object>> rows = employeeMapper.listEmployees(tenantId, scope.exactOrgId(),
                scope.pathPrefix(), null, null, 100000, 0);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String status : List.of("ACTIVE", "PROBATION", "PENDING", "RESIGNED")) {
            byStatus.put(status, 0L);
        }
        Map<String, Long> byEmploymentType = new LinkedHashMap<>();
        Map<Long, Long> orgCounts = new LinkedHashMap<>();
        Map<Long, String> orgNames = new LinkedHashMap<>();
        long tenureMonths = 0;
        int tenureCount = 0;
        LocalDate today = LocalDate.now();
        for (Map<String, Object> row : rows) {
            byStatus.merge(Rows.str(row, "empStatus"), 1L, Long::sum);
            String employmentType = Rows.str(row, "employmentType");
            byEmploymentType.merge(employmentType == null ? "UNKNOWN" : employmentType, 1L, Long::sum);
            Long rowOrgId = Rows.lng(row, "orgId");
            if (rowOrgId != null) {
                orgCounts.merge(rowOrgId, 1L, Long::sum);
                orgNames.putIfAbsent(rowOrgId, Rows.str(row, "orgName"));
            }
            LocalDate hireDate = Rows.date(row, "hireDate");
            if (hireDate != null && !hireDate.isAfter(today)) {
                tenureMonths += ChronoUnit.MONTHS.between(hireDate, today);
                tenureCount++;
            }
        }
        List<OrgHeadcount> byOrg = new ArrayList<>();
        orgCounts.forEach((key, count) -> byOrg.add(new OrgHeadcount(key, orgNames.get(key), count)));
        BigDecimal avgTenureMonths = tenureCount == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(tenureMonths)
                        .divide(BigDecimal.valueOf(tenureCount), 1, RoundingMode.HALF_UP);
        return new HeadcountReport(rows.size(), byStatus, byOrg, byEmploymentType, avgTenureMonths);
    }

    private Scope resolveScope(long tenantId, Long orgId, Boolean includeSubOrg) {
        if (orgId == null) {
            return new Scope(null, null);
        }
        if (!Boolean.TRUE.equals(includeSubOrg)) {
            return new Scope(orgId, null);
        }
        Map<String, Object> org = orgMapper.findOrg(tenantId, orgId);
        if (org == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "组织不存在");
        }
        return new Scope(null, Rows.str(org, "path"));
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")
                || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private record Scope(Long exactOrgId, String pathPrefix) {
    }

    private NameRef ref(Long id, String name) {
        return id == null ? null : new NameRef(id, name);
    }

    private String maskedPhone(long tenantId, Map<String, Object> row) {
        return crypto.maskPhone(crypto.decrypt(tenantId, "talent_employee", "phone",
                Rows.str(row, "phoneEnc")));
    }

    private String maskedIdCard(long tenantId, Map<String, Object> row) {
        return crypto.maskIdCard(crypto.decrypt(tenantId, "talent_employee", "id_card",
                Rows.str(row, "idCardEnc")));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
