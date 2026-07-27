package com.company.meetinghelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.venue.api.dto.ElementInput;
import com.company.meetinghelper.venue.validation.VenueLayoutValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

class VenueLayoutValidatorTests {
    private final VenueLayoutValidator validator = new VenueLayoutValidator();

    @Test
    void acceptsMultiCellSeatAndCountsItAsOneSeat() {
        List<ElementInput> elements = List.of(
                new ElementInput("SEAT", " 双连座 ", 2, 3, 1, 2, "#FFFFFF", "#8FB4E8")
        );

        VenueLayoutValidator.ValidationResult result = validator.validate(5, 5, elements);

        assertEquals(1, result.seatCount());
        assertEquals("双连座", result.elements().getFirst().name());
        assertEquals("#ffffff", result.elements().getFirst().fillColor());
    }

    @Test
    void rejectsOverlap() {
        List<ElementInput> elements = List.of(
                new ElementInput("GENERIC", "舞台", 1, 1, 2, 3, "#dbeafe", "#93c5fd"),
                new ElementInput("SEAT", "座位", 2, 3, 1, 1, "#ffffff", "#8fb4e8")
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> validator.validate(5, 5, elements)
        );

        assertEquals("元素“座位”与其他元素发生重叠", exception.getMessage());
    }

    @Test
    void rejectsOutOfBounds() {
        ElementInput element = new ElementInput(
                "SEAT", "越界座位", 5, 5, 1, 2, "#ffffff", "#8fb4e8"
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> validator.validate(5, 5, List.of(element))
        );

        assertEquals("元素“越界座位”超出布局边界", exception.getMessage());
    }

    @Test
    void rejectsGridSmallerThanFiveByFive() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> validator.validate(4, 5, List.of())
        );

        assertEquals("布局网格不能小于5×5", exception.getMessage());
    }

    @Test
    void rejectsBlankElementName() {
        ElementInput element = new ElementInput(
                "SEAT", "  ", 1, 1, 1, 1, "#ffffff", "#8fb4e8"
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> validator.validate(5, 5, List.of(element))
        );

        assertEquals("元素名称不能为空", exception.getMessage());
    }

    @Test
    void rejectsInvalidKind() {
        ElementInput element = new ElementInput(
                "TABLE", "桌子", 1, 1, 1, 1, "#ffffff", "#8fb4e8"
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> validator.validate(5, 5, List.of(element))
        );

        assertEquals("元素“桌子”的种类无效", exception.getMessage());
    }

    @Test
    void rejectsInvalidColors() {
        ElementInput element = new ElementInput(
                "SEAT", "座位", 1, 1, 1, 1, "white", "#8fb4e8"
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> validator.validate(5, 5, List.of(element))
        );

        assertEquals("元素“座位”的颜色格式无效", exception.getMessage());
    }

    @Test
    void rejectsNullElementWithoutServerError() {
        List<ElementInput> elements = new java.util.ArrayList<ElementInput>();
        elements.add(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> validator.validate(5, 5, elements)
        );

        assertEquals("布局元素不能为空", exception.getMessage());
    }

    @Test
    void rejectsSpanThatWouldOverflowIntegerCoordinates() {
        ElementInput element = new ElementInput(
                "SEAT", "溢出座位", 2, 2, Integer.MAX_VALUE, 1, "#ffffff", "#8fb4e8"
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> validator.validate(5, 5, List.of(element))
        );

        assertEquals("元素“溢出座位”超出布局边界", exception.getMessage());
    }
}
