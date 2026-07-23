package com.company.meetinghelper.importing;

import java.util.List;
import java.util.Map;

public final class ImportModels {
    private ImportModels() {
    }

    public record TemplateDescriptor(
            String code,
            String name,
            String description,
            int version,
            List<SheetDescriptor> sheets
    ) {
    }

    public record SheetDescriptor(String name, boolean required, String rowMeaning) {
    }

    public record ParticipantRow(
            int sourceRow,
            String employeeNo,
            String name,
            Integer level,
            String department,
            String participantType,
            String tags,
            Map<String, String> attributes
    ) {
    }

    public record AwardRow(
            int sourceRow,
            String employeeNo,
            int batchOrder,
            String batchName,
            String awardName,
            String awardLevel,
            String projectName,
            Integer teamSize
    ) {
    }

    public record ParsedWorkbook(
            List<ParticipantRow> participants,
            List<AwardRow> awards,
            List<String> errors
    ) {
    }

    public record DuplicateGroup(String employeeNo, List<ParticipantRow> candidates) {
    }

    public record ImportPreview(
            String token,
            String templateCode,
            int participantRowCount,
            int awardRowCount,
            List<ParticipantRow> uniqueParticipants,
            List<DuplicateGroup> duplicateGroups,
            List<String> errors
    ) {
    }

    public record CommitRequest(Map<String, Integer> selectedSourceRows) {
    }

    public record CommitResult(int inserted, int updated, int awardRecords, int pendingCount) {
    }
}

