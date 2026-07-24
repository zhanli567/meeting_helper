package com.company.meetinghelper.workspace.api.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record WorkspaceResponse(
        MeetingView meeting,
        PlanView plan,
        LayoutView layout,
        List<ParticipantView> participants,
        List<PlanItemView> items,
        List<VersionView> versions,
        List<FieldDefinitionView> fieldDefinitions,
        List<StyleRuleView> styleRules
) {
    public record MeetingView(
            String id,
            String name,
            String status,
            String layoutName,
            int layoutVersion,
            OffsetDateTime updatedAt,
            String updatedByName
    ) {
    }

    public record PlanView(String id, String name, String status, int currentVersionNo) {
    }

    public record LayoutView(int gridRows, int gridColumns, int cellSize, List<ElementView> elements) {
    }

    public record ElementView(
            String id,
            String type,
            String code,
            String label,
            int row,
            int column,
            int rowSpan,
            int columnSpan,
            int rotation,
            int capacity,
            boolean assignable,
            boolean walkable,
            String groupCode,
            String groupLabel,
            Integer sequenceNo,
            String backgroundColor,
            String borderColor
    ) {
    }

    public record ParticipantView(
            String id,
            String employeeNo,
            String name,
            Integer level,
            String department,
            String participantType,
            List<String> tags,
            Map<String, String> attributes,
            String attendanceStatus,
            boolean locked,
            String assignedElementId,
            Integer primaryBatchOrder,
            String primaryBatchName,
            String displayColor,
            List<String> repeatedBatches,
            List<AwardView> awards
    ) {
    }

    public record AwardView(
            String id,
            int batchOrder,
            String batchName,
            String awardName,
            String awardLevel,
            String projectName,
            Integer teamSize
    ) {
    }

    public record PlanItemView(
            String id,
            String type,
            String participantId,
            String label,
            boolean locked,
            String backgroundColor,
            String textColor,
            boolean bold,
            List<String> targetElementIds
    ) {
    }

    public record VersionView(
            String id,
            int versionNo,
            String versionName,
            String changeNote,
            boolean automatic,
            int assignedCount,
            int unassignedCount,
            OffsetDateTime createdAt,
            String createdByName
    ) {
    }

    public record FieldDefinitionView(
            String code,
            String label,
            String type,
            boolean searchable,
            boolean filterable,
            boolean sortable,
            boolean cardVisible
    ) {
    }

    public record StyleRuleView(String fieldCode, String value, String backgroundColor, String textColor) {
    }
}
