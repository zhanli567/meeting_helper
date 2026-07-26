package com.company.meetinghelper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.company.meetinghelper.participant.service.ParticipantRecordMerger;
import com.company.meetinghelper.participant.service.ParticipantRecordMerger.Action;
import com.company.meetinghelper.participant.service.ParticipantRecordMerger.RecordValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParticipantRecordMergerTests {

    private final ParticipantRecordMerger merger = new ParticipantRecordMerger();

    @Test
    void enrichesTheOnlyCompatibleRecord() {
        var existing = List.of(new RecordValue("r1", 1, Map.of("字段1", "值1")));

        var result = merger.decide(
                Map.of("字段1", "值1", "字段2", "值2"),
                existing);

        assertEquals(Action.MERGE, result.action());
        assertEquals("r1", result.targetRecordId());
        assertEquals(Map.of("字段1", "值1", "字段2", "值2"), result.mergedAttributes());
    }

    @Test
    void skipsIncomingDataAlreadyContainedByARecord() {
        var existing = List.of(new RecordValue(
                "r1", 1, Map.of("字段1", "值1", "字段2", "值2")));

        var result = merger.decide(Map.of("字段1", "值1"), existing);

        assertEquals(Action.SKIP, result.action());
        assertEquals("r1", result.targetRecordId());
        assertEquals(Map.of("字段1", "值1", "字段2", "值2"), result.mergedAttributes());
    }

    @Test
    void appendsWhenSharedFieldsConflict() {
        var existing = List.of(new RecordValue(
                "r1", 1, Map.of("批次", "第二批", "奖项", "优秀项目奖")));

        var result = merger.decide(
                Map.of("批次", "第三批", "奖项", "创新奖"), existing);

        assertEquals(Action.APPEND, result.action());
        assertEquals(null, result.targetRecordId());
        assertEquals(Map.of("批次", "第三批", "奖项", "创新奖"), result.mergedAttributes());
    }

    @Test
    void appendsWhenSeveralRecordsAreCompatibleButTargetIsAmbiguous() {
        var existing = List.of(
                new RecordValue("r1", 1, Map.of("批次", "第二批")),
                new RecordValue("r2", 2, Map.of("批次", "第三批")));

        var result = merger.decide(Map.of("部门", "研发部"), existing);

        assertEquals(Action.APPEND, result.action());
        assertEquals(null, result.targetRecordId());
        assertEquals(Map.of("部门", "研发部"), result.mergedAttributes());
    }

    @Test
    void normalizesWhitespaceAndDiscardsBlankKeysAndValuesBeforeDeciding() {
        var existing = List.of(new RecordValue(
                "r1", 1, Map.of(" 部门 ", " 研发部 ", "空值", "   ")));

        var result = merger.decide(
                Map.of(" 部门 ", " 研发部 ", " ", "忽略", "无效", " "),
                existing);

        assertEquals(Action.SKIP, result.action());
        assertEquals("r1", result.targetRecordId());
        assertEquals(Map.of("部门", "研发部"), result.mergedAttributes());
    }
}
