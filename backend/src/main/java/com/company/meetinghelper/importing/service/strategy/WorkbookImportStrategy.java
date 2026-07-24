package com.company.meetinghelper.importing.service.strategy;

import com.company.meetinghelper.importing.api.dto.response.TemplateDescriptor;
import com.company.meetinghelper.importing.service.model.ParsedWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;

public interface WorkbookImportStrategy {
    TemplateDescriptor descriptor();

    ParsedWorkbook parse(XSSFWorkbook workbook);

    void customizeTemplate(XSSFWorkbook workbook);

    default boolean supports(String code) {
        return descriptor().code().equals(code);
    }

    default XSSFWorkbook createTemplate() throws IOException {
        var workbook = new XSSFWorkbook();
        var metadata = workbook.createSheet("_模板信息");
        metadata.createRow(0).createCell(0).setCellValue("模板编码");
        metadata.getRow(0).createCell(1).setCellValue(descriptor().code());
        metadata.createRow(1).createCell(0).setCellValue("模板版本");
        metadata.getRow(1).createCell(1).setCellValue(descriptor().version());
        workbook.setSheetHidden(workbook.getSheetIndex(metadata), true);
        customizeTemplate(workbook);
        return workbook;
    }
}
