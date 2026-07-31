package com.company.meetinghelper.importing.service;

import com.company.meetinghelper.importing.service.model.ParsedParticipantRow;
import com.company.meetinghelper.importing.service.model.ParsedParticipantWorkbook;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * ParticipantWorkbookParser 类。
 */
@Component
public class ParticipantWorkbookParser {

    private static final String PARTICIPANT_SHEET = "参会人员";
    private static final String EMPLOYEE_NO_HEADER = "工号";
    private static final String NAME_HEADER = "姓名";

    private final DataFormatter formatter = new DataFormatter();

                /**
         * createTemplate 方法。
         * @return 返回结果。
         */
public XSSFWorkbook createTemplate() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(PARTICIPANT_SHEET);
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue(EMPLOYEE_NO_HEADER);
        headerRow.createCell(1).setCellValue(NAME_HEADER);
        sheet.createFreezePane(0, 1);
        sheet.setColumnWidth(0, 12 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        return workbook;
    }

                /**
         * parse 方法。
         * @param workbook workbook 参数。
         * @return 返回结果。
         */
public ParsedParticipantWorkbook parse(XSSFWorkbook workbook) {
        List<String> errors = new ArrayList<>();
        if (workbook.getNumberOfSheets() == 0) {
            errors.add("Excel 文件缺少工作表");
            return result(List.of(), List.of(), 0, 0, errors);
        }

        Sheet sheet = workbook.getSheetAt(0);
        HeaderResolution headers = readHeaders(sheet, errors);
        if (!headers.isValid()) {
            return result(headers.fieldNames(), List.of(), 0, 0, errors);
        }

        return result(parseRows(sheet, headers, errors));
    }

    private ParsedWorkbookResult parseRows(
            Sheet sheet,
            HeaderResolution headers,
            List<String> errors
    ) {
        List<ParsedParticipantRow> rows = new ArrayList<>();
        Map<String, String> employeeNames = new HashMap<>();
        Set<String> duplicateKeys = new LinkedHashSet<>();
        int totalRows = 0;
        int ignoredDuplicateRows = 0;
        boolean employeeNameConflict = false;

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isBlank(row)) {
                continue;
            }
            totalRows++;
            int sourceRow = rowIndex + 1;
            String employeeNo = text(row, headers.employeeNoColumn());
            String name = text(row, headers.nameColumn());
            if (employeeNo.isBlank()) {
                errors.add("第" + sourceRow + "行工号不能为空");
                continue;
            }
            if (name.isBlank()) {
                errors.add("第" + sourceRow + "行姓名不能为空");
                continue;
            }

            String knownName = employeeNames.putIfAbsent(employeeNo, name);
            if (knownName != null && !knownName.equals(name)) {
                errors.add("工号" + employeeNo + "存在不同姓名：" + knownName + "、" + name);
                employeeNameConflict = true;
            }

            Map<String, String> attributes = attributes(row, headers.extensionHeaders());
            String duplicateKey = duplicateKey(employeeNo, name, headers.fieldNames(), attributes);
            if (!duplicateKeys.add(duplicateKey)) {
                ignoredDuplicateRows++;
                continue;
            }
            rows.add(new ParsedParticipantRow(sourceRow, employeeNo, name, attributes));
        }

        return new ParsedWorkbookResult(
                headers.fieldNames(),
                rows,
                totalRows,
                ignoredDuplicateRows,
                errors,
                employeeNameConflict
        );
    }

    private HeaderResolution readHeaders(Sheet sheet, List<String> errors) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            errors.add("工作表缺少表头");
            return HeaderResolution.invalid();
        }

        Map<String, Integer> columnsByHeader = new LinkedHashMap<>();
        List<Header> extensionHeaders = new ArrayList<>();
        Set<String> normalizedHeaders = new LinkedHashSet<>();
        for (int column = 0; column < headerRow.getLastCellNum(); column++) {
            String header = text(headerRow, column);
            if (header.isBlank()) {
                continue;
            }
            String normalizedHeader = header.toLowerCase(Locale.ROOT);
            if (!normalizedHeaders.add(normalizedHeader)) {
                errors.add("表头重复：" + normalizedHeader);
                continue;
            }
            columnsByHeader.put(header, column);
            if (!EMPLOYEE_NO_HEADER.equals(header) && !NAME_HEADER.equals(header)) {
                extensionHeaders.add(new Header(header, column));
            }
        }

        if (!columnsByHeader.containsKey(EMPLOYEE_NO_HEADER)) {
            errors.add("缺少必填表头：工号");
        }
        if (!columnsByHeader.containsKey(NAME_HEADER)) {
            errors.add("缺少必填表头：姓名");
        }
        if (!errors.isEmpty()) {
            return new HeaderResolution(null, null, extensionHeaders);
        }
        return new HeaderResolution(
                columnsByHeader.get(EMPLOYEE_NO_HEADER),
                columnsByHeader.get(NAME_HEADER),
                extensionHeaders);
    }

    private Map<String, String> attributes(Row row, List<Header> headers) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (Header header : headers) {
            String value = text(row, header.column());
            if (!value.isBlank()) {
                attributes.put(header.name(), value);
            }
        }
        return Collections.unmodifiableMap(attributes);
    }

    private String duplicateKey(
            String employeeNo,
            String name,
            List<String> fieldNames,
            Map<String, String> attributes
    ) {
        return employeeNo + "\u001F" + name + "\u001F" + fieldNames.stream()
                .map(field -> field + "=" + attributes.getOrDefault(field, ""))
                .collect(Collectors.joining("\u001E"));
    }

    private ParsedParticipantWorkbook result(
            List<String> fieldNames,
            List<ParsedParticipantRow> rows,
            int totalRows,
            int ignoredDuplicateRows,
            List<String> errors
    ) {
        return result(new ParsedWorkbookResult(
                fieldNames,
                rows,
                totalRows,
                ignoredDuplicateRows,
                errors,
                false
        ));
    }

    private ParsedParticipantWorkbook result(ParsedWorkbookResult result) {
        return new ParsedParticipantWorkbook(
                List.copyOf(result.fieldNames()),
                List.copyOf(result.rows()),
                result.totalRows(),
                result.ignoredDuplicateRows(),
                List.copyOf(result.errors()),
                result.employeeNameConflict());
    }

    private boolean isBlank(Row row) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).trim().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String text(Row row, int column) {
        Cell cell = row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private record Header(String name, int column) {
    }

    private record ParsedWorkbookResult(
            List<String> fieldNames,
            List<ParsedParticipantRow> rows,
            int totalRows,
            int ignoredDuplicateRows,
            List<String> errors,
            boolean employeeNameConflict
    ) {
    }

    private record HeaderResolution(
            Integer employeeNoColumn,
            Integer nameColumn,
            List<Header> extensionHeaders
    ) {
        static HeaderResolution invalid() {
            return new HeaderResolution(null, null, List.of());
        }

        boolean isValid() {
            return employeeNoColumn != null && nameColumn != null;
        }

        List<String> fieldNames() {
            return extensionHeaders.stream().map(Header::name).toList();
        }
    }
}
