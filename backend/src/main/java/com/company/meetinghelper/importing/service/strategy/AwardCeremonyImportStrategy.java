package com.company.meetinghelper.importing.service.strategy;

import com.company.meetinghelper.importing.api.dto.response.AwardRow;
import com.company.meetinghelper.importing.api.dto.response.SheetDescriptor;
import com.company.meetinghelper.importing.api.dto.response.TemplateDescriptor;
import com.company.meetinghelper.importing.service.model.ParsedWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AwardCeremonyImportStrategy extends AbstractParticipantImportStrategy {
    private static final String AWARD_SHEET = "获奖记录";
    private static final List<String> AWARD_HEADERS = List.of(
            "工号", "批次顺序", "批次名称", "奖项名称", "奖项等级", "项目名称", "团队人数"
    );

    @Override
    public TemplateDescriptor descriptor() {
        return new TemplateDescriptor(
                "AWARD_CEREMONY_V1",
                "颁奖会议人员模板",
                "参会人员一人一行；同一人员的多条获奖信息写在获奖记录工作表。",
                1,
                List.of(
                        new SheetDescriptor(PARTICIPANT_SHEET, true, "一人一行"),
                        new SheetDescriptor(AWARD_SHEET, true, "一条获奖记录一行")
                )
        );
    }

    @Override
    protected void customizeScenarioSheets(XSSFWorkbook workbook) {
        var awardSheet = workbook.createSheet(AWARD_SHEET);
        writeHeader(awardSheet, AWARD_HEADERS);
    }

    @Override
    public ParsedWorkbook parse(XSSFWorkbook workbook) {
        var participants = parseParticipants(workbook);
        var errors = new ArrayList<>(participants.errors());
        var awardRows = new ArrayList<AwardRow>();
        var sheet = workbook.getSheet(AWARD_SHEET);
        if (sheet == null) {
            errors.add("缺少必需工作表：获奖记录");
            return new ParsedWorkbook(participants.rows(), awardRows, errors);
        }
        var headers = readHeaders(sheet, errors);
        for (var required : List.of("工号", "批次顺序", "批次名称", "奖项名称")) {
            if (!headers.containsKey(required)) {
                errors.add("获奖记录工作表缺少列：" + required);
            }
        }
        if (!errors.isEmpty() && headers.size() < 4) {
            return new ParsedWorkbook(participants.rows(), awardRows, errors);
        }
        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            var row = sheet.getRow(index);
            if (row == null || text(row, headers.get("工号")).isBlank()) {
                continue;
            }
            var employeeNo = text(row, headers.get("工号")).toUpperCase();
            var batchOrder = integer(row, headers.get("批次顺序"));
            var batchName = text(row, headers.get("批次名称"));
            var awardName = text(row, headers.get("奖项名称"));
            if (batchOrder == null || batchOrder < 1 || batchName.isBlank() || awardName.isBlank()) {
                errors.add("获奖记录第" + (index + 1) + "行批次或奖项信息不完整");
                continue;
            }
            awardRows.add(new AwardRow(
                    index + 1,
                    employeeNo,
                    batchOrder,
                    batchName,
                    awardName,
                    text(row, headers.get("奖项等级")),
                    text(row, headers.get("项目名称")),
                    integer(row, headers.get("团队人数"))
            ));
        }
        return new ParsedWorkbook(participants.rows(), awardRows, errors);
    }
}
