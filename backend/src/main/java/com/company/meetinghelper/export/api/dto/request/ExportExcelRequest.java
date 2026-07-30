package com.company.meetinghelper.export.api.dto.request;

import java.util.List;

public record ExportExcelRequest(
        String versionId,
        SheetSelection sheets
) {
    public SheetSelection normalizedSheets() {
        return sheets == null ? SheetSelection.defaults() : sheets.withDefaults();
    }

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

    public record StyleRule(String fieldCode, String value, String backgroundColor, String textColor) {
    }

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
