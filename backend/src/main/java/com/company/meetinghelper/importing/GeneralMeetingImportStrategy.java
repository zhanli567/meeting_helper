package com.company.meetinghelper.importing;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeneralMeetingImportStrategy extends AbstractParticipantImportStrategy {
    @Override
    public ImportModels.TemplateDescriptor descriptor() {
        return new ImportModels.TemplateDescriptor(
                "GENERAL_V1",
                "通用会议人员模板",
                "适用于没有一对多业务记录的普通会议，可增加自定义人员列。",
                1,
                List.of(new ImportModels.SheetDescriptor(PARTICIPANT_SHEET, true, "一人一行"))
        );
    }

    @Override
    public ImportModels.ParsedWorkbook parse(XSSFWorkbook workbook) {
        var result = parseParticipants(workbook);
        return new ImportModels.ParsedWorkbook(result.rows(), List.of(), result.errors());
    }
}

