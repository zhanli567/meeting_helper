package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.export.service.ExportPalette;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ExportPaletteTests {
    @Test
    void exhaustedPaletteStillAvoidsReservedElementColors() {
        Set<String> values = IntStream.rangeClosed(1, 25)
                .mapToObj(index -> "值" + index)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, String> colors = ExportPalette.colorsByValue(values, Set.of("#ffffff"));

        assertThat(colors).hasSize(25);
        assertThat(colors.values()).doesNotContain("#ffffff", ExportPalette.SYSTEM_LAYOUT_COLOR);
    }
}
