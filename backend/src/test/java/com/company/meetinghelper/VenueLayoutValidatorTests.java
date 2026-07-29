package com.company.meetinghelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.venue.api.dto.ElementInput;
import com.company.meetinghelper.venue.validation.VenueLayoutValidator;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class VenueLayoutValidatorTests {
    private final VenueLayoutValidator validator = new VenueLayoutValidator();

    @Test
    void acceptsMultiCellSeatAndCountsItAsOneSeat() {
        List<ElementInput> elements = List.of(
                new ElementInput("SEAT", " 双连座 ", 2, 3, 1, 2, "#FFFFFF")
        );

        VenueLayoutValidator.ValidationResult result = validator.validate(5, 5, elements);

        assertEquals(1, result.seatCount());
        assertEquals("双连座", result.elements().getFirst().name());
        assertEquals("#ffffff", result.elements().getFirst().fillColor());
    }

    @Test
    void rejectsOverlap() {
        List<ElementInput> elements = List.of(
                new ElementInput("GENERIC", "舞台", 1, 1, 2, 3, "#dbeafe"),
                new ElementInput("SEAT", "座位", 2, 3, 1, 1, "#ffffff")
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
                "SEAT", "越界座位", 5, 5, 1, 2, "#ffffff"
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
                "SEAT", "  ", 1, 1, 1, 1, "#ffffff"
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
                "TABLE", "桌子", 1, 1, 1, 1, "#ffffff"
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
                "SEAT", "座位", 1, 1, 1, 1, "white"
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
                "SEAT", "溢出座位", 2, 2, Integer.MAX_VALUE, 1, "#ffffff"
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> validator.validate(5, 5, List.of(element))
        );

        assertEquals("元素“溢出座位”超出布局边界", exception.getMessage());
    }

    @Test
    void rejectsOverlapAtMaximumGridSizeWithoutTraversingEveryCell() {
        List<ElementInput> elements = List.of(
                new ElementInput(
                        "GENERIC", "超大区域", 1, 1, Integer.MAX_VALUE, 1,
                        "#dbeafe"
                ),
                new ElementInput(
                        "SEAT", "重叠座位", 1, 1, 1, 1,
                        "#ffffff"
                )
        );

        ApiException exception = assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> assertThrows(
                        ApiException.class,
                        () -> validator.validate(Integer.MAX_VALUE, 5, elements)
                )
        );

        assertEquals("元素“重叠座位”与其他元素发生重叠", exception.getMessage());
    }

    @Test
    void normalizesOnlyFillColorForElements() {
        VenueLayoutValidator.ValidationResult result = validator.validate(5, 5, List.of(
                new ElementInput("GENERIC", "舞台", 1, 1, 1, 2, " #DBEAFE ")
        ));

        assertEquals("#dbeafe", result.elements().getFirst().fillColor());
    }

    @Test
    void allowsSameGenericNameToShareFillColor() {
        VenueLayoutValidator.ValidationResult result = validator.validate(5, 5, List.of(
                new ElementInput("GENERIC", "门", 1, 1, 1, 1, "#dbeafe"),
                new ElementInput("GENERIC", "门", 1, 2, 1, 1, "#dbeafe")
        ));

        assertEquals(2, result.elements().size());
    }

    @Test
    void rejectsDifferentGenericNamesSharingFillColor() {
        ApiException exception = assertThrows(ApiException.class, () -> validator.validate(5, 5, List.of(
                new ElementInput("GENERIC", "门", 1, 1, 1, 1, "#dbeafe"),
                new ElementInput("GENERIC", "桌子", 1, 2, 1, 1, "#dbeafe")
        )));

        assertEquals("非座位元素“门”和“桌子”不能使用相同填充色", exception.getMessage());
    }

    @Test
    void ignoresSeatFillColorWhenCheckingGenericColorUniqueness() {
        VenueLayoutValidator.ValidationResult result = validator.validate(5, 5, List.of(
                new ElementInput("SEAT", "座位", 1, 1, 1, 1, "#dbeafe"),
                new ElementInput("GENERIC", "门", 1, 2, 1, 1, "#dbeafe")
        ));

        assertEquals(2, result.elements().size());
    }
}
