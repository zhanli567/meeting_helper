package com.company.meetinghelper.export.api.dto.request;

import java.util.List;

/**
 * ExportExcelRequest 数据结构。
 * @param versionId versionId 参数。
 * @param sheets sheets 参数。
 */
public record ExportExcelRequest(
        String versionId,
        SheetSelection sheets
) {
/**
 * normalizedSheets 方法。
 * @return 返回结果。
 */
public SheetSelection normalizedSheets() {
        return sheets == null ? SheetSelection.defaults() : sheets.withDefaults();
    }

/**
 * SheetSelection 数据结构。
 * @param participants participants 参数。
 * @param layout layout 参数。
 * @param seatDetails seatDetails 参数。
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
 * ParticipantSheet 数据结构。
 * @param enabled enabled 参数。
 * @param fieldCodes fieldCodes 参数。
 * @param includeAttendance includeAttendance 参数。
 * @param includeSeatLabel includeSeatLabel 参数。
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
 * LayoutSheet 数据结构。
 * @param enabled enabled 参数。
 * @param fieldCodes fieldCodes 参数。
 * @param colorFieldCodes colorFieldCodes 参数。
 * @param styleRules styleRules 参数。
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
 * StyleRule 数据结构。
 * @param fieldCode fieldCode 参数。
 * @param value value 参数。
 * @param backgroundColor backgroundColor 参数。
 * @param textColor textColor 参数。
 */
public record StyleRule(String fieldCode, String value, String backgroundColor, String textColor) {
    }

/**
 * SeatDetailSheet 数据结构。
 * @param enabled enabled 参数。
 * @param fieldCodes fieldCodes 参数。
 * @param includeOccupancyType includeOccupancyType 参数。
 * @param includeRegionName includeRegionName 参数。
 * @param includeParticipant includeParticipant 参数。
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
