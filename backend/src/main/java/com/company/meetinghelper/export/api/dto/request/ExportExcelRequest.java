package com.company.meetinghelper.export.api.dto.request;

import java.util.List;

/**
 * Represents the export excel request record.
 *
 * @param versionId version id
 * @param sheets sheets
 */
public record ExportExcelRequest(
        String versionId,
        SheetSelection sheets
) {
/**
 * Handles normalized sheets.
 *
 * @return result
 */
    public SheetSelection normalizedSheets() {
        return sheets == null ? SheetSelection.defaults() : sheets.withDefaults();
    }

/**
 * Represents the sheet selection record.
 *
 * @param participants participants
 * @param layout layout
 * @param seatDetails seat details
 */
    public record SheetSelection(
            ParticipantSheet participants,
            LayoutSheet layout,
            SeatDetailSheet seatDetails
    ) {
        static SheetSelection defaults() {
            return new SheetSelection(
                    ParticipantSheet.defaults(),
                    LayoutSheet.defaults(),
                    SeatDetailSheet.defaults()
            );
        }

        SheetSelection withDefaults() {
            return new SheetSelection(
                    participants == null ? ParticipantSheet.defaults() : participants.withDefaults(),
                    layout == null ? LayoutSheet.defaults() : layout.withDefaults(),
                    seatDetails == null ? SeatDetailSheet.defaults() : seatDetails.withDefaults()
            );
        }
    }

/**
 * Represents the participant sheet record.
 *
 * @param enabled enabled
 * @param fieldCodes field codes
 * @param includeAttendance include attendance
 * @param includeSeatLabel include seat label
 */
    public record ParticipantSheet(
            Boolean enabled,
            List<String> fieldCodes,
            Boolean includeAttendance,
            Boolean includeSeatLabel
    ) {
        static ParticipantSheet defaults() {
            return new ParticipantSheet(true, null, true, true);
        }

        ParticipantSheet withDefaults() {
            return new ParticipantSheet(
                    enabled == null ? true : enabled,
                    fieldCodes,
                    includeAttendance == null ? true : includeAttendance,
                    includeSeatLabel == null ? true : includeSeatLabel
            );
        }
    }

/**
 * Represents the layout sheet record.
 *
 * @param enabled enabled
 * @param fieldCodes field codes
 * @param colorFieldCodes color field codes
 * @param styleRules style rules
 */
    public record LayoutSheet(
            Boolean enabled,
            List<String> fieldCodes,
            List<String> colorFieldCodes,
            List<StyleRule> styleRules
    ) {
        static LayoutSheet defaults() {
            return new LayoutSheet(true, List.of(), List.of(), List.of());
        }

        public LayoutSheet(Boolean enabled, List<String> fieldCodes, List<String> colorFieldCodes) {
            this(enabled, fieldCodes, colorFieldCodes, List.of());
        }

        LayoutSheet withDefaults() {
            return new LayoutSheet(
                    enabled == null ? true : enabled,
                    fieldCodes == null ? List.of() : fieldCodes,
                    colorFieldCodes == null ? List.of() : colorFieldCodes,
                    styleRules == null ? List.of() : styleRules
            );
        }
    }

/**
 * Represents the style rule record.
 *
 * @param fieldCode field code
 * @param value value
 * @param backgroundColor background color
 * @param textColor text color
 */
    public record StyleRule(String fieldCode, String value, String backgroundColor, String textColor) {
    }

/**
 * Represents the seat detail sheet record.
 *
 * @param enabled enabled
 * @param fieldCodes field codes
 * @param includeOccupancyType include occupancy type
 * @param includeRegionName include region name
 * @param includeParticipant include participant
 */
    public record SeatDetailSheet(
            Boolean enabled,
            List<String> fieldCodes,
            Boolean includeOccupancyType,
            Boolean includeRegionName,
            Boolean includeParticipant
    ) {
        static SeatDetailSheet defaults() {
            return new SeatDetailSheet(true, null, true, true, true);
        }

        SeatDetailSheet withDefaults() {
            return new SeatDetailSheet(
                    enabled == null ? true : enabled,
                    fieldCodes,
                    includeOccupancyType == null ? true : includeOccupancyType,
                    includeRegionName == null ? true : includeRegionName,
                    includeParticipant == null ? true : includeParticipant
            );
        }
    }
}
