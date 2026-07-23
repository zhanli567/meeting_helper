package com.company.meetinghelper.export;

import com.company.meetinghelper.api.ApiException;
import com.company.meetinghelper.workspace.WorkspaceResponse;
import com.company.meetinghelper.workspace.WorkspaceService;
import com.company.meetinghelper.seating.PlanVersionService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExportService {
    private final WorkspaceService workspaceService;
    private final PlanVersionService versionService;

    public ExportService(WorkspaceService workspaceService, PlanVersionService versionService) {
        this.workspaceService = workspaceService;
        this.versionService = versionService;
    }

    public byte[] exportExcel(String meetingId, String versionId) {
        var workspace = resolveWorkspace(meetingId, versionId);
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            writeLayoutSheet(workbook, workspace);
            writeSeatDetailSheet(workbook, workspace);
            writeParticipantSheet(workbook, workspace, false);
            writeParticipantSheet(workbook, workspace, true);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "生成Excel失败");
        }
    }

    public byte[] exportExcel(String meetingId) {
        return exportExcel(meetingId, null);
    }

    public byte[] exportPdf(String meetingId, String versionId) {
        var workspace = resolveWorkspace(meetingId, versionId);
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage(new PDRectangle(PDRectangle.A3.getHeight(), PDRectangle.A3.getWidth()));
            document.addPage(page);
            var font = loadChineseFont(document);
            try (var content = new PDPageContentStream(document, page)) {
                drawPdf(content, page.getMediaBox(), font, workspace);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "生成PDF失败");
        }
    }

    public byte[] exportPdf(String meetingId) {
        return exportPdf(meetingId, null);
    }

    private WorkspaceResponse resolveWorkspace(String meetingId, String versionId) {
        return versionId == null || versionId.isBlank()
                ? workspaceService.getWorkspace(meetingId)
                : versionService.getSnapshotForMeeting(meetingId, versionId);
    }

    private void writeLayoutSheet(XSSFWorkbook workbook, WorkspaceResponse workspace) {
        var sheet = workbook.createSheet("排座图");
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(0, 1);
        for (int column = 0; column < workspace.layout().gridColumns(); column++) {
            sheet.setColumnWidth(column, 5 * 256);
        }
        for (int row = 0; row < workspace.layout().gridRows(); row++) {
            sheet.createRow(row).setHeightInPoints(34);
        }
        var participantById = workspace.participants().stream()
                .collect(Collectors.toMap(WorkspaceResponse.ParticipantView::id, Function.identity()));
        var itemByElement = new LinkedHashMap<String, WorkspaceResponse.PlanItemView>();
        workspace.items().forEach(item -> item.targetElementIds().forEach(elementId -> itemByElement.put(elementId, item)));

        for (var element : workspace.layout().elements()) {
            var firstRow = element.row() - 1;
            var firstColumn = element.column() - 1;
            var lastRow = firstRow + element.rowSpan() - 1;
            var lastColumn = firstColumn + element.columnSpan() - 1;
            if (lastRow > firstRow || lastColumn > firstColumn) {
                sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstColumn, lastColumn));
            }
            var cell = sheet.getRow(firstRow).getCell(firstColumn);
            if (cell == null) {
                cell = sheet.getRow(firstRow).createCell(firstColumn);
            }
            var item = itemByElement.get(element.id());
            var participant = item == null || item.participantId() == null
                    ? null
                    : participantById.get(item.participantId());
            cell.setCellValue(elementText(element, item, participant));
            cell.setCellStyle(createElementStyle(workbook, element, item, participant));
        }
    }

    private String elementText(
            WorkspaceResponse.ElementView element,
            WorkspaceResponse.PlanItemView item,
            WorkspaceResponse.ParticipantView participant
    ) {
        if ("SEAT".equals(element.type())) {
            if (participant != null) {
                var batch = participant.primaryBatchName() == null ? "" : "\n" + participant.primaryBatchName();
                var repeats = participant.repeatedBatches().isEmpty()
                        ? ""
                        : "\n复：" + String.join("、", participant.repeatedBatches());
                return nullToEmpty(element.code()) + "\n" + participant.name() + batch + repeats;
            }
            if (item != null) {
                return nullToEmpty(element.code()) + "\n" + nullToEmpty(item.label());
            }
            return nullToEmpty(element.code());
        }
        return nullToEmpty(element.label());
    }

    private XSSFCellStyle createElementStyle(
            XSSFWorkbook workbook,
            WorkspaceResponse.ElementView element,
            WorkspaceResponse.PlanItemView item,
            WorkspaceResponse.ParticipantView participant
    ) {
        var style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        var color = participant != null && participant.displayColor() != null
                ? participant.displayColor()
                : item != null && item.backgroundColor() != null
                ? item.backgroundColor()
                : element.backgroundColor();
        if (color != null) {
            style.setFillForegroundColor(new XSSFColor(parseRgbBytes(color), new DefaultIndexedColorMap()));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        var font = workbook.createFont();
        font.setFontName("Microsoft YaHei");
        font.setFontHeightInPoints((short) 9);
        font.setBold(participant != null || item != null && item.bold());
        style.setFont(font);
        return style;
    }

    private void writeSeatDetailSheet(XSSFWorkbook workbook, WorkspaceResponse workspace) {
        var sheet = workbook.createSheet("座位明细");
        var headers = new String[]{"座位编号", "元素类型", "人员工号", "姓名", "人员类型", "主批次", "重复批次"};
        writeHeaderRow(sheet, headers);
        var participantById = workspace.participants().stream()
                .collect(Collectors.toMap(WorkspaceResponse.ParticipantView::id, Function.identity()));
        var itemByElement = new LinkedHashMap<String, WorkspaceResponse.PlanItemView>();
        workspace.items().forEach(item -> item.targetElementIds().forEach(elementId -> itemByElement.put(elementId, item)));
        int rowIndex = 1;
        for (var element : workspace.layout().elements().stream().filter(WorkspaceResponse.ElementView::assignable).toList()) {
            var row = sheet.createRow(rowIndex++);
            var item = itemByElement.get(element.id());
            var participant = item == null || item.participantId() == null
                    ? null : participantById.get(item.participantId());
            row.createCell(0).setCellValue(nullToEmpty(element.code()));
            row.createCell(1).setCellValue(item == null ? "座位" : item.type());
            row.createCell(2).setCellValue(participant == null ? "" : participant.employeeNo());
            row.createCell(3).setCellValue(participant == null ? "" : participant.name());
            row.createCell(4).setCellValue(participant == null ? "" : nullToEmpty(participant.participantType()));
            row.createCell(5).setCellValue(participant == null ? "" : nullToEmpty(participant.primaryBatchName()));
            row.createCell(6).setCellValue(participant == null ? "" : String.join("、", participant.repeatedBatches()));
        }
        autosize(sheet, headers.length);
    }

    private void writeParticipantSheet(XSSFWorkbook workbook, WorkspaceResponse workspace, boolean onlyUnassigned) {
        var sheet = workbook.createSheet(onlyUnassigned ? "待排人员" : "人员名单");
        var headers = new String[]{"工号", "姓名", "职级", "部门", "人员类型", "主批次", "座位编号"};
        writeHeaderRow(sheet, headers);
        var elementById = workspace.layout().elements().stream()
                .collect(Collectors.toMap(WorkspaceResponse.ElementView::id, Function.identity()));
        int rowIndex = 1;
        for (var participant : workspace.participants()) {
            if (onlyUnassigned && participant.assignedElementId() != null) {
                continue;
            }
            var row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(participant.employeeNo());
            row.createCell(1).setCellValue(participant.name());
            if (participant.level() != null) {
                row.createCell(2).setCellValue(participant.level());
            }
            row.createCell(3).setCellValue(nullToEmpty(participant.department()));
            row.createCell(4).setCellValue(nullToEmpty(participant.participantType()));
            row.createCell(5).setCellValue(nullToEmpty(participant.primaryBatchName()));
            var element = participant.assignedElementId() == null
                    ? null : elementById.get(participant.assignedElementId());
            row.createCell(6).setCellValue(element == null ? "" : nullToEmpty(element.code()));
        }
        autosize(sheet, headers.length);
    }

    private void writeHeaderRow(org.apache.poi.ss.usermodel.Sheet sheet, String[] headers) {
        var row = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            row.createCell(index).setCellValue(headers[index]);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
    }

    private void autosize(org.apache.poi.ss.usermodel.Sheet sheet, int count) {
        for (int index = 0; index < count; index++) {
            sheet.autoSizeColumn(index);
            sheet.setColumnWidth(index, Math.min(sheet.getColumnWidth(index) + 512, 40 * 256));
        }
    }

    private void drawPdf(
            PDPageContentStream content,
            PDRectangle page,
            PDFont font,
            WorkspaceResponse workspace
    ) throws IOException {
        float margin = 28;
        float titleHeight = 34;
        float availableWidth = page.getWidth() - margin * 2;
        float availableHeight = page.getHeight() - margin * 2 - titleHeight;
        float unit = Math.min(
                availableWidth / workspace.layout().gridColumns(),
                availableHeight / workspace.layout().gridRows());
        float originX = margin + (availableWidth - unit * workspace.layout().gridColumns()) / 2;
        float originY = margin;

        content.beginText();
        content.setFont(font, 15);
        content.newLineAtOffset(margin, page.getHeight() - margin - 16);
        content.showText(workspace.meeting().name() + " · " + workspace.plan().name());
        content.endText();

        var participantById = workspace.participants().stream()
                .collect(Collectors.toMap(WorkspaceResponse.ParticipantView::id, Function.identity()));
        var itemByElement = new LinkedHashMap<String, WorkspaceResponse.PlanItemView>();
        workspace.items().forEach(item -> item.targetElementIds().forEach(elementId -> itemByElement.put(elementId, item)));
        for (var element : workspace.layout().elements()) {
            float x = originX + (element.column() - 1) * unit;
            float y = originY + (workspace.layout().gridRows() - element.row() - element.rowSpan() + 1) * unit;
            float width = element.columnSpan() * unit;
            float height = element.rowSpan() * unit;
            var item = itemByElement.get(element.id());
            var participant = item == null || item.participantId() == null
                    ? null : participantById.get(item.participantId());
            var color = participant != null && participant.displayColor() != null
                    ? participant.displayColor()
                    : item != null && item.backgroundColor() != null
                    ? item.backgroundColor()
                    : element.backgroundColor();
            if (color != null) {
                content.setNonStrokingColor(parseAwtColor(color));
                content.addRect(x, y, width, height);
                content.fill();
            }
            content.setStrokingColor(new Color(148, 163, 184));
            content.setLineWidth(0.4f);
            content.addRect(x, y, width, height);
            content.stroke();
            var text = compactText(element, item, participant);
            if (!text.isBlank() && width > 10 && height > 7) {
                content.beginText();
                content.setNonStrokingColor(new Color(30, 41, 59));
                content.setFont(font, Math.max(4.2f, Math.min(7f, unit * 0.26f)));
                content.newLineAtOffset(x + 1.5f, y + height / 2 - 2);
                content.showText(text);
                content.endText();
            }
        }
    }

    private String compactText(
            WorkspaceResponse.ElementView element,
            WorkspaceResponse.PlanItemView item,
            WorkspaceResponse.ParticipantView participant
    ) {
        if ("SEAT".equals(element.type())) {
            if (participant != null) {
                return truncate(element.code() + " " + participant.name(), 18);
            }
            if (item != null) {
                return truncate(element.code() + " " + nullToEmpty(item.label()), 18);
            }
            return truncate(nullToEmpty(element.code()), 12);
        }
        return truncate(nullToEmpty(element.label()), 18);
    }

    private PDFont loadChineseFont(PDDocument document) throws IOException {
        var candidates = new String[]{
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/msyh.ttf",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"
        };
        for (var candidate : candidates) {
            var file = new File(candidate);
            if (file.isFile() && candidate.toLowerCase().endsWith(".ttf")) {
                return PDType0Font.load(document, file);
            }
        }
        throw new IOException("未找到可用于导出中文PDF的字体");
    }

    private byte[] parseRgbBytes(String value) {
        var normalized = value.startsWith("#") ? value.substring(1) : value;
        if (normalized.length() != 6) {
            return new byte[]{(byte) 255, (byte) 255, (byte) 255};
        }
        return new byte[]{
                (byte) Integer.parseInt(normalized.substring(0, 2), 16),
                (byte) Integer.parseInt(normalized.substring(2, 4), 16),
                (byte) Integer.parseInt(normalized.substring(4, 6), 16)
        };
    }

    private Color parseAwtColor(String value) {
        var rgb = parseRgbBytes(value);
        return new Color(Byte.toUnsignedInt(rgb[0]), Byte.toUnsignedInt(rgb[1]), Byte.toUnsignedInt(rgb[2]));
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
