package com.company.meetinghelper.importing.service.strategy;

import com.company.meetinghelper.importing.api.dto.response.ParticipantRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public abstract class AbstractParticipantImportStrategy implements WorkbookImportStrategy {
    protected static final String PARTICIPANT_SHEET = "参会人员";
    protected static final List<String> PARTICIPANT_HEADERS = List.of(
            "工号", "姓名", "职级", "部门", "人员类型", "标签"
    );
    private static final Pattern EMPLOYEE_NO_PATTERN = Pattern.compile("^[A-Za-z][0-9]{8}$");
    private static final Set<String> STANDARD_HEADERS = Set.copyOf(PARTICIPANT_HEADERS);

    protected final DataFormatter formatter = new DataFormatter();

    @Override
    public void customizeTemplate(XSSFWorkbook workbook) {
        var participantSheet = workbook.createSheet(PARTICIPANT_SHEET);
        writeHeader(participantSheet, PARTICIPANT_HEADERS);
        customizeScenarioSheets(workbook);
    }

    protected void customizeScenarioSheets(XSSFWorkbook workbook) {
    }

    protected ParticipantParseResult parseParticipants(XSSFWorkbook workbook) {
        var errors = new ArrayList<String>();
        var sheet = workbook.getSheet(PARTICIPANT_SHEET);
        if (sheet == null) {
            errors.add("缺少必需工作表：参会人员");
            return new ParticipantParseResult(List.of(), errors);
        }
        var headers = readHeaders(sheet, errors);
        if (!headers.containsKey("工号") || !headers.containsKey("姓名")) {
            errors.add("参会人员工作表必须包含“工号”和“姓名”列");
            return new ParticipantParseResult(List.of(), errors);
        }

        var rows = new ArrayList<ParticipantRow>();
        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            var row = sheet.getRow(index);
            if (row == null || isBlank(row)) {
                continue;
            }
            var employeeNo = text(row, headers.get("工号")).toUpperCase();
            var name = text(row, headers.get("姓名"));
            if (!EMPLOYEE_NO_PATTERN.matcher(employeeNo).matches()) {
                errors.add("参会人员第" + (index + 1) + "行工号格式不正确");
                continue;
            }
            if (name.isBlank()) {
                errors.add("参会人员第" + (index + 1) + "行姓名不能为空");
                continue;
            }
            var attributes = new LinkedHashMap<String, String>();
            headers.forEach((header, column) -> {
                if (!STANDARD_HEADERS.contains(header)) {
                    attributes.put(header, text(row, column));
                }
            });
            rows.add(new ParticipantRow(
                    index + 1,
                    employeeNo,
                    name,
                    integer(row, headers.get("职级")),
                    text(row, headers.get("部门")),
                    text(row, headers.get("人员类型")),
                    text(row, headers.get("标签")),
                    attributes
            ));
        }
        return new ParticipantParseResult(rows, errors);
    }

    protected Map<String, Integer> readHeaders(Sheet sheet, List<String> errors) {
        var headers = new LinkedHashMap<String, Integer>();
        var headerRow = sheet.getRow(0);
        if (headerRow == null) {
            errors.add(sheet.getSheetName() + "工作表缺少表头");
            return headers;
        }
        for (Cell cell : headerRow) {
            var header = formatter.formatCellValue(cell).trim();
            if (!header.isBlank()) {
                headers.put(header, cell.getColumnIndex());
            }
        }
        return headers;
    }

    protected void writeHeader(Sheet sheet, List<String> headers) {
        var row = sheet.createRow(0);
        for (int index = 0; index < headers.size(); index++) {
            row.createCell(index).setCellValue(headers.get(index));
            sheet.setColumnWidth(index, Math.max(12, headers.get(index).length() * 4) * 256);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.size() - 1));
    }

    protected String text(Row row, Integer column) {
        if (column == null) {
            return "";
        }
        var cell = row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    protected Integer integer(Row row, Integer column) {
        var value = text(row, column);
        if (value.isBlank()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(value).intValueExact();
        } catch (ArithmeticException | NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isBlank(Row row) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    protected record ParticipantParseResult(
            List<ParticipantRow> rows,
            List<String> errors
    ) {
    }
}
