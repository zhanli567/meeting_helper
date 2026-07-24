package com.company.meetinghelper.importing.service.strategy;

import com.company.meetinghelper.importing.api.dto.response.TemplateDescriptor;
import com.company.meetinghelper.importing.service.model.ParsedWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;

public interface WorkbookImportStrategy {
    /**
     * 返回当前策略的模板说明。
     *
     * @return 模板说明
     */
    TemplateDescriptor descriptor();

    /**
     * 解析已上传的工作簿。
     *
     * @param workbook Excel工作簿
     * @return 解析结果
     */
    ParsedWorkbook parse(XSSFWorkbook workbook);

    /**
     * 向基础模板添加场景工作表及列。
     *
     * @param workbook Excel工作簿
     */
    void customizeTemplate(XSSFWorkbook workbook);

    /**
     * 判断策略是否支持指定模板编码。
     *
     * @param code 模板编码
     * @return 支持时返回true
     */
    default boolean supports(String code) {
        return descriptor().code().equals(code);
    }

    /**
     * 创建包含模板元数据和业务工作表的Excel模板。
     *
     * @return Excel工作簿
     * @throws IOException 模板生成失败时抛出
     */
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
