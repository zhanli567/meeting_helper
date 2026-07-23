package com.company.meetinghelper;

import com.company.meetinghelper.export.ExportService;
import com.company.meetinghelper.importing.ImportService;
import com.company.meetinghelper.meeting.MeetingRepository;
import com.company.meetinghelper.seating.PlanVersionService;
import com.company.meetinghelper.seating.SeatingService;
import com.company.meetinghelper.workspace.WorkspaceService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MeetingHelperIntegrationTests {
    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private SeatingService seatingService;

    @Autowired
    private PlanVersionService planVersionService;

    @Autowired
    private ImportService importService;

    @Autowired
    private ExportService exportService;

    @Test
    void workspaceContainsRealisticDemoLayoutAndParticipants() {
        var meeting = meetingRepository.findAllByDeletedFalseOrderByUpdatedAtDesc().getFirst();
        var workspace = workspaceService.getWorkspace(meeting.getId());

        assertThat(workspace.layout().gridRows()).isEqualTo(18);
        assertThat(workspace.layout().gridColumns()).isEqualTo(43);
        assertThat(workspace.layout().elements()).hasSizeGreaterThan(290);
        assertThat(workspace.participants()).hasSize(28);
        assertThat(workspace.participants().stream()
                .filter(participant -> participant.assignedElementId() != null)
                .count()).isEqualTo(12);
    }

    @Test
    void unassignedParticipantCanBeMovedIntoAnEmptySeat() {
        var meeting = meetingRepository.findAllByDeletedFalseOrderByUpdatedAtDesc().getFirst();
        var before = workspaceService.getWorkspace(meeting.getId());
        var participant = before.participants().stream()
                .filter(value -> value.assignedElementId() == null)
                .findFirst().orElseThrow();
        var occupied = before.items().stream()
                .flatMap(item -> item.targetElementIds().stream())
                .collect(java.util.stream.Collectors.toSet());
        var seat = before.layout().elements().stream()
                .filter(value -> value.assignable() && !occupied.contains(value.id()))
                .findFirst().orElseThrow();

        seatingService.assign(before.plan().id(), new SeatingService.AssignmentRequest(participant.id(), seat.id()));

        var after = workspaceService.getWorkspace(meeting.getId());
        assertThat(after.participants().stream()
                .filter(value -> value.id().equals(participant.id()))
                .findFirst().orElseThrow().assignedElementId()).isEqualTo(seat.id());
    }

    @Test
    void savedVersionCanRestorePreviousSeatingState() {
        var meeting = meetingRepository.findAllByDeletedFalseOrderByUpdatedAtDesc().getFirst();
        var before = workspaceService.getWorkspace(meeting.getId());
        var saved = planVersionService.create(before.plan().id(),
                new PlanVersionService.CreateVersionRequest("恢复测试版本", "保存当前排座", false));
        var participant = before.participants().stream()
                .filter(value -> value.assignedElementId() == null)
                .findFirst().orElseThrow();
        var occupied = before.items().stream()
                .flatMap(item -> item.targetElementIds().stream())
                .collect(java.util.stream.Collectors.toSet());
        var seat = before.layout().elements().stream()
                .filter(value -> value.assignable() && !occupied.contains(value.id()))
                .findFirst().orElseThrow();

        seatingService.assign(before.plan().id(),
                new SeatingService.AssignmentRequest(participant.id(), seat.id()));
        assertThat(workspaceService.getWorkspace(meeting.getId()).participants().stream()
                .filter(value -> value.assignedElementId() != null)
                .count()).isEqualTo(13);

        planVersionService.restore(before.plan().id(), saved.id());

        var restored = workspaceService.getWorkspace(meeting.getId());
        assertThat(restored.plan().currentVersionNo()).isEqualTo(saved.versionNo());
        assertThat(restored.participants().stream()
                .filter(value -> value.assignedElementId() != null)
                .count()).isEqualTo(12);
    }

    @Test
    void awardTemplateAndExportsAreGenerated() throws Exception {
        var template = importService.templateFile("AWARD_CEREMONY_V1");
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            assertThat(workbook.getSheet("参会人员")).isNotNull();
            assertThat(workbook.getSheet("获奖记录")).isNotNull();
        }

        var meeting = meetingRepository.findAllByDeletedFalseOrderByUpdatedAtDesc().getFirst();
        assertThat(exportService.exportExcel(meeting.getId()).length).isGreaterThan(5_000);
        assertThat(exportService.exportPdf(meeting.getId()).length).isGreaterThan(5_000);
    }
}
