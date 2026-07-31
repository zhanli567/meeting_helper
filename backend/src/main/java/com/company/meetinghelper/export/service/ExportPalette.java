package com.company.meetinghelper.export.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ExportPalette 类。
 */
public final class ExportPalette {
    public static final String SYSTEM_LAYOUT_COLOR = "#e5edf8";
    public static final List<String> SEMANTIC_COLORS = List.of(
            "#fee2e2", "#ffe4e6", "#ffedd5", "#fef3c7", "#fef9c3", "#ecfccb",
            "#dcfce7", "#d1fae5", "#ccfbf1", "#cffafe", "#e0f2fe", "#dbeafe",
            "#e0e7ff", "#ede9fe", "#f3e8ff", "#fce7f3", "#fed7aa", "#fde68a",
            "#bbf7d0", "#a7f3d0", "#bfdbfe", "#c7d2fe", "#fbcfe8", "#f1f5f9"
    );

    private ExportPalette() {
    }

/**
 * colorsByValue 方法。
 * @param values values 参数。
 * @param reservedColors reservedColors 参数。
 * @return 返回结果。
 */
public static Map<String, String> colorsByValue(Set<String> values, Set<String> reservedColors) {
        LinkedHashSet<String> reserved = new LinkedHashSet<String>(reservedColors);
        reserved.add(SYSTEM_LAYOUT_COLOR);
        LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
        int index = 0;
        int generatedIndex = 0;
        for (String value : values) {
            while (index < SEMANTIC_COLORS.size() && reserved.contains(SEMANTIC_COLORS.get(index))) {
                index++;
            }
            String color;
            if (index < SEMANTIC_COLORS.size()) {
                color = SEMANTIC_COLORS.get(index);
            } else {
                do {
                    color = generatedColor(generatedIndex++);
                } while (reserved.contains(color));
            }
            result.put(value, color);
            reserved.add(color);
            index++;
        }
        return result;
    }

    private static String generatedColor(int index) {
        int slot = (index * 40_503) & 0x3ffff;
        int red = 192 + ((slot >>> 12) & 0x3f);
        int green = 192 + ((slot >>> 6) & 0x3f);
        int blue = 192 + (slot & 0x3f);
        return "#%02x%02x%02x".formatted(red, green, blue);
    }
}
