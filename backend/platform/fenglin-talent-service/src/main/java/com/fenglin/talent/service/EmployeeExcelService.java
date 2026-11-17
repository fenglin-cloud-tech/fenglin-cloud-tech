package com.fenglin.talent.service;

import com.fenglin.common.core.BizException;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeImportRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * TAL-10：员工批量导入的 Excel 模板生成与解析。
 * 模板列与 {@link EmployeeImportRow} 一一对应，导入结果仍复用 EmployeeService.importEmployees 的逐行校验。
 */
@Service
public class EmployeeExcelService {

    static final String[] HEADERS = {
            "工号", "姓名", "性别", "出生日期", "手机号", "身份证号", "邮箱",
            "组织ID", "岗位ID", "职级ID", "直属上级ID", "用工类型", "入职日期", "工作地点"
    };
    private static final int MAX_ROWS = 500;
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd")
    };

    public byte[] template() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("员工导入");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, i == 5 || i == 6 ? 24 * 256 : 16 * 256);
            }
            sheet.createFreezePane(0, 1);

            Sheet notes = workbook.createSheet("填写说明");
            String[][] rules = {
                    {"字段", "说明"},
                    {"工号", "必填，租户内唯一；仅字母、数字、下划线、短横线，最长 32"},
                    {"姓名", "必填，最长 64"},
                    {"性别", "可选：M / F / UNKNOWN（留空按 UNKNOWN）"},
                    {"出生日期", "可选，格式 yyyy-MM-dd"},
                    {"手机号", "可选，11 位手机号"},
                    {"身份证号", "可选，18 位；写入后按平台规则加密与脱敏"},
                    {"组织ID/岗位ID/职级ID/直属上级ID", "组织ID 必填；其余可选。填管理端列表中显示的雪花 ID（文本格式，避免精度丢失）"},
                    {"用工类型", "可选：REGULAR / INTERN / OUTSOURCE（留空按 REGULAR）"},
                    {"入职日期", "必填，格式 yyyy-MM-dd"},
                    {"工作地点", "可选，最长 128"},
                    {"限制", "单次最多 500 行；逐行校验，失败行会在返回结果中列出，不影响其他行"}
            };
            for (int i = 0; i < rules.length; i++) {
                Row row = notes.createRow(i);
                row.createCell(0).setCellValue(rules[i][0]);
                row.createCell(1).setCellValue(rules[i][1]);
            }
            notes.setColumnWidth(0, 32 * 256);
            notes.setColumnWidth(1, 70 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "导入模板生成失败");
        }
    }

    public List<EmployeeImportRow> parse(InputStream input) {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getRow(0) == null) {
                throw new BizException(TalentErrorCode.PARAM_INVALID, "模板为空：缺少表头");
            }
            Row header = sheet.getRow(0);
            DataFormatter formatter = new DataFormatter();
            for (int i = 0; i < HEADERS.length; i++) {
                String actual = formatter.formatCellValue(header.getCell(i)).trim();
                if (!HEADERS[i].equals(actual)) {
                    throw new BizException(TalentErrorCode.PARAM_INVALID,
                            "表头不匹配：第 " + (i + 1) + " 列应为「" + HEADERS[i] + "」，实际为「" + actual + "」");
                }
            }
            List<EmployeeImportRow> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                if (rows.size() >= MAX_ROWS) {
                    throw new BizException(TalentErrorCode.PARAM_INVALID, "单次最多导入 " + MAX_ROWS + " 行");
                }
                int line = r + 1;
                rows.add(new EmployeeImportRow(
                        text(row, 0, formatter),
                        text(row, 1, formatter),
                        text(row, 2, formatter),
                        date(row, 3, line, "出生日期", formatter),
                        text(row, 4, formatter),
                        text(row, 5, formatter),
                        text(row, 6, formatter),
                        number(row, 7, line, "组织ID", formatter),
                        number(row, 8, line, "岗位ID", formatter),
                        number(row, 9, line, "职级ID", formatter),
                        number(row, 10, line, "直属上级ID", formatter),
                        text(row, 11, formatter),
                        date(row, 12, line, "入职日期", formatter),
                        text(row, 13, formatter)));
            }
            if (rows.isEmpty()) {
                throw new BizException(TalentErrorCode.PARAM_INVALID, "导入数据不能为空");
            }
            return rows;
        } catch (BizException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "Excel 解析失败：" + ex.getMessage());
        }
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int i = 0; i < HEADERS.length; i++) {
            if (!formatter.formatCellValue(row.getCell(i)).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String text(Row row, int index, DataFormatter formatter) {
        String value = formatter.formatCellValue(row.getCell(index)).trim();
        return value.isEmpty() ? null : value;
    }

    private Long number(Row row, int index, int line, String field, DataFormatter formatter) {
        String value = formatter.formatCellValue(row.getCell(index)).trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "第 " + line + " 行「" + field + "」不是有效数字：" + value);
        }
    }

    private LocalDate date(Row row, int index, int line, String field, DataFormatter formatter) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return null;
        }
        // isCellDateFormatted 内部会无条件取数值，文本单元格会抛 IllegalStateException，必须先判类型
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String value = formatter.formatCellValue(cell).trim();
        if (value.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new BizException(TalentErrorCode.PARAM_INVALID,
                "第 " + line + " 行「" + field + "」日期格式应为 yyyy-MM-dd：" + value);
    }
}
