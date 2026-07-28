package com.company.meetinghelper.export.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.meeting.service.MeetingAccessService;
import com.company.meetinghelper.seating.service.SeatLabelService;
import com.company.meetinghelper.seating.service.PlanVersionService;
import com.company.meetinghelper.venue.entity.ElementKind;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse.ElementView;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse.FieldDefinitionView;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse.ParticipantRecordView;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse.ParticipantView;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse.PlanItemView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
    private final PlanVersionService versionService;
    private final MeetingAccessService meetingAccessService;
    private final SeatLabelService seatLabelService;

    /**
     * 创建导出服务。
     *
     * @param versionService 版本服务
     * @param meetingAccessService 会议归属校验服务
     * @param seatLabelService 动态座位编号服务
     */
    public ExportService(
            PlanVersionService versionService,
            MeetingAccessService meetingAccessService,
            SeatLabelService seatLabelService
    ) {
        this.versionService = versionService;
        this.meetingAccessService = meetingAccessService;
        this.seatLabelService = seatLabelService;
    }

    /**
     * 将会议发布版本导出为Excel。
     *
     * @param meetingId 会议ID
     * @param versionId 发布版本ID
     * @return Excel文件字节
     */
    public byte[] exportExcel(String meetingId, String versionId) {
        WorkspaceResponse workspace = resolveWorkspace(meetingId, versionId);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeParticipantSheet(workbook, workspace);
            writeLayoutSheet(workbook, workspace);
            writeSeatDetailSheet(workbook, workspace);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "生成Excel失败");
        }
    }

    private WorkspaceResponse resolveWorkspace(String meetingId, String versionId) {
        meetingAccessService.requireOwnedMeeting(meetingId);
        if (versionId == null || versionId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "草稿版本不支持导出，请先发布版本");
        }
        return versionService.getSnapshotForMeeting(meetingId, versionId);
    }

    private void writeLayoutSheet(XSSFWorkbook workbook, WorkspaceResponse workspace) {
        XSSFSheet sheet = workbook.createSheet("排座图");
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(0, 1);
        for (int column = 0; column < workspace.layout().gridColumns(); column++) {
            sheet.setColumnWidth(column, 5 * 256);
        }
        for (int row = 0; row < workspace.layout().gridRows(); row++) {
            sheet.createRow(row).setHeightInPoints(34);
        }
        Map<String,ParticipantView> participantById = workspace.participants().stream()
                .collect(Collectors.toMap(WorkspaceResponse.ParticipantView::id, Function.identity()));
        LinkedHashMap<String,PlanItemView> itemByElement = new LinkedHashMap<String, WorkspaceResponse.PlanItemView>();
        workspace.items().forEach(item -> item.targetElementIds().forEach(elementId -> itemByElement.put(elementId, item)));
        Map<String,String> seatLabels = seatLabelService.labelsByElementId(workspace.layout().elements());

        for (ElementView element : workspace.layout().elements()) {
            int firstRow = element.row() - 1;
            int firstColumn = element.column() - 1;
            int lastRow = firstRow + element.rowSpan() - 1;
            int lastColumn = firstColumn + element.columnSpan() - 1;
            if (lastRow > firstRow || lastColumn > firstColumn) {
                sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstColumn, lastColumn));
            }
            XSSFCell cell = sheet.getRow(firstRow).getCell(firstColumn);
            if (cell == null) {
                cell = sheet.getRow(firstRow).createCell(firstColumn);
            }
            PlanItemView item = itemByElement.get(element.id());
            ParticipantView participant = item == null || item.participantId() == null
                    ? null
                    : participantById.get(item.participantId());
            cell.setCellValue(elementText(element, item, participant, seatLabels));
            cell.setCellStyle(createElementStyle(workbook, element, item, participant));
        }
    }

    private String elementText(
            WorkspaceResponse.ElementView element,
            WorkspaceResponse.PlanItemView item,
            WorkspaceResponse.ParticipantView participant,
            Map<String,String> seatLabels
    ) {
        if (isSeat(element)) {
            String seatLabel = seatLabels.getOrDefault(element.id(), nullToEmpty(element.name()));
            if (participant != null) {
                return seatLabel + "\n" + participant.name();
            }
            if (item != null) {
                return seatLabel + "\n" + nullToEmpty(item.label());
            }
            return seatLabel;
        }
        return nullToEmpty(element.name());
    }

    private XSSFCellStyle createElementStyle(
            XSSFWorkbook workbook,
            WorkspaceResponse.ElementView element,
            WorkspaceResponse.PlanItemView item,
            WorkspaceResponse.ParticipantView participant
    ) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        String color = item != null && item.backgroundColor() != null
                ? item.backgroundColor()
                : element.fillColor();
        if (color != null) {
            style.setFillForegroundColor(new XSSFColor(parseRgbBytes(color), new DefaultIndexedColorMap()));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        XSSFFont font = workbook.createFont();
        font.setFontName("Microsoft YaHei");
        font.setFontHeightInPoints((short) 9);
        font.setBold(participant != null || item != null && item.bold());
        style.setFont(font);
        return style;
    }

    private void writeSeatDetailSheet(XSSFWorkbook workbook, WorkspaceResponse workspace) {
        XSSFSheet sheet = workbook.createSheet("座位明细");
        List<FieldDefinitionView> participantFields = dynamicFields(workspace);
        ArrayList<String> headers = new ArrayList<>(List.of("座位编号", "元素类型", "人员工号", "姓名"));
        participantFields.forEach(field -> headers.add(field.label()));
        writeHeaderRow(sheet, headers.toArray(String[]::new));
        Map<String,ParticipantView> participantById = workspace.participants().stream()
                .collect(Collectors.toMap(WorkspaceResponse.ParticipantView::id, Function.identity()));
        LinkedHashMap<String,PlanItemView> itemByElement = new LinkedHashMap<String, WorkspaceResponse.PlanItemView>();
        workspace.items().forEach(item -> item.targetElementIds().forEach(elementId -> itemByElement.put(elementId, item)));
        Map<String,String> seatLabels = seatLabelService.labelsByElementId(workspace.layout().elements());
        int rowIndex = 1;
        for (ElementView element : workspace.layout().elements().stream()
                .filter(this::isSeat)
                .toList()) {
            XSSFRow row = sheet.createRow(rowIndex++);
            PlanItemView item = itemByElement.get(element.id());
            ParticipantView participant = item == null || item.participantId() == null
                    ? null : participantById.get(item.participantId());
            row.createCell(0).setCellValue(seatLabels.getOrDefault(element.id(), nullToEmpty(element.name())));
            row.createCell(1).setCellValue(itemTypeLabel(item));
            row.createCell(2).setCellValue(participant == null ? "" : participant.employeeNo());
            row.createCell(3).setCellValue(participant == null ? "" : participant.name());
            for (int fieldIndex = 0; fieldIndex < participantFields.size(); fieldIndex++) {
                String value = participant == null
                        ? ""
                        : primaryAttributes(participant).getOrDefault(
                                participantFields.get(fieldIndex).code(),
                                ""
                        );
                row.createCell(4 + fieldIndex).setCellValue(value);
            }
        }
        autosize(sheet, headers.size());
    }

    private String itemTypeLabel(PlanItemView item) {
        if (item == null) {
            return "座位";
        }
        return switch (item.type()) {
            case "PERSON" -> "已排人员";
            case "RESERVED" -> "区域标记";
            default -> item.type();
        };
    }

    private void writeParticipantSheet(XSSFWorkbook workbook, WorkspaceResponse workspace) {
        XSSFSheet sheet = workbook.createSheet("人员名单");
        List<FieldDefinitionView> participantFields = dynamicFields(workspace);
        ArrayList<String> headers = new ArrayList<>(List.of("工号", "姓名"));
        participantFields.forEach(field -> headers.add(field.label()));
        writeHeaderRow(sheet, headers.toArray(String[]::new));
        int rowIndex = 1;
        for (ParticipantView participant : workspace.participants()) {
            List<ParticipantRecordView> records = participant.records() == null
                    ? List.<WorkspaceResponse.ParticipantRecordView>of()
                    : participant.records();
            if (records.isEmpty()) {
                writeParticipantRow(
                        sheet.createRow(rowIndex++),
                        participant,
                        participantFields,
                        Map.of()
                );
                continue;
            }
            for (ParticipantRecordView record : records) {
                writeParticipantRow(
                        sheet.createRow(rowIndex++),
                        participant,
                        participantFields,
                        record.attributes() == null ? Map.of() : record.attributes()
                );
            }
        }
        autosize(sheet, headers.size());
    }

    private void writeParticipantRow(
            Row row,
            WorkspaceResponse.ParticipantView participant,
            List<WorkspaceResponse.FieldDefinitionView> participantFields,
            Map<String, String> attributes
    ) {
        row.createCell(0).setCellValue(participant.employeeNo());
        row.createCell(1).setCellValue(participant.name());
        for (int fieldIndex = 0; fieldIndex < participantFields.size(); fieldIndex++) {
            row.createCell(2 + fieldIndex).setCellValue(attributes.getOrDefault(
                    participantFields.get(fieldIndex).code(),
                    ""
            ));
        }
    }

    private void writeHeaderRow(Sheet sheet, String[] headers) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            row.createCell(index).setCellValue(headers[index]);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
    }

    private void autosize(Sheet sheet, int count) {
        for (int index = 0; index < count; index++) {
            sheet.autoSizeColumn(index);
            sheet.setColumnWidth(index, Math.min(sheet.getColumnWidth(index) + 512, 40 * 256));
        }
    }

    private boolean isSeat(WorkspaceResponse.ElementView element) {
        return ElementKind.SEAT.name().equals(element.kind());
    }

    private List<WorkspaceResponse.FieldDefinitionView> dynamicFields(
            WorkspaceResponse workspace
    ) {
        if (workspace.fieldDefinitions() == null) {
            return List.of();
        }
        return workspace.fieldDefinitions().stream()
                .filter(field -> !"name".equals(field.code())
                        && !"employeeNo".equals(field.code()))
                .toList();
    }

    private Map<String, String> primaryAttributes(
            WorkspaceResponse.ParticipantView participant
    ) {
        return participant.primaryAttributes() == null
                ? Map.of()
                : participant.primaryAttributes();
    }

    private byte[] parseRgbBytes(String value) {
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        if (normalized.length() != 6) {
            return new byte[]{(byte) 255, (byte) 255, (byte) 255};
        }
        return new byte[]{
                (byte) Integer.parseInt(normalized.substring(0, 2), 16),
                (byte) Integer.parseInt(normalized.substring(2, 4), 16),
                (byte) Integer.parseInt(normalized.substring(4, 6), 16)
        };
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
