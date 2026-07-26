package com.company.meetinghelper.venue.preset;

import com.company.meetinghelper.venue.api.dto.ElementInput;

import java.util.ArrayList;
import java.util.List;

final class PresetVenueCatalog {
    private PresetVenueCatalog() {
    }

    static List<PresetVenueDefinition> definitions() {
        return List.of(auditoriumHall());
    }

    private static PresetVenueDefinition auditoriumHall() {
        var elements = new ArrayList<ElementInput>();
        elements.add(element("STAGE", "STAGE", "舞台", 1, 5, 2, 35,
                false, true, 0, "#DBEAFE", "#93C5FD"));
        elements.add(element("WALL", null, null, 1, 1, 4, 1,
                false, false, 0, "#64748B", "#475569"));
        elements.add(element("WALL", null, null, 6, 1, 13, 1,
                false, false, 0, "#64748B", "#475569"));
        elements.add(element("WALL", null, null, 1, 43, 4, 1,
                false, false, 0, "#64748B", "#475569"));
        elements.add(element("WALL", null, null, 6, 43, 13, 1,
                false, false, 0, "#64748B", "#475569"));
        elements.add(element("DOOR", "LEFT_DOOR", "左前门", 5, 1, 1, 2,
                false, true, 0, "#FED7AA", "#EA580C"));
        elements.add(element("DOOR", "RIGHT_DOOR", "右前门", 5, 42, 1, 2,
                false, true, 0, "#FED7AA", "#EA580C"));
        elements.add(element("AISLE", "FRONT_AISLE", "舞台前通行区", 3, 3, 3, 39,
                false, true, 0, "#F8FAFC", "#E2E8F0"));
        elements.add(element("AISLE", "LEFT_OUTER_AISLE", "左侧走廊", 6, 2, 9, 2,
                false, true, 0, "#F8FAFC", "#E2E8F0"));
        elements.add(element("AISLE", "LEFT_INNER_AISLE", "左中走廊", 6, 11, 9, 2,
                false, true, 0, "#F8FAFC", "#E2E8F0"));
        elements.add(element("AISLE", "RIGHT_INNER_AISLE", "中右走廊", 6, 32, 9, 2,
                false, true, 0, "#F8FAFC", "#E2E8F0"));
        elements.add(element("AISLE", "RIGHT_OUTER_AISLE", "右侧走廊", 6, 41, 9, 2,
                false, true, 0, "#F8FAFC", "#E2E8F0"));

        for (int row = 1; row <= 9; row++) {
            var gridRow = row + 5;
            for (int index = 1; index <= 7; index++) {
                elements.add(seat(row, index, gridRow, 3 + index, 1));
            }
            int centerColumn = 13;
            for (int index = 8; index <= 25; index++) {
                int span = row == 5 && index == 15 ? 2 : 1;
                elements.add(seat(row, index, gridRow, centerColumn, span));
                centerColumn += span;
            }
            for (int index = 26; index <= 32; index++) {
                elements.add(seat(row, index, gridRow, 34 + index - 26, 1));
            }
        }
        elements.add(element("LABEL", "EXIT", "后方出口", 16, 18, 1, 9,
                false, true, 0, "#F1F5F9", "#CBD5E1"));
        return new PresetVenueDefinition(
                "preset-auditorium-hall",
                "多功能礼堂",
                "舞台位于上方，包含左右门、四条纵向通道和九排座位的通用预置场馆。",
                18,
                43,
                34,
                1,
                "TOP",
                List.copyOf(elements)
        );
    }

    private static ElementInput seat(int row, int index, int gridRow, int gridColumn, int columnSpan) {
        return new ElementInput(
                "SEAT",
                row + "排" + String.format("%02d", index),
                null,
                gridRow,
                gridColumn,
                1,
                columnSpan,
                0,
                1,
                true,
                false,
                "ROW_" + row,
                row + "排",
                index,
                "#FFFFFF",
                "#CBD5E1"
        );
    }

    private static ElementInput element(
            String type,
            String code,
            String label,
            int row,
            int column,
            int rowSpan,
            int columnSpan,
            boolean assignable,
            boolean walkable,
            int capacity,
            String background,
            String border
    ) {
        return new ElementInput(
                type, code, label, row, column, rowSpan, columnSpan, 0, capacity,
                assignable, walkable, null, null, null, background, border
        );
    }
}
