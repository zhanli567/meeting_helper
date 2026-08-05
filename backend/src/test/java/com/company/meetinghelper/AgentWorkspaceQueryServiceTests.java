package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.agent.tool.query.AgentWorkspaceQueryService;
import com.company.meetinghelper.agent.tool.query.AgentPageResult;
import com.company.meetinghelper.agent.tool.query.ParticipantBrief;
import com.company.meetinghelper.agent.tool.query.SeatBrief;
import com.company.meetinghelper.agent.tool.query.WorkspaceSummaryResult;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;

/** 智能体工作区只读查询服务测试。 */
class AgentWorkspaceQueryServiceTests {

    @Test
    void summarizeCountsWorkspace() {
        AgentWorkspaceQueryService service = new AgentWorkspaceQueryService(meetingId -> workspaceFixture());

        WorkspaceSummaryResult summary = service.summarize("meeting-1");

        assertThat(summary.meetingId()).isEqualTo("meeting-1");
        assertThat(summary.participantCount()).isEqualTo(3);
        assertThat(summary.seatCount()).isEqualTo(2);
        assertThat(summary.assignedCount()).isEqualTo(1);
        assertThat(summary.unassignedCount()).isEqualTo(2);
        assertThat(summary.lockedCount()).isEqualTo(1);
    }

    @Test
    void listUnassignedFiltersByKeyword() {
        AgentWorkspaceQueryService service = new AgentWorkspaceQueryService(meetingId -> workspaceFixture());

        AgentPageResult<ParticipantBrief> result = service.listUnassigned("meeting-1", "李", 10);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).extracting("name").containsExactly("李四");
    }

    @Test
    void searchParticipantsMatchesEmployeeNoStatusAndPrimaryAttributes() {
        AgentWorkspaceQueryService service = new AgentWorkspaceQueryService(meetingId -> workspaceFixture());

        AgentPageResult<ParticipantBrief> employeeResult = service.searchParticipants("meeting-1", "002", 10);
        AgentPageResult<ParticipantBrief> statusResult = service.searchParticipants("meeting-1", "ABSENT", 10);
        AgentPageResult<ParticipantBrief> attributeResult = service.searchParticipants("meeting-1", "技术部", 10);

        assertThat(employeeResult.items()).extracting(ParticipantBrief::name).containsExactly("李四");
        assertThat(statusResult.items()).extracting(ParticipantBrief::name).containsExactly("王五");
        assertThat(attributeResult.items()).extracting(ParticipantBrief::name).containsExactly("李四");
    }

    @Test
    void searchSeatsMatchesSeatIdentityAndOccupant() {
        AgentWorkspaceQueryService service = new AgentWorkspaceQueryService(meetingId -> workspaceFixture());

        AgentPageResult<SeatBrief> idResult = service.searchSeats("meeting-1", "seat-a1", 10);
        AgentPageResult<SeatBrief> nameResult = service.searchSeats("meeting-1", "A2", 10);
        AgentPageResult<SeatBrief> occupantNameResult = service.searchSeats("meeting-1", "张三", 10);
        AgentPageResult<SeatBrief> occupantEmployeeResult = service.searchSeats("meeting-1", "001", 10);

        assertThat(idResult.items()).extracting(SeatBrief::id).containsExactly("seat-a1");
        assertThat(nameResult.items()).extracting(SeatBrief::name).containsExactly("A2");
        assertThat(occupantNameResult.items()).extracting(SeatBrief::occupiedParticipantName)
                .containsExactly("张三");
        assertThat(occupantEmployeeResult.items()).extracting(SeatBrief::occupiedEmployeeNo)
                .containsExactly("001");
    }

    @Test
    void listQueriesClampLimitBetweenOneAndOneHundred() {
        AgentWorkspaceQueryService service = new AgentWorkspaceQueryService(meetingId -> workspaceFixture());

        AgentPageResult<ParticipantBrief> lowerBound = service.searchParticipants("meeting-1", null, 0);
        AgentPageResult<ParticipantBrief> upperBound = service.searchParticipants("meeting-1", null, 101);

        assertThat(lowerBound.returned()).isEqualTo(1);
        assertThat(upperBound.returned()).isEqualTo(3);
    }

    @Test
    void listQueriesNeverReturnsMoreThanOneHundredItems() {
        AgentWorkspaceQueryService service = new AgentWorkspaceQueryService(meetingId -> workspaceWithManyParticipants());

        AgentPageResult<ParticipantBrief> result = service.searchParticipants("meeting-1", null, 101);

        assertThat(result.total()).isEqualTo(125);
        assertThat(result.returned()).isEqualTo(100);
        assertThat(result.items()).hasSize(100);
    }

    @Test
    void participantBriefDoesNotExposeRecords() {
        AgentWorkspaceQueryService service = new AgentWorkspaceQueryService(meetingId -> workspaceFixture());

        AgentPageResult<ParticipantBrief> result = service.searchParticipants("meeting-1", "李", 10);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().attributes()).containsEntry("部门", "技术部");
        assertThat(ParticipantBrief.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("records");
    }

    private static WorkspaceResponse workspaceFixture() {
        return new WorkspaceResponse(
                new WorkspaceResponse.MeetingView("meeting-1", "经营会", "DRAFT", "一号厅", 1, null, "秘书"),
                new WorkspaceResponse.PlanView("plan-1", "默认方案", "DRAFT", 0),
                new WorkspaceResponse.LayoutView(10, 10, List.of(
                        new WorkspaceResponse.ElementView("seat-a1", "SEAT", "A1", 1, 1, 1, 1, "#fff"),
                        new WorkspaceResponse.ElementView("seat-a2", "SEAT", "A2", 1, 2, 1, 1, "#fff")
                )),
                List.of(
                        new WorkspaceResponse.ParticipantView("p1", "001", "张三", Map.of(), Map.of(), List.of(), "PRESENT", true, "seat-a1"),
                        new WorkspaceResponse.ParticipantView("p2", "002", "李四", Map.of("部门", "技术部"), Map.of(), List.of(), "PRESENT", false, null),
                        new WorkspaceResponse.ParticipantView("p3", "003", "王五", Map.of(), Map.of(), List.of(), "ABSENT", false, null)
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static WorkspaceResponse workspaceWithManyParticipants() {
        WorkspaceResponse fixture = workspaceFixture();
        List<WorkspaceResponse.ParticipantView> participants = IntStream.range(0, 125)
                .mapToObj(index -> new WorkspaceResponse.ParticipantView(
                        "p-" + index,
                        String.format("%03d", index),
                        "人员" + index,
                        Map.of(),
                        Map.of(),
                        List.of(),
                        "PRESENT",
                        false,
                        null
                ))
                .toList();
        return new WorkspaceResponse(
                fixture.meeting(),
                fixture.plan(),
                fixture.layout(),
                participants,
                fixture.items(),
                fixture.versions(),
                fixture.fieldDefinitions(),
                fixture.styleRules()
        );
    }
}
