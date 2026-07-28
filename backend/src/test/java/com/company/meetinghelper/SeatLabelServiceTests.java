package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.seating.service.SeatLabelService;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeatLabelServiceTests {
    private final SeatLabelService service = new SeatLabelService();

    @Test
    void labelsSeatsByOccupiedRowsThenColumns() {
        List<WorkspaceResponse.ElementView> elements = List.of(
                element("stage", "GENERIC", 1, 1),
                element("seat-b", "SEAT", 3, 4),
                element("seat-a", "SEAT", 3, 2),
                element("seat-c", "SEAT", 6, 1)
        );

        assertThat(service.labelsByElementId(elements))
                .containsEntry("seat-a", "1排1")
                .containsEntry("seat-b", "1排2")
                .containsEntry("seat-c", "2排1")
                .doesNotContainKey("stage");
        assertThat(service.rowLabels(elements))
                .extracting(SeatLabelService.RowLabel::sourceRow)
                .containsExactly(3, 6);
    }

    private WorkspaceResponse.ElementView element(String id, String kind, int row, int column) {
        return new WorkspaceResponse.ElementView(id, kind, "x", row, column, 1, 1, "#fff", "#aaa");
    }
}
