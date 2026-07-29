package com.company.meetinghelper.venue.validation;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.venue.api.dto.ElementInput;
import com.company.meetinghelper.venue.entity.ElementKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class VenueLayoutValidator {
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    /**
     * 校验并规范化场馆布局。
     *
     * @param gridRows 网格总行数
     * @param gridColumns 网格总列数
     * @param elements 布局元素
     * @return 规范化元素和座位数量
     */
    public ValidationResult validate(int gridRows, int gridColumns, List<ElementInput> elements) {
        if (gridRows < 5 || gridColumns < 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "布局网格不能小于5×5");
        }
        if (elements == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "布局元素不能为空");
        }
        List<OccupiedRectangle> occupiedRectangles = new ArrayList<>(elements.size());
        List<ElementInput> normalizedElements = new ArrayList<>(elements.size());
        int seatCount = 0;
        for (ElementInput source : elements) {
            if (source == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "布局元素不能为空");
            }
            String name = source.name() == null ? "" : source.name().trim();
            if (name.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "元素名称不能为空");
            }
            ElementKind kind;
            try {
                kind = ElementKind.valueOf(source.kind().trim().toUpperCase(Locale.ROOT));
            } catch (NullPointerException | IllegalArgumentException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "元素“" + name + "”的种类无效");
            }
            String fillColor = normalizeColor(source.fillColor(), name);
            long endRow = (long) source.row() + source.rowSpan() - 1L;
            long endColumn = (long) source.column() + source.columnSpan() - 1L;
            if (source.row() < 1 || source.column() < 1
                    || source.rowSpan() < 1 || source.columnSpan() < 1
                    || endRow > gridRows
                    || endColumn > gridColumns) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "元素“" + name + "”超出布局边界");
            }
            OccupiedRectangle rectangle = new OccupiedRectangle(
                    source.row(), endRow, source.column(), endColumn
            );
            for (OccupiedRectangle occupied : occupiedRectangles) {
                if (rectangle.overlaps(occupied)) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "元素“" + name + "”与其他元素发生重叠"
                    );
                }
            }
            occupiedRectangles.add(rectangle);
            normalizedElements.add(new ElementInput(
                    kind.name(), name, source.row(), source.column(), source.rowSpan(),
                    source.columnSpan(), fillColor
            ));
            if (kind == ElementKind.SEAT) {
                seatCount++;
            }
        }
        validateGenericFillColorUniqueness(normalizedElements);
        return new ValidationResult(List.copyOf(normalizedElements), seatCount);
    }

    private void validateGenericFillColorUniqueness(List<ElementInput> elements) {
        LinkedHashMap<String, String> nameByColor = new LinkedHashMap<>();
        for (ElementInput element : elements) {
            if (!ElementKind.GENERIC.name().equals(element.kind())) {
                continue;
            }
            String existingName = nameByColor.putIfAbsent(element.fillColor(), element.name());
            if (existingName != null && !existingName.equals(element.name())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "非座位元素“" + existingName + "”和“" + element.name() + "”不能使用相同填充色"
                );
            }
        }
    }

    private String normalizeColor(String color, String elementName) {
        String normalized = color == null ? "" : color.trim().toLowerCase(Locale.ROOT);
        if (!COLOR_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "元素“" + elementName + "”的颜色格式无效");
        }
        return normalized;
    }

    private record OccupiedRectangle(
            long startRow,
            long endRow,
            long startColumn,
            long endColumn
    ) {
        private boolean overlaps(OccupiedRectangle other) {
            return startRow <= other.endRow
                    && endRow >= other.startRow
                    && startColumn <= other.endColumn
                    && endColumn >= other.startColumn;
        }
    }

    public record ValidationResult(List<ElementInput> elements, int seatCount) {
    }
}
