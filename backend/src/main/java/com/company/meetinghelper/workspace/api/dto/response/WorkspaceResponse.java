package com.company.meetinghelper.workspace.api.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents the workspace response record.
 *
 * @param meeting meeting
 * @param plan plan
 * @param layout layout
 * @param participants participants
 * @param items items
 * @param versions versions
 * @param fieldDefinitions field definitions
 * @param styleRules style rules
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
 * Represents the meeting view record.
 *
 * @param id id
 * @param name name
 * @param status status
 * @param layoutName layout name
 * @param layoutVersion layout version
 * @param updatedAt updated at
 * @param updatedByName updated by name
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
 * Represents the plan view record.
 *
 * @param id id
 * @param name name
 * @param status status
 * @param currentVersionNo current version no
 */
    public record PlanView(String id, String name, String status, int currentVersionNo) {
    }

/**
 * Represents the layout view record.
 *
 * @param gridRows grid rows
 * @param gridColumns grid columns
 * @param elements elements
 */
    public record LayoutView(int gridRows, int gridColumns, List<ElementView> elements) {
    }

/**
 * Represents the element view record.
 *
 * @param id id
 * @param kind kind
 * @param name name
 * @param row row
 * @param column column
 * @param rowSpan row span
 * @param columnSpan column span
 * @param fillColor fill color
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
 * Represents the participant view record.
 *
 * @param id id
 * @param employeeNo employee no
 * @param name name
 * @param primaryAttributes primary attributes
 * @param attributeValues attribute values
 * @param records records
 * @param attendanceStatus attendance status
 * @param locked locked
 * @param assignedElementId assigned element id
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
 * Represents the participant record view record.
 *
 * @param id id
 * @param recordOrder record order
 * @param attributes attributes
 */
    public record ParticipantRecordView(
            String id,
            int recordOrder,
            Map<String, String> attributes
    ) {
    }

/**
 * Represents the plan item view record.
 *
 * @param id id
 * @param type type
 * @param participantId participant id
 * @param label label
 * @param locked locked
 * @param backgroundColor background color
 * @param textColor text color
 * @param bold bold
 * @param targetElementIds target element ids
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
 * Represents the version view record.
 *
 * @param id id
 * @param versionNo version no
 * @param versionName version name
 * @param changeNote change note
 * @param automatic automatic
 * @param assignedCount assigned count
 * @param unassignedCount unassigned count
 * @param createdAt created at
 * @param createdByName created by name
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
 * Represents the field definition view record.
 *
 * @param code code
 * @param label label
 * @param type type
 * @param searchable searchable
 * @param filterable filterable
 * @param sortable sortable
 * @param cardVisible card visible
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
 * Represents the style rule view record.
 *
 * @param fieldCode field code
 * @param value value
 * @param backgroundColor background color
 * @param textColor text color
 */
    public record StyleRuleView(String fieldCode, String value, String backgroundColor, String textColor) {
    }
}
