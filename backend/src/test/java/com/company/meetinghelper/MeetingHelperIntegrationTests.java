package com.company.meetinghelper;

import com.company.meetinghelper.export.service.ExportService;
import com.company.meetinghelper.importing.service.ImportService;
import com.company.meetinghelper.meeting.api.dto.request.CreateMeetingRequest;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.meeting.service.MeetingService;
import com.company.meetinghelper.participant.api.dto.request.CreateParticipantRequest;
import com.company.meetinghelper.participant.api.dto.request.UpdateAttendanceRequest;
import com.company.meetinghelper.participant.entity.AttendanceStatus;
import com.company.meetinghelper.participant.service.ParticipantService;
import com.company.meetinghelper.seating.api.dto.request.AssignmentRequest;
import com.company.meetinghelper.seating.api.dto.request.AssignmentInput;
import com.company.meetinghelper.seating.api.dto.request.CreateVersionRequest;
import com.company.meetinghelper.seating.api.dto.request.SaveAssignmentsRequest;
import com.company.meetinghelper.seating.service.PlanVersionService;
import com.company.meetinghelper.seating.service.SeatingService;
import com.company.meetinghelper.venue.api.dto.ElementInput;
import com.company.meetinghelper.venue.api.dto.request.CreateVenueRequest;
import com.company.meetinghelper.venue.service.VenueService;
import com.company.meetinghelper.workspace.service.WorkspaceService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MeetingHelperIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

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

    @Autowired
    private VenueService venueService;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private ParticipantService participantService;

    @Test
    void controllerRoutesDoNotExposeApiPrefixAndAllowLocalNetworkOrigins() throws Exception {
        mockMvc.perform(get("/meetings"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/meetings"))
                .andExpect(status().isNotFound());
        mockMvc.perform(options("/meetings")
                        .header("Origin", "http://192.168.10.25:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.10.25:5173"));
    }

    @Test
    void temporarilyAbsentParticipantReleasesSeatAndDoesNotBlockPublishing() {
        var venue = venueService.list().getFirst();
        var meeting = meetingService.create(new CreateMeetingRequest(
                "临时不出席测试-" + java.util.UUID.randomUUID(),
                venue.id()
        ));
        var participant = participantService.create(
                meeting.id(),
                new CreateParticipantRequest(
                        "12345678", "测试人员", 10, "测试部门", "参会人员", "", java.util.Map.of(), null
                )
        );
        var workspace = workspaceService.getWorkspace(meeting.id());
        var seat = workspace.layout().elements().stream()
                .filter(value -> value.assignable() && value.capacity() == 1)
                .findFirst()
                .orElseThrow();
        seatingService.assign(
                workspace.plan().id(),
                new AssignmentRequest(participant.id(), seat.id())
        );

        participantService.updateAttendance(
                meeting.id(),
                participant.id(),
                new UpdateAttendanceRequest(AttendanceStatus.TEMPORARILY_ABSENT)
        );

        var absentWorkspace = workspaceService.getWorkspace(meeting.id());
        var absentParticipant = absentWorkspace.participants().getFirst();
        assertThat(absentParticipant.attendanceStatus()).isEqualTo("TEMPORARILY_ABSENT");
        assertThat(absentParticipant.assignedElementId()).isNull();
        assertThat(planVersionService.create(
                absentWorkspace.plan().id(),
                new CreateVersionRequest("临时不出席版本", "", false)
        ).unassignedCount()).isZero();
    }

    @Test
    void creatingParticipantFromEmptySeatAssignsTheNewParticipantAtomically() throws Exception {
        var venue = venueService.list().getFirst();
        var meeting = meetingService.create(new CreateMeetingRequest(
                "空座新增人员测试-" + java.util.UUID.randomUUID(),
                venue.id()
        ));
        var before = workspaceService.getWorkspace(meeting.id());
        var seat = before.layout().elements().stream()
                .filter(value -> value.assignable() && value.capacity() == 1)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/meetings/{meetingId}/participants", meeting.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeNo": "87654321",
                                  "name": "直接落座人员",
                                  "level": 12,
                                  "department": "测试部门",
                                  "participantType": "参会人员",
                                  "tags": "",
                                  "attributes": {},
                                  "targetElementId": "%s"
                                }
                                """.formatted(seat.id())))
                .andExpect(status().isOk());

        var after = workspaceService.getWorkspace(meeting.id());
        var participant = after.participants().stream()
                .filter(value -> value.employeeNo().equals("87654321"))
                .findFirst()
                .orElseThrow();
        assertThat(participant.assignedElementId()).isEqualTo(seat.id());
    }

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

        seatingService.assign(before.plan().id(), new AssignmentRequest(participant.id(), seat.id()));

        var after = workspaceService.getWorkspace(meeting.getId());
        assertThat(after.participants().stream()
                .filter(value -> value.id().equals(participant.id()))
                .findFirst().orElseThrow().assignedElementId()).isEqualTo(seat.id());
    }

    @Test
    void twoAssignedParticipantsCanSwapSeatsWithoutViolatingUniqueConstraint() {
        var meeting = meetingRepository.findAllByDeletedFalseOrderByUpdatedAtDesc().getFirst();
        var before = workspaceService.getWorkspace(meeting.getId());
        var assigned = before.participants().stream()
                .filter(value -> value.assignedElementId() != null && !value.locked())
                .limit(2)
                .toList();
        var first = assigned.get(0);
        var second = assigned.get(1);

        seatingService.assign(
                before.plan().id(),
                new AssignmentRequest(first.id(), second.assignedElementId())
        );

        var after = workspaceService.getWorkspace(meeting.getId());
        var participantById = after.participants().stream()
                .collect(java.util.stream.Collectors.toMap(value -> value.id(), value -> value));
        assertThat(participantById.get(first.id()).assignedElementId())
                .isEqualTo(second.assignedElementId());
        assertThat(participantById.get(second.id()).assignedElementId())
                .isEqualTo(first.assignedElementId());
    }

    @Test
    void completeAssignmentSetCanBeSavedInOneBatch() {
        var meeting = meetingRepository.findAllByDeletedFalseOrderByUpdatedAtDesc().getFirst();
        var before = workspaceService.getWorkspace(meeting.getId());
        var assigned = before.participants().stream()
                .filter(value -> value.assignedElementId() != null && !value.locked())
                .limit(2)
                .toList();
        var first = assigned.get(0);
        var second = assigned.get(1);
        var assignments = before.participants().stream()
                .filter(value -> value.assignedElementId() != null)
                .map(value -> {
                    if (value.id().equals(first.id())) {
                        return new AssignmentInput(value.id(), second.assignedElementId());
                    }
                    if (value.id().equals(second.id())) {
                        return new AssignmentInput(value.id(), first.assignedElementId());
                    }
                    return new AssignmentInput(value.id(), value.assignedElementId());
                })
                .toList();

        seatingService.replaceAssignments(
                before.plan().id(),
                new SaveAssignmentsRequest(assignments)
        );

        var after = workspaceService.getWorkspace(meeting.getId());
        var participantById = after.participants().stream()
                .collect(java.util.stream.Collectors.toMap(value -> value.id(), value -> value));
        assertThat(participantById.get(first.id()).assignedElementId())
                .isEqualTo(second.assignedElementId());
        assertThat(participantById.get(second.id()).assignedElementId())
                .isEqualTo(first.assignedElementId());
    }

    @Test
    void savedVersionCanRestorePreviousSeatingState() {
        var meeting = meetingRepository.findAllByDeletedFalseOrderByUpdatedAtDesc().getFirst();
        var before = workspaceService.getWorkspace(meeting.getId());

        assertThatThrownBy(() -> planVersionService.create(before.plan().id(),
                new CreateVersionRequest("未完成版本", "仍有待排人员", false)))
                .hasMessageContaining("全部完成排座后才能发布");

        var occupied = before.items().stream()
                .flatMap(item -> item.targetElementIds().stream())
                .collect(java.util.stream.Collectors.toSet());
        var emptySeats = before.layout().elements().stream()
                .filter(value -> value.assignable() && !occupied.contains(value.id()))
                .iterator();
        before.participants().stream()
                .filter(value -> value.assignedElementId() == null)
                .forEach(participant -> seatingService.assign(
                        before.plan().id(),
                        new AssignmentRequest(participant.id(), emptySeats.next().id())
                ));

        var readyToPublish = workspaceService.getWorkspace(meeting.getId());
        var saved = planVersionService.create(before.plan().id(),
                new CreateVersionRequest("恢复测试版本", "全部人员已完成排座", false));
        var immutableSnapshot = planVersionService.getSnapshot(before.plan().id(), saved.id());
        assertThat(immutableSnapshot.participants().stream()
                .filter(value -> value.assignedElementId() != null)
                .count()).isEqualTo(28);

        var participant = readyToPublish.participants().getFirst();
        seatingService.unassign(before.plan().id(), participant.id());
        assertThat(workspaceService.getWorkspace(meeting.getId()).participants().stream()
                .filter(value -> value.assignedElementId() != null)
                .count()).isEqualTo(27);
        assertThat(planVersionService.getSnapshot(before.plan().id(), saved.id()).participants().stream()
                .filter(value -> value.assignedElementId() != null)
                .count()).isEqualTo(28);
        assertThat(exportService.exportExcel(meeting.getId(), saved.id()).length).isGreaterThan(5_000);

        planVersionService.restore(before.plan().id(), saved.id());

        var restored = workspaceService.getWorkspace(meeting.getId());
        assertThat(restored.plan().currentVersionNo()).isEqualTo(saved.versionNo());
        assertThat(restored.participants().stream()
                .filter(value -> value.assignedElementId() != null)
                .count()).isEqualTo(28);
    }

    @Test
    void publishedVersionNamesAreUniqueWithinASeatingPlan() {
        var venue = venueService.list().getFirst();
        var meeting = meetingService.create(new CreateMeetingRequest(
                "版本名称判重测试-" + java.util.UUID.randomUUID(),
                venue.id()
        ));
        var workspace = workspaceService.getWorkspace(meeting.id());

        var published = planVersionService.create(
                workspace.plan().id(),
                new CreateVersionRequest("  Final  ", "", false)
        );

        assertThat(published.versionName()).isEqualTo("Final");
        assertThatThrownBy(() -> planVersionService.create(
                workspace.plan().id(),
                new CreateVersionRequest("final", "", false)
        ))
                .hasMessageContaining("版本名称已存在");
    }

    @Test
    void draftWorkspaceCannotBeExported() throws Exception {
        var venue = venueService.list().getFirst();
        var meeting = meetingService.create(new CreateMeetingRequest(
                "草稿导出限制测试-" + java.util.UUID.randomUUID(),
                venue.id()
        ));

        assertThatThrownBy(() -> exportService.exportExcel(meeting.id(), null))
                .hasMessageContaining("草稿版本不支持导出");
        mockMvc.perform(get("/meetings/{meetingId}/exports/excel", meeting.id()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void awardTemplateAndExportsAreGenerated() throws Exception {
        var template = importService.templateFile("AWARD_CEREMONY_V1");
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            assertThat(workbook.getSheet("参会人员")).isNotNull();
            assertThat(workbook.getSheet("获奖记录")).isNotNull();
        }

        var venue = venueService.list().getFirst();
        var meeting = meetingService.create(new CreateMeetingRequest(
                "发布版本导出测试-" + java.util.UUID.randomUUID(),
                venue.id()
        ));
        var workspace = workspaceService.getWorkspace(meeting.id());
        var version = planVersionService.create(
                workspace.plan().id(),
                new CreateVersionRequest("导出确认版", "", false)
        );
        assertThat(exportService.exportExcel(meeting.id(), version.id()).length).isGreaterThan(5_000);
        assertThat(exportService.exportPdf(meeting.id(), version.id()).length).isGreaterThan(5_000);
    }

    @Test
    void customVenueSupportsDuplicateCheckUpdateAndSoftDelete() {
        var name = "测试场馆-" + java.util.UUID.randomUUID();
        var request = new CreateVenueRequest(
                name,
                "用于验证场馆管理",
                5,
                5,
                34,
                "TOP",
                java.util.List.of(new ElementInput(
                        "SEAT", "1排01", "座位", 1, 1, 1, 1, 0, 1,
                        true, false, null, null, 1, "#ffffff", "#93b4df")));

        var created = venueService.create(request);
        assertThatThrownBy(() -> venueService.create(request))
                .hasMessage("场馆名称已存在");

        var renamed = new CreateVenueRequest(
                name + "-修改",
                request.description(),
                request.gridRows(),
                request.gridColumns(),
                request.cellSize(),
                request.frontDirection(),
                request.elements());
        var updated = venueService.update(created.id(), renamed);
        assertThat(updated.versionNo()).isEqualTo(2);
        assertThat(updated.name()).isEqualTo(name + "-修改");

        venueService.delete(created.id());
        assertThat(venueService.list()).noneMatch(venue -> venue.id().equals(created.id()));
        assertThat(venueService.create(renamed).name()).isEqualTo(name + "-修改");
    }

    @Test
    void meetingNameIsCheckedOnBothCreateAttempts() {
        var venue = venueService.list().getFirst();
        var name = "测试会议-" + java.util.UUID.randomUUID();
        var request = new CreateMeetingRequest(name, venue.id());

        meetingService.create(request);

        assertThatThrownBy(() -> meetingService.create(request))
                .hasMessage("会议名称已存在");
    }
}
