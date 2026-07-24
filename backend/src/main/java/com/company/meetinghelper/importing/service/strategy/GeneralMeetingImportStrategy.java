package com.company.meetinghelper.importing.service.strategy;

import com.company.meetinghelper.importing.api.dto.response.SheetDescriptor;
import com.company.meetinghelper.importing.api.dto.response.TemplateDescriptor;
import com.company.meetinghelper.importing.service.model.ParsedWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeneralMeetingImportStrategy extends AbstractParticipantImportStrategy {
    @Override
    public TemplateDescriptor descriptor() {
        return new TemplateDescriptor(
                "GENERAL_V1",
                "通用会议人员模板",
                "适用于没有一对多业务记录的普通会议，可增加自定义人员列。",
                1,
                List.of(new SheetDescriptor(PARTICIPANT_SHEET, true, "一人一行"))
        );
    }

    @Override
    public ParsedWorkbook parse(XSSFWorkbook workbook) {
        var result = parseParticipants(workbook);
        return new ParsedWorkbook(result.rows(), List.of(), result.errors());
    }
}
