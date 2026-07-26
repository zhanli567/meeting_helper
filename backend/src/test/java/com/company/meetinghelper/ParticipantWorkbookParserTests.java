package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.importing.service.ParticipantWorkbookParser;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ParticipantWorkbookParserTests {

    private final ParticipantWorkbookParser parser = new ParticipantWorkbookParser();

    @Test
    void createsTemplateWithOnlyEmployeeNumberAndNameHeaders() throws IOException {
        try (var workbook = parser.createTemplate()) {
            var row = workbook.getSheetAt(0).getRow(0);

            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("工号");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("姓名");
            assertThat(row.getLastCellNum()).isEqualTo((short) 2);
        }
    }

    @Test
    void parsesNonBlankDynamicColumnsInTheirOriginalOrder() {
        var parsed = parser.parse(workbookWith(
                List.of("工号", "姓名", "字段1", "字段2"),
                List.of(
                        List.of("a12345678", "张三", "值1", ""),
                        List.of("a12345678", "张三", "值1", "值2"))));

        assertThat(parsed.fieldNames()).containsExactly("字段1", "字段2");
        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.rows().get(0).attributes()).containsExactly(Map.entry("字段1", "值1"));
        assertThat(parsed.rows().get(1).attributes())
                .containsExactly(Map.entry("字段1", "值1"), Map.entry("字段2", "值2"));
        assertThat(parsed.errors()).isEmpty();
    }

    @Test
    void reportsMissingRequiredHeaders() {
        var parsed = parser.parse(workbookWith(
                List.of("工号", "部门"),
                List.of(List.of("a12345678", "研发部"))));

        assertThat(parsed.errors()).anyMatch(error -> error.contains("姓名"));
    }

    @Test
    void reportsMissingEmployeeNumberHeader() {
        var parsed = parser.parse(workbookWith(
                List.of("姓名", "部门"),
                List.of(List.of("张三", "研发部"))));

        assertThat(parsed.errors()).anyMatch(error -> error.contains("工号"));
    }

    @Test
    void reportsDuplicateHeadersIgnoringEnglishCase() {
        var parsed = parser.parse(workbookWith(
                List.of("工号", "姓名", "Department", "department"),
                List.of(List.of("a12345678", "张三", "研发", "研发"))));

        assertThat(parsed.errors()).anyMatch(error -> error.contains("重复") && error.contains("department"));
    }

    @Test
    void ignoresCompletelyDuplicatedNormalizedRows() {
        var parsed = parser.parse(workbookWith(
                List.of("工号", "姓名", "部门"),
                List.of(
                        List.of(" a12345678 ", " 张三 ", " 研发部 "),
                        List.of("a12345678", "张三", "研发部"))));

        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.ignoredDuplicateRows()).isEqualTo(1);
        assertThat(parsed.totalRows()).isEqualTo(2);
    }

    @Test
    void reportsSameEmployeeNumberWithDifferentNamesAsBlockingError() {
        var parsed = parser.parse(workbookWith(
                List.of("工号", "姓名"),
                List.of(
                        List.of("a12345678", "张三"),
                        List.of("a12345678", "李四"))));

        assertThat(parsed.errors()).anyMatch(error -> error.contains("工号") && error.contains("不同姓名"));
    }

    @Test
    void reportsMalformedEmployeeNumbers() {
        var parsed = parser.parse(workbookWith(
                List.of("工号", "姓名"),
                List.of(List.of("A12345678", "张三"))));

        assertThat(parsed.errors()).anyMatch(error -> error.contains("工号格式"));
    }

    private XSSFWorkbook workbookWith(List<String> headers, List<List<String>> values) {
        var workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet("参会人员");
        var headerRow = sheet.createRow(0);
        for (int column = 0; column < headers.size(); column++) {
            headerRow.createCell(column).setCellValue(headers.get(column));
        }
        for (int rowIndex = 0; rowIndex < values.size(); rowIndex++) {
            var row = sheet.createRow(rowIndex + 1);
            var rowValues = values.get(rowIndex);
            for (int column = 0; column < rowValues.size(); column++) {
                row.createCell(column).setCellValue(rowValues.get(column));
            }
        }
        return workbook;
    }
}
