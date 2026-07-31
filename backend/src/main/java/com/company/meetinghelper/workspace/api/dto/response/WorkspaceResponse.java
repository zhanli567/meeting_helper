package com.company.meetinghelper.workspace.api.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * WorkspaceResponse 数据结构。
 * @param meeting meeting 参数。
 * @param plan plan 参数。
 * @param layout layout 参数。
 * @param participants participants 参数。
 * @param items items 参数。
 * @param versions versions 参数。
 * @param fieldDefinitions fieldDefinitions 参数。
 * @param styleRules styleRules 参数。
 */
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
/**
 * MeetingView 数据结构。
 * @param id id 参数。
 * @param name name 参数。
 * @param status status 参数。
 * @param layoutName layoutName 参数。
 * @param layoutVersion layoutVersion 参数。
 * @param updatedAt updatedAt 参数。
 * @param updatedByName updatedByName 参数。
 */
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

/**
 * PlanView 数据结构。
 * @param id id 参数。
 * @param name name 参数。
 * @param status status 参数。
 * @param currentVersionNo currentVersionNo 参数。
 */
public record PlanView(String id, String name, String status, int currentVersionNo) {
    }

/**
 * LayoutView 数据结构。
 * @param gridRows gridRows 参数。
 * @param gridColumns gridColumns 参数。
 * @param elements elements 参数。
 */
public record LayoutView(int gridRows, int gridColumns, List<ElementView> elements) {
    }

/**
 * ElementView 数据结构。
 * @param id id 参数。
 * @param kind kind 参数。
 * @param name name 参数。
 * @param row row 参数。
 * @param column column 参数。
 * @param rowSpan rowSpan 参数。
 * @param columnSpan columnSpan 参数。
 * @param fillColor fillColor 参数。
 */
public record ElementView(
            String id,
            String kind,
            String name,
            int row,
            int column,
            int rowSpan,
            int columnSpan,
            String fillColor
    ) {
    }

/**
 * ParticipantView 数据结构。
 * @param id id 参数。
 * @param employeeNo employeeNo 参数。
 * @param name name 参数。
 * @param primaryAttributes primaryAttributes 参数。
 * @param attributeValues attributeValues 参数。
 * @param records records 参数。
 * @param attendanceStatus attendanceStatus 参数。
 * @param locked locked 参数。
 * @param assignedElementId assignedElementId 参数。
 */
public record ParticipantView(
            String id,
            String employeeNo,
            String name,
            Map<String, String> primaryAttributes,
            Map<String, List<String>> attributeValues,
            List<ParticipantRecordView> records,
            String attendanceStatus,
            boolean locked,
            String assignedElementId
    ) {
    }

/**
 * ParticipantRecordView 数据结构。
 * @param id id 参数。
 * @param recordOrder recordOrder 参数。
 * @param attributes attributes 参数。
 */
public record ParticipantRecordView(
            String id,
            int recordOrder,
            Map<String, String> attributes
    ) {
    }

/**
 * PlanItemView 数据结构。
 * @param id id 参数。
 * @param type type 参数。
 * @param participantId participantId 参数。
 * @param label label 参数。
 * @param locked locked 参数。
 * @param backgroundColor backgroundColor 参数。
 * @param textColor textColor 参数。
 * @param bold bold 参数。
 * @param targetElementIds targetElementIds 参数。
 */
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

/**
 * VersionView 数据结构。
 * @param id id 参数。
 * @param versionNo versionNo 参数。
 * @param versionName versionName 参数。
 * @param changeNote changeNote 参数。
 * @param automatic automatic 参数。
 * @param assignedCount assignedCount 参数。
 * @param unassignedCount unassignedCount 参数。
 * @param createdAt createdAt 参数。
 * @param createdByName createdByName 参数。
 */
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

/**
 * FieldDefinitionView 数据结构。
 * @param code code 参数。
 * @param label label 参数。
 * @param type type 参数。
 * @param searchable searchable 参数。
 * @param filterable filterable 参数。
 * @param sortable sortable 参数。
 * @param cardVisible cardVisible 参数。
 */
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

/**
 * StyleRuleView 数据结构。
 * @param fieldCode fieldCode 参数。
 * @param value value 参数。
 * @param backgroundColor backgroundColor 参数。
 * @param textColor textColor 参数。
 */
public record StyleRuleView(String fieldCode, String value, String backgroundColor, String textColor) {
    }
}
