package com.fenglin.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.report.mapper.ReportDefMapper;
import com.fenglin.report.web.dto.ReportDtos.DatasetQueryRequest;
import com.fenglin.report.web.dto.ReportDtos.DatasetQueryResult;
import com.fenglin.report.web.dto.ReportDtos.ExportRequest;
import com.fenglin.report.web.dto.ReportDtos.ExportResult;
import com.fenglin.report.web.dto.ReportDtos.Filter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HUB-API-1105 报表导出：支持 CSV/EXCEL/PDF/PNG 四种格式，产物经文件服务归档。
 * 水印策略：EXCEL 页脚、PDF/PNG 斜向水印；CSV 不注入水印行以免破坏数据结构（watermark=false）。
 */
@Service
public class ReportExportService {

    private static final Set<String> FORMATS = Set.of("CSV", "EXCEL", "PDF", "PNG");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final ReportDefMapper reportDefMapper;
    private final DatasetQueryService datasetQueryService;
    private final FileServiceClient fileServiceClient;
    private final ObjectMapper objectMapper;
    private final AuditSink auditSink;

    public ReportExportService(ReportDefMapper reportDefMapper,
                               DatasetQueryService datasetQueryService,
                               FileServiceClient fileServiceClient,
                               ObjectMapper objectMapper,
                               AuditSink auditSink) {
        this.reportDefMapper = reportDefMapper;
        this.datasetQueryService = datasetQueryService;
        this.fileServiceClient = fileServiceClient;
        this.objectMapper = objectMapper;
        this.auditSink = auditSink;
    }

    @Transactional
    public ExportResult export(ExportRequest request, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        if (request == null || request.reportId() == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "report_id 不能为空");
        }
        Map<String, Object> definition = reportDefMapper.findById(tenantId, request.reportId());
        if (definition == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "报表不存在: " + request.reportId());
        }
        if ("DASHBOARD".equals(definition.get("report_type"))) {
            throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "看板导出结构未在契约中定义，暂不支持（已标注契约缺口）");
        }
        String format = request.format() == null ? "CSV" : request.format().trim().toUpperCase(Locale.ROOT);
        if (!FORMATS.contains(format)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "format 仅支持 CSV/EXCEL/PDF/PNG");
        }
        JsonNode config = parse(definition.get("config_text"));
        DatasetQueryResult data = datasetQueryService.query(
                config.path("datasetId").asLong(), toQuery(config));

        String watermark = "WATERMARK " + (operatorId == null ? "anonymous" : operatorId)
                + " / tenant " + tenantId + " / " + TS.format(Instant.now());
        byte[] content = switch (format) {
            case "EXCEL" -> excel(definition, data, watermark);
            case "PDF" -> pdf(definition, data, watermark);
            case "PNG" -> png(definition, data, watermark);
            default -> csv(data);
        };
        boolean watermarked = !"CSV".equals(format);
        String filename = "report-" + definition.get("code") + "-" + TS.format(Instant.now())
                + "." + extension(format);
        String fileId = fileServiceClient.upload(filename, content,
                fileServiceClient.contentType(format), tenantId, operatorId);

        TenantContext.Snapshot context = TenantContext.get();
        auditSink.publish(new AuditEvent(UUID.randomUUID().toString(), "REPORT_EXPORTED", "report",
                "EXPORT", "MEDIUM", tenantId, operatorId,
                context == null ? null : context.clientType(),
                context == null ? null : context.requestId(),
                "REPORT", String.valueOf(request.reportId()), Instant.now()));
        return new ExportResult(fileId, fileServiceClient.downloadUrl(fileId), watermarked, format);
    }

    private DatasetQueryRequest toQuery(JsonNode config) {
        List<String> dimensions = new ArrayList<>();
        config.path("dimensions").forEach(node -> dimensions.add(node.asText()));
        List<String> measures = new ArrayList<>();
        config.path("measures").forEach(node -> measures.add(node.asText()));
        List<Filter> filters = new ArrayList<>();
        config.path("filters").forEach(node -> {
            Object value = node.path("value").isNumber()
                    ? node.path("value").numberValue() : node.path("value").asText(null);
            filters.add(new Filter(node.path("field").asText(null), node.path("op").asText("EQ"), value));
        });
        return new DatasetQueryRequest(dimensions, measures, filters,
                config.hasNonNull("limit") ? config.path("limit").asInt() : null);
    }

    private byte[] csv(DatasetQueryResult data) {
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append(line(data.columns()));
        for (List<Object> row : data.rows()) {
            List<String> values = row.stream().map(value -> value == null ? "" : String.valueOf(value)).toList();
            builder.append(line(values));
        }
        return builder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String line(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            String value = values.get(i) == null ? "" : values.get(i);
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                builder.append('"').append(value.replace("\"", "\"\"")).append('"');
            } else {
                builder.append(value);
            }
        }
        return builder.append('\n').toString();
    }

    private byte[] excel(Map<String, Object> definition, DatasetQueryResult data, String watermark) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(String.valueOf(definition.get("name")));
            sheet.getFooter().setCenter(watermark);
            CellStyle header = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = workbook.createFont();
            bold.setBold(true);
            header.setFont(bold);
            Row head = sheet.createRow(0);
            for (int i = 0; i < data.columns().size(); i++) {
                org.apache.poi.ss.usermodel.Cell cell = head.createCell(i);
                cell.setCellValue(data.columns().get(i));
                cell.setCellStyle(header);
            }
            for (int r = 0; r < data.rows().size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Object> values = data.rows().get(r);
                for (int c = 0; c < values.size(); c++) {
                    Object value = values.get(c);
                    if (value instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else {
                        row.createCell(c).setCellValue(value == null ? "" : String.valueOf(value));
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "Excel 生成失败: " + ex.getMessage());
        }
    }

    private byte[] pdf(Map<String, Object> definition, DatasetQueryResult data, String watermark) {
        Document document = new Document(PageSize.A4.rotate());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new WatermarkEvent(watermark));
            document.open();
            document.add(new Paragraph(String.valueOf(definition.get("name")), new Font(Font.HELVETICA, 16)));
            PdfPTable table = new PdfPTable(Math.max(data.columns().size(), 1));
            table.setWidthPercentage(100);
            data.columns().forEach(column -> {
                PdfPCell cell = new PdfPCell(new Phrase(column, new Font(Font.HELVETICA, 10, Font.BOLD)));
                cell.setGrayFill(0.9f);
                table.addCell(cell);
            });
            for (List<Object> row : data.rows()) {
                for (Object value : row) {
                    table.addCell(new Phrase(value == null ? "" : String.valueOf(value),
                            new Font(Font.HELVETICA, 9)));
                }
            }
            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            if (document.isOpen()) {
                document.close();
            }
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "PDF 生成失败: " + ex.getMessage());
        }
    }

    private byte[] png(Map<String, Object> definition, DatasetQueryResult data, String watermark) {
        int rowHeight = 32;
        int width = 1200;
        int height = Math.max(200, rowHeight * (data.rows().size() + 3));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLACK);
            graphics.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 16));
            graphics.drawString(String.valueOf(definition.get("name")), 20, 26);
            graphics.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12));
            int columns = Math.max(data.columns().size(), 1);
            int columnWidth = (width - 40) / columns;
            int y = 60;
            graphics.setColor(new Color(240, 240, 240));
            graphics.fillRect(10, y - 20, width - 20, rowHeight);
            graphics.setColor(Color.BLACK);
            for (int i = 0; i < data.columns().size(); i++) {
                graphics.drawString(data.columns().get(i), 20 + i * columnWidth, y);
            }
            y += rowHeight;
            for (List<Object> row : data.rows()) {
                for (int i = 0; i < row.size(); i++) {
                    Object value = row.get(i);
                    String text = value == null ? "" : String.valueOf(value);
                    graphics.drawString(text.length() > 36 ? text.substring(0, 36) : text,
                            20 + i * columnWidth, y);
                }
                y += rowHeight;
            }
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
            graphics.setColor(Color.GRAY);
            graphics.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 48));
            graphics.rotate(Math.toRadians(-30), width / 2.0, height / 2.0);
            FontMetrics metrics = graphics.getFontMetrics();
            graphics.drawString(watermark, width / 2 - metrics.stringWidth(watermark) / 2, height / 2);
            graphics.rotate(-Math.toRadians(-30), width / 2.0, height / 2.0);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "PNG 生成失败: " + ex.getMessage());
        }
    }

    private String extension(String format) {
        return switch (format) {
            case "EXCEL" -> "xlsx";
            case "PDF" -> "pdf";
            case "PNG" -> "png";
            default -> "csv";
        };
    }

    private JsonNode parse(Object configText) {
        if (configText == null) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree((String) configText);
        } catch (Exception ex) {
            throw new BizException(CommonErrorCode.CONFLICT, "报表配置不是合法 JSON");
        }
    }

    private static final class WatermarkEvent extends PdfPageEventHelper {

        private final String watermark;

        private WatermarkEvent(String watermark) {
            this.watermark = watermark;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfContentByte canvas = writer.getDirectContentUnder();
                canvas.saveState();
                canvas.beginText();
                canvas.setColorFill(new java.awt.Color(200, 200, 200));
                canvas.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI,
                        BaseFont.NOT_EMBEDDED), 30);
                Rectangle page = document.getPageSize();
                canvas.showTextAligned(Element.ALIGN_CENTER, watermark,
                        (page.getLeft() + page.getRight()) / 2,
                        (page.getTop() + page.getBottom()) / 2, 45);
                canvas.endText();
                canvas.restoreState();
            } catch (Exception ignored) {
                // 水印失败不影响导出主流程
            }
        }
    }
}
