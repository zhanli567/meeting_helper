package com.company.meetinghelper;

import static java.util.Locale.ROOT;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.meetinghelper.common.context.CurrentUserHolder;
import com.company.meetinghelper.common.security.CurrentUser;
import com.company.meetinghelper.export.service.ExportService;
import com.company.meetinghelper.meeting.api.dto.request.CreateMeetingRequest;
import com.company.meetinghelper.meeting.api.dto.request.UpdateMeetingNameRequest;
import com.company.meetinghelper.meeting.api.dto.response.MeetingSummary;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.meeting.service.MeetingService;
import com.company.meetinghelper.participant.api.dto.request.CreateParticipantRequest;
import com.company.meetinghelper.participant.api.dto.request.UpdateAttendanceRequest;
import com.company.meetinghelper.participant.api.dto.response.ParticipantResult;
import com.company.meetinghelper.participant.entity.AttendanceStatus;
import com.company.meetinghelper.participant.entity.MeetingParticipantFieldEntity;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import com.company.meetinghelper.participant.entity.ParticipantRecordEntity;
import com.company.meetinghelper.participant.repository.MeetingParticipantFieldRepository;
import com.company.meetinghelper.participant.repository.ParticipantRecordRepository;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import com.company.meetinghelper.participant.service.ParticipantFieldRegistrationService;
import com.company.meetinghelper.participant.service.ParticipantService;
import com.company.meetinghelper.seating.api.dto.request.AssignmentInput;
import com.company.meetinghelper.seating.api.dto.request.AssignmentRequest;
import com.company.meetinghelper.seating.api.dto.request.CreateVersionRequest;
import com.company.meetinghelper.seating.api.dto.request.SaveAssignmentsRequest;
import com.company.meetinghelper.seating.api.dto.response.VersionResult;
import com.company.meetinghelper.seating.entity.PlanVersionEntity;
import com.company.meetinghelper.seating.entity.PlanItemType;
import com.company.meetinghelper.seating.repository.PlanItemRepository;
import com.company.meetinghelper.seating.repository.PlanVersionRepository;
import com.company.meetinghelper.seating.service.PlanVersionService;
import com.company.meetinghelper.seating.service.SeatingService;
import com.company.meetinghelper.support.PostgreSqlTestDatabaseInitializer;
import com.company.meetinghelper.venue.api.dto.ElementInput;
import com.company.meetinghelper.venue.api.dto.request.CreateVenueRequest;
import com.company.meetinghelper.venue.api.dto.request.UpdateVenueInfoRequest;
import com.company.meetinghelper.venue.api.dto.request.UpdateVenueLayoutRequest;
import com.company.meetinghelper.venue.api.dto.response.VenueDetail;
import com.company.meetinghelper.venue.api.dto.response.VenueLayout;
import com.company.meetinghelper.venue.api.dto.response.VenueSummary;
import com.company.meetinghelper.venue.repository.VenueElementRepository;
import com.company.meetinghelper.venue.service.VenueService;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse.ElementView;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse.ParticipantView;
import com.company.meetinghelper.workspace.service.WorkspaceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.sql.DataSource;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MeetingHelperIntegrationTests.TestUserConfiguration.class)
@ContextConfiguration(initializers = PostgreSqlTestDatabaseInitializer.class)
@ExtendWith(OutputCaptureExtension.class)
class MeetingHelperIntegrationTests {
    private static final String USER_HEADER = "X-User-Id";
    private static final String DEFAULT_USER = "demo-secretary";

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
    private PlanVersionRepository planVersionRepository;

    @Autowired
    private ExportService exportService;

    @Autowired
    private VenueService venueService;

    @Autowired
    private VenueElementRepository venueElementRepository;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private ParticipantService participantService;

    @SpyBean
    private ParticipantFieldRegistrationService fieldRegistrationService;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private ParticipantRecordRepository recordRepository;

    @Autowired
    private PlanItemRepository itemRepository;

    @Autowired
    private MeetingParticipantFieldRepository fieldRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void bindDefaultUserToDirectServiceCalls() {
        CurrentUserHolder.set(new CurrentUser(DEFAULT_USER, DEFAULT_USER, Set.of()));
    }

    @AfterEach
    void clearDirectServiceUser() {
        CurrentUserHolder.clear();
    }

    @Test
    void controllerRoutesDoNotExposeApiPrefixAndAllowLocalNetworkOrigins() throws Exception {
        mockMvc.perform(get("/meetings").header(USER_HEADER, DEFAULT_USER))
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
    void venueCatalogDoesNotInitializePresetVenuesOrDemoMeetings() {
        assertThat(venueService.list("preset-auditorium-hall", "", 1, 10).records())
                .isEmpty();
        assertThat(meetingRepository
                .findAllByCreatedByIdOrderByUpdatedAtDesc(DEFAULT_USER))
                .isEmpty();
    }

    @Test
    void meetingApisAllowAnonymousDemoSpaceWithoutRequestHeaders() throws Exception {
        CurrentUserHolder.clear();
        mockMvc.perform(get("/meetings"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/meetings").header(USER_HEADER, "   "))
                .andExpect(status().isOk());
    }

    @Test
    void successfulApiCallsWriteStartAndResultLogs(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/meetings").header(USER_HEADER, DEFAULT_USER))
                .andExpect(status().isOk());

        assertThat(output.getOut())
                .contains("[API][START]")
                .contains("[API][RESULT]")
                .contains("method=GET")
                .contains("path=/meetings")
                .contains("status=200");
    }

    @Test
    void handledApiExceptionsWriteExceptionLogs(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/meetings/{meetingId}/workspace", "missing-meeting")
                        .header(USER_HEADER, DEFAULT_USER))
                .andExpect(status().isNotFound());

        assertThat(output.getOut())
                .contains("[API][EXCEPTION]")
                .contains("requestId=")
                .contains("method=GET")
                .contains("path=/meetings/missing-meeting/workspace")
                .contains("status=404")
                .contains("exception=ApiException");
    }

    @Test
    void meetingAndAllChildResourcesAreIsolatedByOwner() throws Exception {
        UUID suffix = UUID.randomUUID();
        String userAMeetingName = "用户A会议-" + suffix;
        String userBMeetingName = "用户B会议-" + suffix;
        String userAMeetingId = createMeetingAs("user-a", userAMeetingName);
        String userBMeetingId = createMeetingAs("user-b", userBMeetingName);

        JsonNode userAList = responseJson(mockMvc.perform(
                        get("/meetings").header(USER_HEADER, "user-a"))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode userBList = responseJson(mockMvc.perform(
                        get("/meetings").header(USER_HEADER, "user-b"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(userAList.findValues("name").stream().map(JsonNode::asText).toList())
                .contains(userAMeetingName)
                .doesNotContain(userBMeetingName);
        assertThat(userBList.findValues("name").stream().map(JsonNode::asText).toList())
                .contains(userBMeetingName)
                .doesNotContain(userAMeetingName);

        mockMvc.perform(get("/meetings/{meetingId}/workspace", userBMeetingId)
                        .header(USER_HEADER, "user-a"))
                .andExpect(status().isNotFound());

        String participantId = createParticipantAs(
                "user-b",
                userBMeetingId,
                "b00000001",
                "用户B人员"
        );
        JsonNode workspace = responseJson(mockMvc.perform(
                        get("/meetings/{meetingId}/workspace", userBMeetingId)
                                .header(USER_HEADER, "user-b"))
                .andExpect(status().isOk())
                .andReturn());
        String planId = workspace.path("plan").path("id").asText();
        String seatId = StreamSupport.stream(
                        workspace.path("layout").path("elements").spliterator(),
                        false
                )
                .filter(element -> "SEAT".equals(element.path("kind").asText()))
                .findFirst()
                .orElseThrow()
                .path("id")
                .asText();

        mockMvc.perform(post("/meetings/{meetingId}/participants", userBMeetingId)
                        .header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeNo":"a00000001","name":"越权新增","attributes":{}}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(
                        "/meetings/{meetingId}/participants/{participantId}/attendance",
                        userBMeetingId,
                        participantId
                ).header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendanceStatus\":\"TEMPORARILY_ABSENT\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(
                        "/meetings/{meetingId}/participants/{participantId}/delete",
                        userBMeetingId,
                        participantId
                ).header(USER_HEADER, "user-a"))
                .andExpect(status().isNotFound());

        byte[] importContent = workbook("工号,姓名,批次\nb00000001,用户B人员,第一批");
        mockMvc.perform(multipart("/meetings/{meetingId}/imports/preview", userBMeetingId)
                        .file(new MockMultipartFile(
                                "file",
                                "participants.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                importContent
                        ))
                        .header(USER_HEADER, "user-a"))
                .andExpect(status().isNotFound());
        JsonNode preview = previewAs("user-b", userBMeetingId, importContent);
        String token = preview.path("token").asText();
        mockMvc.perform(post(
                        "/meetings/{meetingId}/imports/{token}/commit",
                        userAMeetingId,
                        token
                ).header(USER_HEADER, "user-a"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(
                        "/meetings/{meetingId}/imports/{token}/commit",
                        userBMeetingId,
                        token
                ).header(USER_HEADER, "user-b"))
                .andExpect(status().isOk());

        String assignmentJson = """
                {"participantId":"%s","targetElementId":"%s"}
                """.formatted(participantId, seatId);
        mockMvc.perform(post("/plans/{planId}/assignments", planId)
                        .header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/plans/{planId}/assignments", planId)
                        .header(USER_HEADER, "user-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson))
                .andExpect(status().isOk());
        mockMvc.perform(post("/plans/{planId}/assignments/save", planId)
                        .header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignments":[%s]}
                                """.formatted(assignmentJson)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(
                        "/plans/{planId}/participants/{participantId}/assignment/remove",
                        planId,
                        participantId
                ).header(USER_HEADER, "user-a"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(
                        "/plans/{planId}/participants/{participantId}/lock",
                        planId,
                        participantId
                ).param("locked", "true").header(USER_HEADER, "user-a"))
                .andExpect(status().isNotFound());

        String versionRequest = """
                {"versionName":"用户B发布版","changeNote":"","automatic":false}
                """;
        mockMvc.perform(post("/plans/{planId}/versions", planId)
                        .header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionRequest))
                .andExpect(status().isNotFound());
        JsonNode version = responseJson(mockMvc.perform(post("/plans/{planId}/versions", planId)
                        .header(USER_HEADER, "user-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionRequest))
                .andExpect(status().isOk())
                .andReturn());
        String versionId = version.path("id").asText();
        mockMvc.perform(get("/plans/{planId}/versions/{versionId}", planId, versionId)
                        .header(USER_HEADER, "user-a"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(
                        "/plans/{planId}/versions/{versionId}/restore",
                        planId,
                        versionId
                ).header(USER_HEADER, "user-a"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/meetings/{meetingId}/exports/excel", userBMeetingId)
                        .param("versionId", versionId)
                        .header(USER_HEADER, "user-a"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/meetings/{meetingId}/workspace", userAMeetingId)
                        .header(USER_HEADER, "user-a"))
                .andExpect(status().isOk());
    }

    @Test
    void seatingAssignmentsDoNotRevealForeignOrMissingParticipantAndElementIds() throws Exception {
        UUID suffix = UUID.randomUUID();
        String userAMeetingId = createMeetingAs("user-a", "座位侧信道A-" + suffix);
        String userBMeetingId = createMeetingAs("user-b", "座位侧信道B-" + suffix);
        String userAParticipantId = createParticipantAs(
                "user-a", userAMeetingId, "a90000001", "用户A人员"
        );
        String userBParticipantId = createParticipantAs(
                "user-b", userBMeetingId, "b90000001", "用户B人员"
        );
        JsonNode userAWorkspace = workspaceAs("user-a", userAMeetingId);
        JsonNode userBWorkspace = workspaceAs("user-b", userBMeetingId);
        String userAPlanId = userAWorkspace.path("plan").path("id").asText();
        String userAElementId = assignableSeatId(userAWorkspace);
        String userBElementId = assignableSeatId(userBWorkspace);
        String missingId = "missing-" + suffix;

        mockMvc.perform(post("/plans/{planId}/assignments", userAPlanId)
                        .header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(userBParticipantId, userAElementId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/plans/{planId}/assignments", userAPlanId)
                        .header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(userAParticipantId, userBElementId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/plans/{planId}/assignments", userAPlanId)
                        .header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(missingId, userAElementId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/plans/{planId}/assignments", userAPlanId)
                        .header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(userAParticipantId, missingId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/plans/{planId}/assignments/save", userAPlanId)
                        .header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentsJson(userBParticipantId, userAElementId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/plans/{planId}/assignments/save", userAPlanId)
                        .header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentsJson(userAParticipantId, userBElementId)))
                .andExpect(status().isNotFound());

        assertUnassigned(workspaceAs("user-a", userAMeetingId), userAParticipantId);
        assertUnassigned(workspaceAs("user-b", userBMeetingId), userBParticipantId);
    }

    @Test
    void temporarilyAbsentParticipantReleasesSeatAndDoesNotBlockPublishing() {
        VenueSummary venue = defaultVenue();
        MeetingSummary meeting = meetingService.create(new CreateMeetingRequest(
                "临时不出席测试-" + UUID.randomUUID(),
                venue.id()
        ));
        ParticipantResult participant = participantService.create(
                meeting.id(),
                new CreateParticipantRequest(
                        "12345678", "测试人员", Map.of(), null
                )
        );
        WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());
        ElementView seat = workspace.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()))
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

        WorkspaceResponse absentWorkspace = workspaceService.getWorkspace(meeting.id());
        ParticipantView absentParticipant = absentWorkspace.participants().getFirst();
        assertThat(absentParticipant.attendanceStatus()).isEqualTo("TEMPORARILY_ABSENT");
        assertThat(absentParticipant.assignedElementId()).isNull();
        assertThat(planVersionService.create(
                absentWorkspace.plan().id(),
                new CreateVersionRequest("临时不出席版本", "", false)
        ).unassignedCount()).isZero();
    }

    @Test
    void creatingParticipantFromEmptySeatAssignsTheNewParticipantAtomically() throws Exception {
        VenueSummary venue = defaultVenue();
        MeetingSummary meeting = meetingService.create(new CreateMeetingRequest(
                "空座新增人员测试-" + UUID.randomUUID(),
                venue.id()
        ));
        WorkspaceResponse before = workspaceService.getWorkspace(meeting.id());
        ElementView seat = before.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/meetings/{meetingId}/participants", meeting.id())
                        .header(USER_HEADER, DEFAULT_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeNo": "87654321",
                                  "name": "直接落座人员",
                                  "attributes": {
                                    "活动角色": "主持人",
                                    "批次": "第一批"
                                  },
                                  "targetElementId": "%s"
                                }
                                """.formatted(seat.id())))
                .andExpect(status().isOk());

        WorkspaceResponse after = workspaceService.getWorkspace(meeting.id());
        ParticipantView participant = after.participants().stream()
                .filter(value -> value.employeeNo().equals("87654321"))
                .findFirst()
                .orElseThrow();
        assertThat(participant.assignedElementId()).isEqualTo(seat.id());
        assertThat(participant.primaryAttributes())
                .containsEntry("活动角色", "主持人")
                .containsEntry("批次", "第一批");
        assertThat(participant.records()).hasSize(1);
        assertThat(participant.records().getFirst().recordOrder()).isEqualTo(1);
        assertThat(fieldRepository.findAllByMeetingIdOrderBySortOrderAsc(meeting.id()))
                .extracting(value -> value.getFieldName())
                .containsExactly("活动角色", "批次");
        assertThat(after.items().stream()
                .filter(value -> participant.id().equals(value.participantId()))
                .findFirst()
                .orElseThrow()
                .targetElementIds()).containsExactly(seat.id());
    }

    @Test
    void participantCanBeUpdatedWithoutChangingEmployeeNoAndRegistersNewFields() throws Exception {
        String meetingId = createImportMeeting();
        ParticipantResult participant = participantService.create(
                meetingId,
                new CreateParticipantRequest("a90000001", "旧姓名", Map.of("部门", "研发"), null)
        );

        MvcResult result = mockMvc.perform(post(
                        "/meetings/{meetingId}/participants/{participantId}/update",
                        meetingId,
                        participant.id()
                ).header(USER_HEADER, DEFAULT_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "新姓名",
                                  "records": [
                                    {"attributes": {"部门": "市场", "获奖批次": "第一批"}}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(responseJson(result).path("name").asText()).isEqualTo("新姓名");
        WorkspaceResponse workspace = workspaceService.getWorkspace(meetingId);
        ParticipantView updated = workspace.participants().stream()
                .filter(value -> value.id().equals(participant.id()))
                .findFirst()
                .orElseThrow();
        assertThat(updated.employeeNo()).isEqualTo("a90000001");
        assertThat(updated.primaryAttributes())
                .containsEntry("部门", "市场")
                .containsEntry("获奖批次", "第一批");
    }

    @Test
    void participantUpdateRejectsBlankNameAndBlankNewFieldValues() throws Exception {
        String meetingId = createImportMeeting();
        ParticipantResult participant = participantService.create(
                meetingId,
                new CreateParticipantRequest("a90000002", "待更新", Map.of(), null)
        );

        mockMvc.perform(post(
                        "/meetings/{meetingId}/participants/{participantId}/update",
                        meetingId,
                        participant.id()
                ).header(USER_HEADER, DEFAULT_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","records":[{"attributes":{"新字段":"值"}}]}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(
                        "/meetings/{meetingId}/participants/{participantId}/update",
                        meetingId,
                        participant.id()
                ).header(USER_HEADER, DEFAULT_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"待更新","records":[{"attributes":{"新字段":" "}}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservedAreasOccupySeatsAndBlockPersonAssignments() throws Exception {
        VenueSummary venue = defaultVenue();
        MeetingSummary meeting = meetingService.create(new CreateMeetingRequest(
                "区域标记-" + UUID.randomUUID(),
                venue.id()
        ));
        WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());
        String planId = workspace.plan().id();
        String seatId = workspace.layout().elements().stream()
                .filter(value -> value.kind().equals("SEAT"))
                .findFirst()
                .orElseThrow()
                .id();

        mockMvc.perform(post("/plans/{planId}/reserved-areas/save", planId)
                        .header(USER_HEADER, DEFAULT_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservedAreas":[{"label":"嘉宾","backgroundColor":"#FEF3C7","textColor":"#172033","bold":true,"targetElementIds":["%s"]}]}
                                """.formatted(seatId)))
                .andExpect(status().isOk());

        ParticipantResult participant = participantService.create(
                meeting.id(),
                new CreateParticipantRequest("a91000001", "王嘉宾", Map.of(), null)
        );
        assertThatThrownBy(() -> seatingService.assign(
                planId,
                new AssignmentRequest(participant.id(), seatId)
        )).hasMessageContaining("目标座位已被设备、预留或禁用状态占用");
    }

    @Test
    void draftLayoutUpdateRemovesAssignmentsForDeletedSeatsButNotVenueTemplate() throws Exception {
        VenueSummary venue = defaultVenue();
        MeetingSummary meeting = meetingService.create(new CreateMeetingRequest(
                "布局编辑-" + UUID.randomUUID(),
                venue.id()
        ));
        WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());
        String planId = workspace.plan().id();
        ElementView removedSeat = workspace.layout().elements().stream()
                .filter(value -> value.kind().equals("SEAT"))
                .findFirst()
                .orElseThrow();
        ParticipantResult participant = participantService.create(
                meeting.id(),
                new CreateParticipantRequest("a91000002", "待退回", Map.of(), null)
        );
        seatingService.assign(planId, new AssignmentRequest(participant.id(), removedSeat.id()));

        String elementsJson = workspace.layout().elements().stream()
                .filter(value -> !value.id().equals(removedSeat.id()))
                .map(value -> """
                        {"id":"%s","kind":"%s","name":"%s","row":%d,"column":%d,"rowSpan":%d,"columnSpan":%d,"fillColor":"%s","borderColor":"%s"}
                        """.formatted(
                        value.id(), value.kind(), value.name(), value.row(), value.column(),
                        value.rowSpan(), value.columnSpan(), value.fillColor(), value.borderColor()
                ))
                .collect(Collectors.joining(","));

        mockMvc.perform(post("/meetings/{meetingId}/layout/update", meeting.id())
                        .header(USER_HEADER, DEFAULT_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gridRows":5,"gridColumns":5,"elements":[%s]}
                                """.formatted(elementsJson)))
                .andExpect(status().isOk());

        WorkspaceResponse updated = workspaceService.getWorkspace(meeting.id());
        assertThat(updated.participants().getFirst().assignedElementId()).isNull();
        assertThat(venueService.getLayout(venue.id()).seatCount()).isEqualTo(25);
    }

    @Test
    void workspaceAggregatesDynamicParticipantRecordsByRecordAndFieldOrder() throws Exception {
        String meetingId = createImportMeeting();
        previewAndCommit(
                meetingId,
                "工号,姓名,批次,奖项名称",
                "a12345678,张三,第二批,创新奖"
        );
        previewAndCommit(
                meetingId,
                "工号,姓名,批次,奖项名称",
                "a12345678,张三,第三批,卓越奖"
        );

        WorkspaceResponse workspace = workspaceService.getWorkspace(meetingId);
        ParticipantView person = workspace.participants().stream()
                .filter(value -> value.employeeNo().equals("a12345678"))
                .findFirst()
                .orElseThrow();

        assertThat(person.primaryAttributes())
                .containsEntry("批次", "第二批")
                .containsEntry("奖项名称", "创新奖");
        assertThat(person.primaryAttributes().keySet())
                .containsExactly("批次", "奖项名称");
        assertThat(person.attributeValues().get("批次"))
                .containsExactly("第二批", "第三批");
        assertThat(person.attributeValues().get("奖项名称"))
                .containsExactly("创新奖", "卓越奖");
        assertThat(person.records()).hasSize(2);
        assertThat(person.records())
                .extracting(value -> value.recordOrder())
                .containsExactly(1, 2);
        assertThat(workspace.fieldDefinitions())
                .extracting(value -> value.label())
                .containsExactly("姓名", "工号", "批次", "奖项名称");
        assertThat(workspace.fieldDefinitions().stream()
                .filter(value -> value.label().equals("姓名") || value.label().equals("工号")))
                .allSatisfy(value -> {
                    assertThat(value.searchable()).isTrue();
                    assertThat(value.filterable()).isFalse();
                });
        assertThat(workspace.fieldDefinitions().stream()
                .filter(value -> value.label().equals("批次") || value.label().equals("奖项名称")))
                .allSatisfy(value -> assertThat(value.filterable()).isTrue());
    }

    @Test
    void workspaceKeepsBlankRecordAttributesButSkipsThemWhenChoosingPrimaryValues() throws Exception {
        String meetingId = createImportMeeting();
        previewAndCommit(
                meetingId,
                "工号,姓名,批次,奖项名称",
                "a12345678,张三,第二批,创新奖"
        );
        ParticipantEntity participant = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCase(meetingId, "a12345678")
                .orElseThrow();
        ParticipantRecordEntity blankRecord = new ParticipantRecordEntity();
        blankRecord.setParticipantId(participant.getId());
        blankRecord.setRecordOrder(0);
        blankRecord.setAttributesJson(objectMapper.writeValueAsString(
                Map.of("批次", "   ", "奖项名称", "")
        ));
        recordRepository.saveAndFlush(blankRecord);

        ParticipantView person = workspaceService.getWorkspace(meetingId).participants().stream()
                .filter(value -> value.id().equals(participant.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(person.records()).hasSize(2);
        assertThat(person.records().getFirst().recordOrder()).isZero();
        assertThat(person.records().getFirst().attributes())
                .containsEntry("批次", "   ")
                .containsEntry("奖项名称", "");
        assertThat(person.primaryAttributes())
                .containsEntry("批次", "第二批")
                .containsEntry("奖项名称", "创新奖");
        assertThat(person.attributeValues().get("批次")).containsExactly("第二批");
        assertThat(person.attributeValues().get("奖项名称")).containsExactly("创新奖");
    }

    @Test
    void legacyParticipantLockDoesNotBlockAssignmentWithoutLockedPlanItem() {
        VenueSummary venue = defaultVenue();
        MeetingSummary meeting = meetingService.create(new CreateMeetingRequest(
                "旧人员锁兼容测试-" + UUID.randomUUID(),
                venue.id()
        ));
        ParticipantResult participant = participantService.create(
                meeting.id(),
                new CreateParticipantRequest(
                        "12345678", "测试人员", Map.of(), null
                )
        );
        ParticipantEntity entity = participantRepository.findById(participant.id()).orElseThrow();
        entity.setLocked(true);
        participantRepository.saveAndFlush(entity);
        WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());
        ElementView seat = workspace.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()))
                .findFirst()
                .orElseThrow();

        seatingService.assign(
                workspace.plan().id(),
                new AssignmentRequest(participant.id(), seat.id())
        );

        assertThat(workspaceService.getWorkspace(meeting.id()).participants().stream()
                .filter(value -> value.id().equals(participant.id()))
                .findFirst()
                .orElseThrow()
                .assignedElementId()).isEqualTo(seat.id());
    }

    @Test
    void workspaceContainsVenueSnapshotAndExplicitlyCreatedParticipants() {
        String meetingId = createSeatingScenario(4, 2);
        WorkspaceResponse workspace = workspaceService.getWorkspace(meetingId);

        assertThat(workspace.layout().gridRows()).isEqualTo(5);
        assertThat(workspace.layout().gridColumns()).isEqualTo(5);
        assertThat(workspace.layout().elements()).hasSize(25);
        assertThat(workspace.participants()).hasSize(4);
        assertThat(workspace.participants().stream()
                .filter(participant -> participant.assignedElementId() != null)
                .count()).isEqualTo(2);
    }

    @Test
    void unassignedParticipantCanBeMovedIntoAnEmptySeat() {
        String meetingId = createSeatingScenario(3, 2);
        WorkspaceResponse before = workspaceService.getWorkspace(meetingId);
        ParticipantView participant = before.participants().stream()
                .filter(value -> value.assignedElementId() == null)
                .findFirst().orElseThrow();
        Set<String> occupied = before.items().stream()
                .flatMap(item -> item.targetElementIds().stream())
                .collect(Collectors.toSet());
        ElementView seat = before.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()) && !occupied.contains(value.id()))
                .findFirst().orElseThrow();

        seatingService.assign(before.plan().id(), new AssignmentRequest(participant.id(), seat.id()));

        WorkspaceResponse after = workspaceService.getWorkspace(meetingId);
        assertThat(after.participants().stream()
                .filter(value -> value.id().equals(participant.id()))
                .findFirst().orElseThrow().assignedElementId()).isEqualTo(seat.id());
    }

    @Test
    void twoAssignedParticipantsCanSwapSeatsWithoutViolatingUniqueConstraint() {
        String meetingId = createSeatingScenario(2, 2);
        WorkspaceResponse before = workspaceService.getWorkspace(meetingId);
        List<ParticipantView> assigned = before.participants().stream()
                .filter(value -> value.assignedElementId() != null && !value.locked())
                .limit(2)
                .toList();
        ParticipantView first = assigned.get(0);
        ParticipantView second = assigned.get(1);

        seatingService.assign(
                before.plan().id(),
                new AssignmentRequest(first.id(), second.assignedElementId())
        );

        WorkspaceResponse after = workspaceService.getWorkspace(meetingId);
        Map<String,ParticipantView> participantById = after.participants().stream()
                .collect(Collectors.toMap(value -> value.id(), value -> value));
        assertThat(participantById.get(first.id()).assignedElementId())
                .isEqualTo(second.assignedElementId());
        assertThat(participantById.get(second.id()).assignedElementId())
                .isEqualTo(first.assignedElementId());
    }

    @Test
    void completeAssignmentSetCanBeSavedInOneBatch() {
        String meetingId = createSeatingScenario(2, 2);
        WorkspaceResponse before = workspaceService.getWorkspace(meetingId);
        List<ParticipantView> assigned = before.participants().stream()
                .filter(value -> value.assignedElementId() != null && !value.locked())
                .limit(2)
                .toList();
        ParticipantView first = assigned.get(0);
        ParticipantView second = assigned.get(1);
        List<AssignmentInput> assignments = before.participants().stream()
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

        WorkspaceResponse after = workspaceService.getWorkspace(meetingId);
        Map<String,ParticipantView> participantById = after.participants().stream()
                .collect(Collectors.toMap(value -> value.id(), value -> value));
        assertThat(participantById.get(first.id()).assignedElementId())
                .isEqualTo(second.assignedElementId());
        assertThat(participantById.get(second.id()).assignedElementId())
                .isEqualTo(first.assignedElementId());
    }

    @Test
    void savedVersionCanRestorePreviousSeatingState() {
        String meetingId = createSeatingScenario(3, 2);
        WorkspaceResponse before = workspaceService.getWorkspace(meetingId);

        assertThatThrownBy(() -> planVersionService.create(before.plan().id(),
                new CreateVersionRequest("未完成版本", "仍有待排人员", false)))
                .hasMessageContaining("全部完成排座后才能发布");

        Set<String> occupied = before.items().stream()
                .flatMap(item -> item.targetElementIds().stream())
                .collect(Collectors.toSet());
        Iterator<ElementView> emptySeats = before.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()) && !occupied.contains(value.id()))
                .iterator();
        before.participants().stream()
                .filter(value -> value.assignedElementId() == null)
                .forEach(participant -> seatingService.assign(
                        before.plan().id(),
                        new AssignmentRequest(participant.id(), emptySeats.next().id())
                ));

        WorkspaceResponse readyToPublish = workspaceService.getWorkspace(meetingId);
        VersionResult saved = planVersionService.create(before.plan().id(),
                new CreateVersionRequest("恢复测试版本", "全部人员已完成排座", false));
        WorkspaceResponse immutableSnapshot = planVersionService.getSnapshot(before.plan().id(), saved.id());
        assertThat(immutableSnapshot.participants().stream()
                .filter(value -> value.assignedElementId() != null)
                .count()).isEqualTo(3);

        ParticipantView participant = readyToPublish.participants().getFirst();
        seatingService.unassign(before.plan().id(), participant.id());
        assertThat(workspaceService.getWorkspace(meetingId).participants().stream()
                .filter(value -> value.assignedElementId() != null)
                .count()).isEqualTo(2);
        assertThat(planVersionService.getSnapshot(before.plan().id(), saved.id()).participants().stream()
                .filter(value -> value.assignedElementId() != null)
                .count()).isEqualTo(3);
        assertThat(exportService.exportExcel(meetingId, saved.id()).length).isGreaterThan(5_000);

        planVersionService.restore(before.plan().id(), saved.id());

        WorkspaceResponse restored = workspaceService.getWorkspace(meetingId);
        assertThat(restored.plan().currentVersionNo()).isEqualTo(saved.versionNo());
        assertThat(restored.participants().stream()
                .filter(value -> value.assignedElementId() != null)
                .count()).isEqualTo(3);
    }

    @Test
    void publishedVersionFreezesAndRestoresDynamicParticipantStateByEmployeeNumber() throws Exception {
        String meetingId = createImportMeeting();
        previewAndCommit(
                meetingId,
                "工号,姓名,批次,奖项名称",
                "a12345678,张三,第二批,优秀项目奖"
        );
        previewAndCommit(
                meetingId,
                "工号,姓名,批次,奖项名称",
                "a12345678,张三,第三批,创新奖"
        );
        ParticipantEntity original = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCase(meetingId, "a12345678")
                .orElseThrow();
        WorkspaceResponse before = workspaceService.getWorkspace(meetingId);
        ElementView seat = before.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()))
                .findFirst()
                .orElseThrow();
        seatingService.assign(
                before.plan().id(),
                new AssignmentRequest(original.getId(), seat.id())
        );
        VersionResult version = planVersionService.create(
                before.plan().id(),
                new CreateVersionRequest("动态记录恢复版", "", false)
        );

        WorkspaceResponse frozen = planVersionService.getSnapshot(before.plan().id(), version.id());
        ParticipantView frozenPerson = frozen.participants().stream()
                .filter(value -> value.employeeNo().equals("a12345678"))
                .findFirst()
                .orElseThrow();
        assertThat(frozen.fieldDefinitions())
                .extracting(value -> value.label())
                .containsExactly("姓名", "工号", "批次", "奖项名称");
        assertThat(frozenPerson.records())
                .extracting(value -> value.attributes().get("批次"))
                .containsExactly("第二批", "第三批");

        participantService.delete(meetingId, original.getId());
        ParticipantEntity replacement = new ParticipantEntity();
        replacement.setMeetingId(meetingId);
        replacement.setEmployeeNo("A12345678");
        replacement.setName("草稿姓名");
        replacement.setAttendanceStatus(AttendanceStatus.TEMPORARILY_ABSENT);
        participantRepository.saveAndFlush(replacement);
        saveRecord(replacement.getId(), 7, Map.of("批次", "草稿批次"));
        saveRecord(replacement.getId(), 8, Map.of("批次", "残留批次"));

        List<MeetingParticipantFieldEntity> draftFields = fieldRepository
                .findAllByMeetingIdOrderBySortOrderAsc(meetingId);
        draftFields.get(0).setSortOrder(20);
        draftFields.get(1).setSortOrder(10);
        fieldRepository.saveAll(draftFields);
        MeetingParticipantFieldEntity staleField = new MeetingParticipantFieldEntity();
        staleField.setMeetingId(meetingId);
        staleField.setFieldName("草稿新增字段");
        staleField.setSortOrder(1);
        fieldRepository.saveAndFlush(staleField);

        ParticipantResult laterParticipant = participantService.create(
                meetingId,
                new CreateParticipantRequest(
                        "87654321",
                        "后来新增人员",
                        Map.of("后来字段", "保留值"),
                        null
                )
        );

        WorkspaceResponse stillFrozen = planVersionService.getSnapshot(before.plan().id(), version.id());
        ParticipantView stillFrozenPerson = stillFrozen.participants().stream()
                .filter(value -> value.employeeNo().equals("a12345678"))
                .findFirst()
                .orElseThrow();
        assertThat(stillFrozenPerson.id()).isEqualTo(original.getId());
        assertThat(stillFrozenPerson.name()).isEqualTo("张三");
        assertThat(stillFrozenPerson.records()).hasSize(2);

        planVersionService.restore(before.plan().id(), version.id());

        WorkspaceResponse restored = workspaceService.getWorkspace(meetingId);
        ParticipantView restoredPerson = restored.participants().stream()
                .filter(value -> value.employeeNo().equalsIgnoreCase("a12345678"))
                .findFirst()
                .orElseThrow();
        assertThat(restoredPerson.id()).isEqualTo(replacement.getId());
        assertThat(restoredPerson.name()).isEqualTo("张三");
        assertThat(restoredPerson.attendanceStatus()).isEqualTo("PRESENT");
        assertThat(restoredPerson.assignedElementId()).isEqualTo(seat.id());
        assertThat(restoredPerson.records())
                .extracting(value -> value.recordOrder())
                .containsExactly(1, 2);
        assertThat(restoredPerson.records())
                .extracting(value -> value.attributes().get("批次"))
                .containsExactly("第二批", "第三批");
        assertThat(restored.fieldDefinitions())
                .extracting(value -> value.label())
                .containsExactly("姓名", "工号", "批次", "奖项名称", "后来字段");
        assertThat(restored.participants())
                .extracting(value -> value.id())
                .contains(laterParticipant.id());
        assertThat(restored.participants().stream()
                .filter(value -> value.id().equals(laterParticipant.id()))
                .findFirst()
                .orElseThrow()
                .primaryAttributes())
                .containsEntry("后来字段", "保留值");
        assertThat(restored.items().stream()
                .filter(value -> replacement.getId().equals(value.participantId()))
                .flatMap(value -> value.targetElementIds().stream()))
                .containsExactly(seat.id());
    }

    @Test
    void deletingParticipantPhysicallyRemovesIdentityRecordsAndAssignment()
            throws Exception {
        String meetingId = createImportMeeting();
        previewAndCommit(
                meetingId,
                "工号,姓名,批次,奖项名称",
                "a12345678,张三,第二批,优秀项目奖"
        );
        ParticipantEntity original = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCase(meetingId, "a12345678")
                .orElseThrow();
        WorkspaceResponse before = workspaceService.getWorkspace(meetingId);
        ElementView seat = before.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()))
                .findFirst()
                .orElseThrow();
        seatingService.assign(
                before.plan().id(),
                new AssignmentRequest(original.getId(), seat.id())
        );
        participantService.delete(meetingId, original.getId());

        assertThat(participantRepository.findById(original.getId())).isEmpty();
        assertThat(recordRepository.findAllByParticipantIdOrderByRecordOrderAsc(original.getId()))
                .isEmpty();
        assertThat(itemRepository.findByPlanIdAndParticipantIdAndItemType(
                before.plan().id(), original.getId(), PlanItemType.PERSON)).isEmpty();
    }

    @Test
    void restoringPublishedVersionDoesNotRecreatePhysicallyDeletedParticipant()
            throws Exception {
        String meetingId = createImportMeeting();
        previewAndCommit(
                meetingId,
                "工号,姓名,批次",
                "a12345678,张三,第二批"
        );
        ParticipantEntity participant = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCase(meetingId, "a12345678")
                .orElseThrow();
        WorkspaceResponse before = workspaceService.getWorkspace(meetingId);
        ElementView seat = before.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()))
                .findFirst()
                .orElseThrow();
        seatingService.assign(
                before.plan().id(),
                new AssignmentRequest(participant.getId(), seat.id())
        );
        VersionResult version = planVersionService.create(
                before.plan().id(),
                new CreateVersionRequest("已删除人员恢复保护版", "", false)
        );

        participantService.delete(meetingId, participant.getId());

        planVersionService.restore(before.plan().id(), version.id());

        WorkspaceResponse restored = workspaceService.getWorkspace(meetingId);
        assertThat(restored.participants()).isEmpty();
        assertThat(recordRepository.findAllByParticipantIdOrderByRecordOrderAsc(participant.getId()))
                .isEmpty();
        assertThat(itemRepository.findByPlanIdAndParticipantIdAndItemType(
                before.plan().id(), participant.getId(), PlanItemType.PERSON)).isEmpty();
    }

    @Test
    void legacyGenericAttributesAreAdaptedToDynamicFieldsForExportAndRestore()
            throws Exception {
        String meetingId = createImportMeeting();
        ParticipantResult participant = participantService.create(
                meetingId,
                new CreateParticipantRequest(
                        "a12345678",
                        "张三",
                        Map.of("部门", "研发部", "备注", "重点"),
                        null
                )
        );
        WorkspaceResponse before = workspaceService.getWorkspace(meetingId);
        ElementView seat = before.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()))
                .findFirst()
                .orElseThrow();
        seatingService.assign(
                before.plan().id(),
                new AssignmentRequest(participant.id(), seat.id())
        );
        VersionResult version = planVersionService.create(
                before.plan().id(),
                new CreateVersionRequest("旧快照兼容版", "", false)
        );
        PlanVersionEntity versionEntity = planVersionRepository.findById(version.id()).orElseThrow();
        ObjectNode legacySnapshot = (ObjectNode) objectMapper
                .readTree(versionEntity.getSnapshotJson());
        legacySnapshot.remove("fieldDefinitions");
        legacySnapshot.withArray("participants").forEach(value -> {
            ObjectNode participantNode = (ObjectNode) value;
            participantNode.remove("primaryAttributes");
            participantNode.remove("attributeValues");
            participantNode.remove("records");
            participantNode.putObject("attributes")
                    .put("部门", "研发部")
                    .put("备注", "重点");
        });
        versionEntity.setSnapshotJson(objectMapper.writeValueAsString(legacySnapshot));
        planVersionRepository.saveAndFlush(versionEntity);

        WorkspaceResponse adaptedSnapshot = planVersionService.getSnapshot(before.plan().id(), version.id());
        assertThat(adaptedSnapshot.fieldDefinitions())
                .extracting(value -> value.label())
                .containsExactly("姓名", "工号", "部门", "备注");
        assertThat(adaptedSnapshot.participants().getFirst().primaryAttributes())
                .containsEntry("部门", "研发部")
                .containsEntry("备注", "重点");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                exportService.exportExcel(meetingId, version.id())
        ))) {
            assertThat(cellValues(workbook.getSheet("人员名单").getRow(0)))
                    .containsExactly("工号", "姓名", "部门", "备注");
            assertThat(cellValues(workbook.getSheet("人员名单").getRow(1)))
                    .containsExactly("a12345678", "张三", "研发部", "重点");
        }
        planVersionService.restore(before.plan().id(), version.id());

        assertThat(fieldRepository
                .findAllByMeetingIdOrderBySortOrderAsc(meetingId))
                .extracting(value -> value.getFieldName())
                .containsExactly("部门", "备注");
        assertThat(recordRepository
                .findAllByParticipantIdOrderByRecordOrderAsc(participant.id()))
                .extracting(value -> readAttributes(value.getAttributesJson()))
                .singleElement()
                .satisfies(attributes -> assertThat(attributes)
                        .containsEntry("部门", "研发部")
                        .containsEntry("备注", "重点"));
    }

    @Test
    void publishedVersionNamesAreUniqueWithinASeatingPlan() {
        VenueSummary venue = defaultVenue();
        MeetingSummary meeting = meetingService.create(new CreateMeetingRequest(
                "版本名称判重测试-" + UUID.randomUUID(),
                venue.id()
        ));
        WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());

        VersionResult published = planVersionService.create(
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
    void draftWorkspaceCanBeExportedWithSelectedParticipantColumns() throws Exception {
        VenueSummary venue = defaultVenue();
        MeetingSummary meeting = meetingService.create(new CreateMeetingRequest(
                "草稿导出测试-" + UUID.randomUUID(),
                venue.id()
        ));
        previewAndCommit(
                meeting.id(),
                "工号,姓名,部门,批次",
                "12345678,草稿导出人员,研发部,第一批"
        );
        WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());
        ElementView seat = workspace.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()))
                .findFirst()
                .orElseThrow();
        ParticipantView participant = workspace.participants().getFirst();
        seatingService.assign(
                workspace.plan().id(),
                new AssignmentRequest(participant.id(), seat.id())
        );

        byte[] exported = exportService.exportExcel(
                meeting.id(),
                null,
                new ExportService.ExportOptions(List.of("部门"), true, true)
        );
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            assertThat(cellValues(workbook.getSheet("人员名单").getRow(0)))
                    .containsExactly("工号", "姓名", "部门", "出席情况", "座位编号");
            assertThat(cellValues(workbook.getSheet("人员名单").getRow(1)))
                    .containsExactly("12345678", "草稿导出人员", "研发部", "出席", "1排1");
        }

        mockMvc.perform(get("/meetings/{meetingId}/exports/excel", meeting.id())
                        .header(USER_HEADER, DEFAULT_USER))
                .andExpect(status().isOk());
    }

    @Test
    void exportedSeatDetailsShowReservedRegionName() throws Exception {
        VenueSummary venue = defaultVenue();
        MeetingSummary meeting = meetingService.create(new CreateMeetingRequest(
                "区域明细导出-" + UUID.randomUUID(),
                venue.id()
        ));
        WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());
        ElementView seat = workspace.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()))
                .findFirst()
                .orElseThrow();
        mockMvc.perform(post("/plans/{planId}/reserved-areas/save", workspace.plan().id())
                        .header(USER_HEADER, DEFAULT_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservedAreas":[{"label":"嘉宾","backgroundColor":"#FEF3C7","textColor":"#172033","bold":true,"targetElementIds":["%s"]}]}
                                """.formatted(seat.id())))
                .andExpect(status().isOk());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                exportService.exportExcel(meeting.id(), null)
        ))) {
            XSSFSheet sheet = workbook.getSheet("座位明细");
            assertThat(cellValues(sheet.getRow(0)))
                    .contains("座位编号", "元素类型", "区域名称", "人员工号", "姓名");
            assertThat(cellValues(sheet.getRow(1)))
                    .contains("1排1", "区域", "嘉宾");
        }
    }

    @Test
    void genericParticipantTemplateAndExportsAreGenerated() throws Exception {
        byte[] template = mockMvc.perform(get("/imports/template"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            assertThat(workbook.getSheet("参会人员")).isNotNull();
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
        }

        VenueSummary venue = defaultVenue();
        MeetingSummary meeting = meetingService.create(new CreateMeetingRequest(
                "发布版本导出测试-" + UUID.randomUUID(),
                venue.id()
        ));
        WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());
        VersionResult version = planVersionService.create(
                workspace.plan().id(),
                new CreateVersionRequest("导出确认版", "", false)
        );
        assertThat(exportService.exportExcel(meeting.id(), version.id()).length).isGreaterThan(5_000);
    }

    @Test
    void exportedParticipantSheetKeepsDynamicRecordsAndCanBeReimported() throws Exception {
        String meetingId = createImportMeeting();
        previewAndCommit(
                meetingId,
                "工号,姓名,批次,奖项名称",
                "a12345678,张三,第二批,优秀项目奖"
        );
        previewAndCommit(
                meetingId,
                "工号,姓名,批次,奖项名称",
                "a12345678,张三,第三批,创新奖"
        );
        participantService.create(
                meetingId,
                new CreateParticipantRequest("87654321", "无动态记录人员", Map.of(), null)
        );
        WorkspaceResponse workspace = workspaceService.getWorkspace(meetingId);
        Iterator<ElementView> seats = workspace.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()))
                .iterator();
        workspace.participants().forEach(participant -> seatingService.assign(
                workspace.plan().id(),
                new AssignmentRequest(participant.id(), seats.next().id())
        ));
        VersionResult version = planVersionService.create(
                workspace.plan().id(),
                new CreateVersionRequest("动态记录导出版", "", false)
        );

        byte[] exported = exportService.exportExcel(meetingId, version.id());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("人员名单");
            assertThat(workbook.getSheet("获奖名单")).isNull();
            XSSFSheet sheet = workbook.getSheet("人员名单");
            assertThat(cellValues(sheet.getRow(0)))
                    .containsExactly("工号", "姓名", "批次", "奖项名称");
            ArrayList<List<String>> rows = new ArrayList<List<String>>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                List<String> values = cellValues(sheet.getRow(rowIndex));
                if (values.getFirst().equalsIgnoreCase("a12345678")) {
                    rows.add(values);
                }
            }
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).containsExactly(
                    "a12345678", "张三", "第二批", "优秀项目奖"
            );
            assertThat(rows.get(1)).containsExactly(
                    "a12345678", "张三", "第三批", "创新奖"
            );
            assertThat(IntStream.rangeClosed(1, sheet.getLastRowNum())
                    .mapToObj(sheet::getRow)
                    .map(this::cellValues)
                    .filter(values -> values.getFirst().equals("87654321"))
                    .findFirst())
                    .contains(List.of("87654321", "无动态记录人员", "", ""));
        }

        String importedMeetingId = createImportMeeting();
        JsonNode importedPreview = preview(importedMeetingId, exported);
        assertThat(importedPreview.path("errors").isEmpty()).isTrue();
        commit(importedMeetingId, importedPreview.path("token").asText());
        ParticipantEntity imported = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCase(
                        importedMeetingId,
                        "a12345678"
                )
                .orElseThrow();
        assertThat(fieldRepository
                .findAllByMeetingIdOrderBySortOrderAsc(importedMeetingId))
                .extracting(value -> value.getFieldName())
                .containsExactly("批次", "奖项名称");
        assertThat(recordRepository
                .findAllByParticipantIdOrderByRecordOrderAsc(imported.getId()))
                .extracting(value -> readAttributes(value.getAttributesJson()))
                .containsExactly(
                        Map.of("批次", "第二批", "奖项名称", "优秀项目奖"),
                        Map.of("批次", "第三批", "奖项名称", "创新奖")
                );
    }

    @Test
    void exportedLayoutUsesDynamicSeatLabelAndParticipantNameOnly() throws Exception {
        String meetingId = createImportMeeting();
        previewAndCommit(
                meetingId,
                "工号,姓名,空字段,活动角色,批次",
                "12345678,导出人员,,主持人,第一批"
        );
        WorkspaceResponse workspace = workspaceService.getWorkspace(meetingId);
        ElementView seat = workspace.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()))
                .findFirst()
                .orElseThrow();
        ParticipantView participant = workspace.participants().getFirst();
        seatingService.assign(
                workspace.plan().id(),
                new AssignmentRequest(participant.id(), seat.id())
        );
        VersionResult version = planVersionService.create(
                workspace.plan().id(),
                new CreateVersionRequest("排座图文本边界版", "", false)
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                exportService.exportExcel(meetingId, version.id())
        ))) {
            String cellText = workbook.getSheet("排座图")
                    .getRow(seat.row() - 1)
                    .getCell(seat.column() - 1)
                    .getStringCellValue();
            assertThat(cellText).isEqualTo("1排1\n导出人员");
            assertThat(cellText).doesNotContain("主持人");
            assertThat(cellText).doesNotContain("第一批");
        }
    }

    @Test
    void compatibleParticipantImportsMergeIntoOneRecordAndKeepFieldOrder() throws Exception {
        String meetingId = createImportMeeting();

        JsonNode first = previewAndCommit(
                meetingId,
                "工号,姓名,字段1",
                "a12345678,张三,值1"
        );
        JsonNode second = previewAndCommit(
                meetingId,
                "工号,姓名,字段1,字段2",
                "a12345678,张三,值1,值2"
        );

        ParticipantEntity participant = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCase(meetingId, "a12345678")
                .orElseThrow();
        List<ParticipantRecordEntity> records = recordRepository
                .findAllByParticipantIdOrderByRecordOrderAsc(participant.getId());
        List<MeetingParticipantFieldEntity> fields = fieldRepository.findAllByMeetingIdOrderBySortOrderAsc(meetingId);

        assertThat(first.path("newParticipants").asInt()).isEqualTo(1);
        assertThat(first.path("appendedRecords").asInt()).isEqualTo(1);
        assertThat(second.path("newParticipants").asInt()).isZero();
        assertThat(second.path("mergedRecords").asInt()).isEqualTo(1);
        assertThat(records).hasSize(1);
        assertThat(readAttributes(records.getFirst().getAttributesJson()))
                .containsEntry("字段1", "值1")
                .containsEntry("字段2", "值2");
        assertThat(fields)
                .extracting(value -> value.getFieldName())
                .containsExactly("字段1", "字段2");
        assertThat(fields)
                .extracting(value -> value.getSortOrder())
                .containsExactly(1, 2);
    }

    @Test
    void conflictingParticipantImportsAppendRecordsAndIdenticalImportIsSkipped() throws Exception {
        String meetingId = createImportMeeting();
        previewAndCommit(meetingId, "工号,姓名,字段1", "a12345678,张三,初始值");
        previewAndCommit(meetingId, "工号,姓名,字段1", "a12345678,张三,冲突值1");
        JsonNode third = previewAndCommit(
                meetingId,
                "工号,姓名,字段1",
                "a12345678,张三,冲突值2"
        );
        JsonNode repeated = previewAndCommit(
                meetingId,
                "工号,姓名,字段1",
                "a12345678,张三,冲突值2"
        );

        ParticipantEntity participant = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCase(meetingId, "a12345678")
                .orElseThrow();
        List<ParticipantRecordEntity> records = recordRepository
                .findAllByParticipantIdOrderByRecordOrderAsc(participant.getId());

        assertThat(third.path("appendedRecords").asInt()).isEqualTo(1);
        assertThat(repeated.path("skippedRecords").asInt()).isEqualTo(1);
        assertThat(records)
                .extracting(value -> value.getRecordOrder())
                .containsExactly(1, 2, 3);
        assertThat(records)
                .extracting(value -> readAttributes(value.getAttributesJson()).get("字段1"))
                .containsExactly("初始值", "冲突值1", "冲突值2");
    }

    @Test
    void participantImportPreviewReportsStableFieldUnionAndIgnoresExactRows() throws Exception {
        String meetingId = createImportMeeting();
        previewAndCommit(meetingId, "工号,姓名,已有字段", "a12345678,张三,已有值");

        JsonNode preview = preview(
                meetingId,
                "工号,姓名,新字段,已有字段\n"
                        + "a12345678,张三,新值,已有值\n"
                        + "a12345678,张三,新值,已有值"
        );

        assertThat(preview.path("totalRows").asInt()).isEqualTo(2);
        assertThat(preview.path("validRows").asInt()).isEqualTo(1);
        assertThat(preview.path("ignoredDuplicateRows").asInt()).isEqualTo(1);
        assertThat(preview.path("participantCount").asInt()).isEqualTo(1);
        assertThat(preview.path("recordCount").asInt()).isEqualTo(1);
        assertThat(jsonTextValues(preview.path("newFields"))).containsExactly("新字段");
        assertThat(jsonTextValues(preview.path("existingFields"))).containsExactly("已有字段");
        assertThat(preview.path("rows").get(0).path("expectedAction").asText()).isNotBlank();
    }

    @Test
    void participantFieldNamesReuseRegisteredCasingAcrossImports() throws Exception {
        String meetingId = createImportMeeting();
        previewAndCommit(meetingId, "工号,姓名,Department", "a12345678,张三,研发部");

        previewAndCommit(
                meetingId,
                "工号,姓名,department,字段2",
                "a12345678,张三,研发部,值2"
        );

        ParticipantEntity participant = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCase(meetingId, "a12345678")
                .orElseThrow();
        ParticipantRecordEntity record = recordRepository
                .findAllByParticipantIdOrderByRecordOrderAsc(participant.getId())
                .getFirst();
        assertThat(fieldRepository
                .findAllByMeetingIdOrderBySortOrderAsc(meetingId))
                .extracting(value -> value.getFieldName())
                .containsExactly("Department", "字段2");
        assertThat(readAttributes(record.getAttributesJson()))
                .containsEntry("Department", "研发部")
                .containsEntry("字段2", "值2")
                .doesNotContainKey("department");
    }

    @Test
    void participantNameConflictRejectsCommitWithoutDatabaseWrites() throws Exception {
        String meetingId = createImportMeeting();
        participantService.create(
                meetingId,
                new CreateParticipantRequest(
                        "a12345678", "张三", Map.of(), null
                )
        );
        JsonNode preview = preview(meetingId, "工号,姓名,字段1\na12345678,李四,值1");
        String token = preview.path("token").asText();

        assertThat(jsonTextValues(preview.path("errors")))
                .containsExactly("工号a12345678已对应人员张三");
        mockMvc.perform(post(
                        "/meetings/{meetingId}/imports/{token}/commit",
                        meetingId,
                        token
                ).header(USER_HEADER, DEFAULT_USER))
                .andExpect(status().isConflict());

        ParticipantEntity participant = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCase(meetingId, "a12345678")
                .orElseThrow();
        assertThat(participant.getName()).isEqualTo("张三");
        assertThat(recordRepository
                .findAllByParticipantIdOrderByRecordOrderAsc(participant.getId()))
                .isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void participantNameConflictInsideWorkbookCommitsAsConflictWithoutWrites() throws Exception {
        String meetingId = createImportMeeting();
        JsonNode preview = preview(
                meetingId,
                "工号,姓名,新字段\n"
                        + "a12345678,张三,值1\n"
                        + "a12345678,李四,值2"
        );

        assertThat(preview.path("errors").isEmpty()).isFalse();
        mockMvc.perform(post(
                        "/meetings/{meetingId}/imports/{token}/commit",
                        meetingId,
                        preview.path("token").asText()
                ).header(USER_HEADER, DEFAULT_USER))
                .andExpect(status().isConflict());

        assertThat(participantRepository.countByMeetingId(meetingId)).isZero();
        assertThat(fieldRepository.findAllByMeetingIdOrderBySortOrderAsc(meetingId))
                .isEmpty();
    }

    @Test
    void participantPreviewSimulatesEarlierRowsInTheSameWorkbook() throws Exception {
        String meetingId = createImportMeeting();
        JsonNode preview = preview(
                meetingId,
                "工号,姓名,字段1,字段2\n"
                        + "a12345678,张三,值1,\n"
                        + "a12345678,张三,值1,值2"
        );

        assertThat(preview.path("rows").get(0).path("expectedAction").asText())
                .isEqualTo("新增人员并追加记录");
        assertThat(preview.path("rows").get(1).path("expectedAction").asText())
                .isEqualTo("合并至已有记录");

        commit(meetingId, preview.path("token").asText());

        ParticipantEntity participant = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCase(meetingId, "a12345678")
                .orElseThrow();
        List<ParticipantRecordEntity> records = recordRepository
                .findAllByParticipantIdOrderByRecordOrderAsc(participant.getId());
        assertThat(records).hasSize(1);
        assertThat(readAttributes(records.getFirst().getAttributesJson()))
                .containsEntry("字段1", "值1")
                .containsEntry("字段2", "值2");
    }

    @Test
    void previewErrorsRejectCommitWithoutDatabaseWrites() throws Exception {
        String meetingId = createImportMeeting();
        JsonNode preview = preview(meetingId, "工号,姓名,新字段\n错误工号,张三,值1");

        assertThat(preview.path("errors").isEmpty()).isFalse();
        mockMvc.perform(post(
                        "/meetings/{meetingId}/imports/{token}/commit",
                        meetingId,
                        preview.path("token").asText()
                ).header(USER_HEADER, DEFAULT_USER))
                .andExpect(status().isBadRequest());

        assertThat(participantRepository.countByMeetingId(meetingId)).isZero();
        assertThat(fieldRepository.findAllByMeetingIdOrderBySortOrderAsc(meetingId))
                .isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void participantNameConflictRollsBackEarlierWritesInRequestTransaction() throws Exception {
        String meetingId = createImportMeeting();
        participantService.create(
                meetingId,
                new CreateParticipantRequest(
                        "a12345678", "张三", Map.of(), null
                )
        );
        JsonNode preview = preview(
                meetingId,
                "工号,姓名,新字段\n"
                        + "12345678,王五,先写入\n"
                        + "a12345678,李四,触发冲突"
        );

        mockMvc.perform(post(
                        "/meetings/{meetingId}/imports/{token}/commit",
                        meetingId,
                        preview.path("token").asText()
                ).header(USER_HEADER, DEFAULT_USER))
                .andExpect(status().isConflict());

        assertThat(participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCase(meetingId, "12345678"))
                .isEmpty();
        assertThat(fieldRepository.findAllByMeetingIdOrderBySortOrderAsc(meetingId))
                .isEmpty();
        ParticipantEntity existing = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCase(meetingId, "a12345678")
                .orElseThrow();
        assertThat(recordRepository
                .findAllByParticipantIdOrderByRecordOrderAsc(existing.getId()))
                .isEmpty();
    }

    @Test
    void importingAssignedParticipantReusesParticipantId() throws Exception {
        String meetingId = createImportMeeting();
        ParticipantResult participant = participantService.create(
                meetingId,
                new CreateParticipantRequest(
                        "a12345678", "张三", Map.of(), null
                )
        );
        WorkspaceResponse workspace = workspaceService.getWorkspace(meetingId);
        ElementView seat = workspace.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()))
                .findFirst()
                .orElseThrow();
        seatingService.assign(
                workspace.plan().id(),
                new AssignmentRequest(participant.id(), seat.id())
        );

        previewAndCommit(meetingId, "工号,姓名,字段1", "a12345678,张三,值1");

        ParticipantEntity imported = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCase(meetingId, "a12345678")
                .orElseThrow();
        WorkspaceResponse refreshedWorkspace = workspaceService.getWorkspace(meetingId);
        assertThat(imported.getId()).isEqualTo(participant.id());
        assertThat(refreshedWorkspace.participants().stream()
                .filter(value -> value.id().equals(participant.id()))
                .findFirst()
                .orElseThrow()
                .assignedElementId()).isEqualTo(seat.id());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void concurrentImportAndParticipantRequestsRegisterCaseInsensitiveFieldsWithContinuousUniqueOrder()
            throws Exception {
        String meetingId = createImportMeeting();
        JsonNode importPreview = preview(
                meetingId,
                "工号,姓名,SharedField,ImportField\n"
                        + "a11111111,并发导入人员,导入共享值,导入字段值"
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> commitImportAfterBarrier(
                    meetingId,
                    importPreview.path("token").asText(),
                    ready,
                    start
            ));
            Future<Integer> second = executor.submit(() -> createParticipantAfterBarrier(
                    meetingId,
                    "87654321",
                    "并发新增人员",
                    """
                            {"sharedfield":"新增共享值","ParticipantField":"新增字段值"}
                            """,
                    ready,
                    start
            ));

            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(10, SECONDS)).isEqualTo(200);
            assertThat(second.get(10, SECONDS)).isEqualTo(200);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
        }

        List<MeetingParticipantFieldEntity> fields = fieldRepository
                .findAllByMeetingIdOrderBySortOrderAsc(meetingId);
        assertThat(fields)
                .extracting(value -> value.getFieldName().toLowerCase(ROOT))
                .containsExactlyInAnyOrder("sharedfield", "importfield", "participantfield");
        assertThat(fields)
                .extracting(MeetingParticipantFieldEntity::getSortOrder)
                .containsExactly(1, 2, 3)
                .doesNotHaveDuplicates();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void concurrentImportAndParticipantCreateForSameEmployeeHasOneWinnerWithoutFieldDuplicates()
            throws Exception {
        String meetingId = createImportMeeting();
        JsonNode importPreview = preview(
                meetingId,
                "工号,姓名,SharedField,ImportField\n"
                        + "a22222222,并发导入姓名,导入共享值,导入字段值"
        );
        CountDownLatch bothRequestsAtMeetingLock = new CountDownLatch(2);
        CountDownLatch importHasMeetingLock = new CountDownLatch(1);
        ParticipantFieldRegistrationService registrationTarget =
                AopTestUtils
                        .getUltimateTargetObject(fieldRegistrationService);
        Mockito.doAnswer(invocation -> {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder
                    .currentRequestAttributes())
                    .getRequest();
            boolean importRequest = request.getRequestURI().contains("/imports/");
            bothRequestsAtMeetingLock.countDown();
            if (!bothRequestsAtMeetingLock.await(5, SECONDS)) {
                throw new IllegalStateException("两个请求未同时到达会议锁");
            }
            if (importRequest) {
                Object lockedMeeting = invocation.callRealMethod();
                importHasMeetingLock.countDown();
                return lockedMeeting;
            }
            if (!importHasMeetingLock.await(5, SECONDS)) {
                throw new IllegalStateException("导入请求未先取得会议锁");
            }
            return invocation.callRealMethod();
        }).when(registrationTarget).registerFields(
                ArgumentMatchers.eq(meetingId),
                ArgumentMatchers.any()
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int importStatus;
        int participantStatus;
        try {
            Future<Integer> importFuture = executor.submit(() -> commitImportAfterBarrier(
                    meetingId,
                    importPreview.path("token").asText(),
                    ready,
                    start
            ));
            Future<Integer> participantFuture = executor.submit(() -> createParticipantAfterBarrier(
                    meetingId,
                    "a22222222",
                    "并发新增姓名",
                    """
                            {"sharedfield":"新增共享值","ParticipantField":"新增字段值"}
                            """,
                    ready,
                    start
            ));

            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();
            importStatus = importFuture.get(10, SECONDS);
            participantStatus = participantFuture.get(10, SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
        }

        assertThat(List.of(importStatus, participantStatus))
                .containsExactlyInAnyOrder(200, 409);
        List<ParticipantEntity> participants = participantRepository
                .findAllByMeetingIdOrderByNameAsc(meetingId)
                .stream()
                .filter(value -> value.getEmployeeNo().equalsIgnoreCase("a22222222"))
                .toList();
        assertThat(participants).hasSize(1);

        List<MeetingParticipantFieldEntity> fields = fieldRepository
                .findAllByMeetingIdOrderBySortOrderAsc(meetingId);
        List<String> normalizedFieldNames = fields.stream()
                .map(value -> value.getFieldName().toLowerCase(ROOT))
                .toList();
        assertThat(normalizedFieldNames)
                .hasSize(2)
                .contains("sharedfield")
                .doesNotHaveDuplicates();
        assertThat(normalizedFieldNames)
                .contains(importStatus == 200 ? "importfield" : "participantfield");
        assertThat(fields)
                .extracting(MeetingParticipantFieldEntity::getSortOrder)
                .containsExactly(1, 2)
                .doesNotHaveDuplicates();
    }

    @Test
    void venueTemplatesArePagedAndGloballySearchable() throws Exception {
        String marker = "R10-" + UUID.randomUUID();
        for (int index = 0; index < 12; index++) {
            createVenueWithElements(
                    marker + "-" + index,
                    index % 2 == 0 ? "东校区" : "西校区",
                    List.of(seat("座位" + index, 1, 1, 1, 1))
            );
        }

        mockMvc.perform(get("/venues")
                        .param("keyword", marker)
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(10))
                .andExpect(jsonPath("$.data.total").value(12));
    }

    @Test
    void venueTemplatesAreSearchableByBookingUrl() throws Exception {
        String marker = "booking-" + UUID.randomUUID();
        CreateVenueRequest base = createVenueRequest(
                "预约链接搜索-" + UUID.randomUUID(),
                "主校区",
                List.of(seat("座位", 1, 1, 1, 1))
        );
        VenueDetail created = venueService.create(new CreateVenueRequest(
                base.location(),
                base.campus(),
                base.mainScreenResolution(),
                base.stageDimensions(),
                base.manualCapacity(),
                base.contactInfo(),
                "https://example.test/" + marker,
                base.meetingRoomFunctions(),
                base.servicesProvided(),
                base.description(),
                base.remarks(),
                base.gridRows(),
                base.gridColumns(),
                base.elements()
        ));

        mockMvc.perform(get("/venues")
                        .param("keyword", marker)
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(created.id()))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void venueCreateTrimsAndPersistsAbsoluteHttpsBookingUrl() {
        CreateVenueRequest request = createVenueRequest(
                "安全链接场馆-" + UUID.randomUUID(),
                "主校区",
                List.of()
        );

        VenueDetail created = venueService.create(
                createVenueRequestWithBookingUrl(request, "  https://example.test/booking?q=1  ")
        );

        assertThat(created.bookingUrl()).isEqualTo("https://example.test/booking?q=1");
        assertThat(venueService.get(created.id()).bookingUrl())
                .isEqualTo("https://example.test/booking?q=1");
    }

    @Test
    void venueCreateRejectsUnsafeBookingUrls() {
        List<String> unsafeUrls = List.of(
                "javascript:alert(1)",
                "data:text/html,unsafe",
                "file:///tmp/unsafe",
                "//example.test/booking",
                "https:/missing-host",
                "https://",
                "https://example.test/\nunsafe"
        );

        for (String unsafeUrl : unsafeUrls) {
            CreateVenueRequest request = createVenueRequest(
                    "危险链接场馆-" + UUID.randomUUID(),
                    "主校区",
                    List.of()
            );
            assertThatThrownBy(() -> venueService.create(
                    createVenueRequestWithBookingUrl(request, unsafeUrl)
            )).hasMessage("预定链接必须是绝对 http/https URL");
        }
    }

    @Test
    void venueUpdateTrimsAndPersistsAbsoluteHttpBookingUrl() {
        VenueDetail created = createVenueWithElements(
                "更新安全链接场馆-" + UUID.randomUUID(),
                "主校区",
                List.of()
        );

        VenueDetail updated = venueService.updateInfo(
                created.id(),
                updateInfoRequestWithBookingUrl(
                        created,
                        "  http://example.test/updated-booking  "
                )
        );

        assertThat(updated.bookingUrl()).isEqualTo("http://example.test/updated-booking");
        assertThat(venueService.get(created.id()).bookingUrl())
                .isEqualTo("http://example.test/updated-booking");
    }

    @Test
    void venueUpdateRejectsUnsafeBookingUrlThroughApiAndKeepsPersistedValue()
            throws Exception {
        VenueDetail created = createVenueWithElements(
                "更新危险链接场馆-" + UUID.randomUUID(),
                "主校区",
                List.of()
        );

        mockMvc.perform(post("/venues/{id}/info/update", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                updateInfoRequestWithBookingUrl(created, "javascript:alert(1)")
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg")
                        .value("预定链接必须是绝对 http/https URL"));

        assertThat(venueService.get(created.id()).bookingUrl())
                .isEqualTo("https://example.test/booking");
    }

    @Test
    void venueCreateNormalizesOptionalTextsBeforePersistence() {
        CreateVenueRequest source = createVenueRequest(
                "创建文本规范化场馆-" + UUID.randomUUID(),
                "  北区  ",
                List.of()
        );
        CreateVenueRequest request = new CreateVenueRequest(
                source.location(),
                source.campus(),
                "  3840×2160  ",
                "   ",
                source.manualCapacity(),
                "  张三  ",
                "  https://example.test/normalized  ",
                "\t",
                "  会务支持  ",
                " ",
                "  靠近东门  ",
                source.gridRows(),
                source.gridColumns(),
                source.elements()
        );

        VenueDetail persisted = venueService.get(venueService.create(request).id());

        assertThat(persisted.campus()).isEqualTo("北区");
        assertThat(persisted.mainScreenResolution()).isEqualTo("3840×2160");
        assertThat(persisted.stageDimensions()).isNull();
        assertThat(persisted.contactInfo()).isEqualTo("张三");
        assertThat(persisted.bookingUrl()).isEqualTo("https://example.test/normalized");
        assertThat(persisted.meetingRoomFunctions()).isNull();
        assertThat(persisted.servicesProvided()).isEqualTo("会务支持");
        assertThat(persisted.description()).isNull();
        assertThat(persisted.remarks()).isEqualTo("靠近东门");
    }

    @Test
    void venueUpdateNormalizesOptionalTextsBeforePersistence() {
        VenueDetail created = createVenueWithElements(
                "更新文本规范化场馆-" + UUID.randomUUID(),
                "主校区",
                List.of()
        );
        UpdateVenueInfoRequest request = new UpdateVenueInfoRequest(
                created.location(),
                "   ",
                "  1920×1080  ",
                "\t",
                created.manualCapacity(),
                "  李四  ",
                " ",
                "  视频会议  ",
                "\n",
                "  更新说明  ",
                "   ",
                created.rowVersion()
        );

        venueService.updateInfo(created.id(), request);
        VenueDetail persisted = venueService.get(created.id());

        assertThat(persisted.campus()).isNull();
        assertThat(persisted.mainScreenResolution()).isEqualTo("1920×1080");
        assertThat(persisted.stageDimensions()).isNull();
        assertThat(persisted.contactInfo()).isEqualTo("李四");
        assertThat(persisted.bookingUrl()).isNull();
        assertThat(persisted.meetingRoomFunctions()).isEqualTo("视频会议");
        assertThat(persisted.servicesProvided()).isNull();
        assertThat(persisted.description()).isEqualTo("更新说明");
        assertThat(persisted.remarks()).isNull();
    }

    @Test
    void venueListRejectsPageNumberBelowOne() throws Exception {
        mockMvc.perform(get("/venues")
                        .param("pageNum", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.msg").value("pageNum：必须大于等于1"));
    }

    @Test
    void venueListRejectsPageSizeBelowOne() throws Exception {
        mockMvc.perform(get("/venues")
                        .param("pageNum", "1")
                        .param("pageSize", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.msg").value("pageSize：必须大于等于1"));
    }

    @Test
    void venueLocationIsTrimmedCaseInsensitiveUniqueAndZeroSeatVenueIsUnusable()
            throws Exception {
        String location = "Room-" + UUID.randomUUID();
        VenueDetail created = createVenueWithElements(location, "主校区", List.of());

        mockMvc.perform(post("/venues/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(createVenueRequest(
                                "  " + location.toUpperCase(ROOT) + "  ",
                                "主校区",
                                List.of()
                        ))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/venues")
                        .param("keyword", location)
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(created.id()))
                .andExpect(jsonPath("$.data.records[0].seatCount").value(0))
                .andExpect(jsonPath("$.data.records[0].usable").value(false));
    }

    @Test
    void venueLocationAvailabilityIsExactAndCanExcludeCurrentTemplate() throws Exception {
        String location = "精确判重-" + UUID.randomUUID();
        VenueDetail created = createVenueWithElements(location, "主校区", List.of());

        mockMvc.perform(get("/venues/location-availability")
                        .param("location", "  " + location.toUpperCase(ROOT) + "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
        mockMvc.perform(get("/venues/location-availability")
                        .param("location", "  " + location.toUpperCase(ROOT) + "  ")
                        .param("excludeId", created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
        mockMvc.perform(get("/venues/location-availability")
                        .param("location", location + "-其他"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void venueTemplateRejectsStaleUpdates() throws Exception {
        VenueDetail created = createVenueWithElements(
                "并发场馆-" + UUID.randomUUID(),
                "主校区",
                List.of(seat("座位", 1, 1, 1, 1))
        );
        VenueDetail updated = venueService.updateInfo(
                created.id(),
                updateInfoRequest(created, created.location() + "-更新")
        );

        assertThatThrownBy(() -> venueService.updateInfo(
                created.id(),
                updateInfoRequest(created, created.location() + "-过期")
        )).hasMessage("场馆模板已被其他用户修改，请刷新后重试");
        assertThatThrownBy(() -> venueService.updateLayout(
                created.id(),
                new UpdateVenueLayoutRequest(
                        5,
                        5,
                        List.of(seat("过期座位", 1, 1, 1, 1)),
                        created.rowVersion()
                )
        )).hasMessage("场馆模板已被其他用户修改，请刷新后重试");
        mockMvc.perform(post("/venues/{id}/info/update", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                updateInfoRequest(created, created.location() + "-接口过期")
                        )))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/venues/{id}/layout/update", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new UpdateVenueLayoutRequest(
                                5,
                                5,
                                List.of(seat("接口过期座位", 1, 1, 1, 1)),
                                created.rowVersion()
                        ))))
                .andExpect(status().isConflict());
        assertThat(updated.rowVersion()).isEqualTo(created.rowVersion() + 1);
    }

    @Test
    void deletingVenueKeepsMeetingSnapshot() {
        VenueDetail venue = createVenueWithElements(
                "待删除场馆-" + UUID.randomUUID(),
                "主校区",
                List.of(seat("座位", 1, 1, 1, 1))
        );
        MeetingSummary meeting = meetingService.create(
                new CreateMeetingRequest("保留快照-" + UUID.randomUUID(), venue.id())
        );

        venueService.delete(venue.id());

        assertThat(venueElementRepository
                .findAllByVenueTemplateIdOrderByStartRowAscStartColumnAsc(venue.id()))
                .isEmpty();
        assertThat(meetingRepository.findById(meeting.id()).orElseThrow().getVenueTemplateId())
                .isNull();
        assertThat(workspaceService.getWorkspace(meeting.id()).layout().elements())
                .extracting(ElementView::name)
                .containsExactly("座位");
    }

    @Test
    void venueApiResponsesDoNotContainLegacyTemplateFields() throws Exception {
        VenueDetail venue = createVenueWithElements(
                "字段契约-" + UUID.randomUUID(),
                "主校区",
                List.of(seat("座位", 1, 1, 1, 1))
        );

        String detailJson = mockMvc.perform(get("/venues/{id}", venue.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String layoutJson = mockMvc.perform(get("/venues/{id}/layout", venue.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(detailJson + layoutJson)
                .doesNotContain("preset", "versionNo", "frontDirection", "rotation");
    }

    @Test
    void meetingCopiesVenueLayoutAndIgnoresLaterTemplateChanges() {
        VenueDetail venue = createVenueWithElements(
                "快照场馆-" + UUID.randomUUID(),
                "主校区",
                List.of(
                        seat("双连座", 2, 3, 1, 2),
                        generic("主屏幕布", 1, 1, 1, 2)
                )
        );
        MeetingSummary meeting = meetingService.create(
                new CreateMeetingRequest("评审会-" + UUID.randomUUID(), venue.id())
        );
        venueService.updateLayout(
                venue.id(),
                new UpdateVenueLayoutRequest(
                        5,
                        5,
                        List.of(generic("已修改", 1, 1, 1, 1)),
                        venue.rowVersion()
                )
        );

        WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());

        assertThat(workspace.layout().elements())
                .extracting(ElementView::name)
                .containsExactly("主屏幕布", "双连座");
    }

    @Test
    void meetingSnapshotSupportsMaximumVenueLocationLength() {
        String location = "长".repeat(200);
        VenueDetail venue = createVenueWithElements(
                location,
                "主校区",
                List.of(seat("座位", 1, 1, 1, 1))
        );

        MeetingSummary meeting = meetingService.create(
                new CreateMeetingRequest("长地点会议-" + UUID.randomUUID(), venue.id())
        );

        assertThat(workspaceService.getWorkspace(meeting.id()).meeting().layoutName())
                .isEqualTo(location);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void meetingSnapshotWaitsForConcurrentVenueMutationLock() throws Exception {
        VenueDetail venue = createVenueWithElements(
                "并发快照场馆-" + UUID.randomUUID(),
                "主校区",
                List.of(seat("座位", 1, 1, 1, 1))
        );
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch creatorReady = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> locker = executor.submit(() -> {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "select id from t_venue_templates where id = ? for update"
                     )) {
                    connection.setAutoCommit(false);
                    statement.setString(1, venue.id());
                    statement.executeQuery();
                    lockAcquired.countDown();
                    if (!releaseLock.await(5, SECONDS)) {
                        throw new IllegalStateException("未按时释放场馆模板锁");
                    }
                    connection.commit();
                    return null;
                }
            });
            assertThat(lockAcquired.await(5, SECONDS)).isTrue();
            Future<MeetingSummary> creator = executor.submit(() -> {
                CurrentUserHolder.set(new CurrentUser(DEFAULT_USER, DEFAULT_USER, Set.of()));
                creatorReady.countDown();
                try {
                    return meetingService.create(new CreateMeetingRequest(
                            "并发快照会议-" + UUID.randomUUID(),
                            venue.id()
                    ));
                } finally {
                    CurrentUserHolder.clear();
                }
            });
            assertThat(creatorReady.await(5, SECONDS)).isTrue();

            assertThatThrownBy(() -> creator.get(1, SECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseLock.countDown();
            MeetingSummary meeting = creator.get(5, SECONDS);
            assertThat(workspaceAs(DEFAULT_USER, meeting.id())
                    .path("layout").path("elements")).hasSize(1);
            locker.get(5, SECONDS);
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, SECONDS)).isTrue();
        }
    }

    @Test
    void genericElementCannotReceiveParticipantAndMultiCellSeatCanReceiveOnlyOne() {
        VenueDetail venue = createVenueWithElements(
                "排座元素场馆-" + UUID.randomUUID(),
                "主校区",
                List.of(
                        generic("舞台", 1, 1, 1, 2),
                        seat("双连座", 2, 1, 1, 2)
                )
        );
        MeetingSummary meeting = meetingService.create(
                new CreateMeetingRequest("元素排座-" + UUID.randomUUID(), venue.id())
        );
        ParticipantResult first = participantService.create(
                meeting.id(),
                new CreateParticipantRequest("a70000001", "第一人", Map.of(), null)
        );
        ParticipantResult second = participantService.create(
                meeting.id(),
                new CreateParticipantRequest("a70000002", "第二人", Map.of(), null)
        );
        WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());
        ElementView genericElement = workspace.layout().elements().stream()
                .filter(element -> element.kind().equals("GENERIC"))
                .findFirst()
                .orElseThrow();
        ElementView seatElement = workspace.layout().elements().stream()
                .filter(element -> element.kind().equals("SEAT"))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> seatingService.assign(
                workspace.plan().id(),
                new AssignmentRequest(first.id(), genericElement.id())
        )).hasMessage("目标元素不是可排座座位");
        seatingService.assign(
                workspace.plan().id(),
                new AssignmentRequest(first.id(), seatElement.id())
        );
        assertThatThrownBy(() -> seatingService.assign(
                workspace.plan().id(),
                new AssignmentRequest(second.id(), seatElement.id())
        )).isInstanceOf(com.company.meetinghelper.common.exception.ApiException.class);
    }

    @Test
    void meetingNameIsCheckedOnBothCreateAttempts() {
        VenueSummary venue = defaultVenue();
        String name = "测试会议-" + UUID.randomUUID();
        CreateMeetingRequest request = new CreateMeetingRequest(name, venue.id());

        meetingService.create(request);

        assertThatThrownBy(() -> meetingService.create(request))
                .hasMessage("会议名称已存在");
    }

    @Test
    void meetingNameCanBeUpdatedAndStillRejectsDuplicates() {
        VenueSummary venue = defaultVenue();
        MeetingSummary first = meetingService.create(
                new CreateMeetingRequest("待改名会议-" + UUID.randomUUID(), venue.id())
        );
        MeetingSummary second = meetingService.create(
                new CreateMeetingRequest("已占用会议-" + UUID.randomUUID(), venue.id())
        );
        String updatedName = first.name() + "-更新";

        MeetingSummary updated = meetingService.updateName(
                first.id(),
                new UpdateMeetingNameRequest(updatedName)
        );

        assertThat(updated.name()).isEqualTo(updatedName);
        assertThat(meetingRepository.findByIdAndCreatedById(first.id(), DEFAULT_USER)
                .orElseThrow()
                .getName()).isEqualTo(updatedName);
        assertThatThrownBy(() -> meetingService.updateName(
                first.id(),
                new UpdateMeetingNameRequest(second.name())
        )).hasMessage("会议名称已存在");
    }

    private VenueDetail createVenueWithElements(
            String location,
            String campus,
            List<ElementInput> elements
    ) {
        return venueService.create(createVenueRequest(location, campus, elements));
    }

    private CreateVenueRequest createVenueRequest(
            String location,
            String campus,
            List<ElementInput> elements
    ) {
        return new CreateVenueRequest(
                location,
                campus,
                "3840×2160",
                "8m×3m",
                elements.size(),
                "010-12345678",
                "https://example.test/booking",
                "视频会议、无线投屏",
                "会务支持",
                "集成测试场馆",
                "自动创建",
                5,
                5,
                elements
        );
    }

    private UpdateVenueInfoRequest updateInfoRequest(VenueDetail source, String location) {
        return new UpdateVenueInfoRequest(
                location,
                source.campus(),
                source.mainScreenResolution(),
                source.stageDimensions(),
                source.manualCapacity(),
                source.contactInfo(),
                source.bookingUrl(),
                source.meetingRoomFunctions(),
                source.servicesProvided(),
                source.description(),
                source.remarks(),
                source.rowVersion()
        );
    }

    private CreateVenueRequest createVenueRequestWithBookingUrl(
            CreateVenueRequest source,
            String bookingUrl
    ) {
        return new CreateVenueRequest(
                source.location(),
                source.campus(),
                source.mainScreenResolution(),
                source.stageDimensions(),
                source.manualCapacity(),
                source.contactInfo(),
                bookingUrl,
                source.meetingRoomFunctions(),
                source.servicesProvided(),
                source.description(),
                source.remarks(),
                source.gridRows(),
                source.gridColumns(),
                source.elements()
        );
    }

    private UpdateVenueInfoRequest updateInfoRequestWithBookingUrl(
            VenueDetail source,
            String bookingUrl
    ) {
        return new UpdateVenueInfoRequest(
                source.location(),
                source.campus(),
                source.mainScreenResolution(),
                source.stageDimensions(),
                source.manualCapacity(),
                source.contactInfo(),
                bookingUrl,
                source.meetingRoomFunctions(),
                source.servicesProvided(),
                source.description(),
                source.remarks(),
                source.rowVersion()
        );
    }

    private ElementInput seat(
            String name,
            int row,
            int column,
            int rowSpan,
            int columnSpan
    ) {
        return new ElementInput(
                "SEAT", name, row, column, rowSpan, columnSpan, "#ffffff", "#8fb4e8"
        );
    }

    private ElementInput generic(
            String name,
            int row,
            int column,
            int rowSpan,
            int columnSpan
    ) {
        return new ElementInput(
                "GENERIC", name, row, column, rowSpan, columnSpan, "#dbeafe", "#93c5fd"
        );
    }

    private VenueSummary defaultVenue() {
        String location = "默认测试场馆-" + UUID.randomUUID();
        ArrayList<ElementInput> elements = new ArrayList<ElementInput>();
        for (int row = 1; row <= 5; row++) {
            for (int column = 1; column <= 5; column++) {
                elements.add(seat("座位-" + row + "-" + column, row, column, 1, 1));
            }
        }
        createVenueWithElements(location, "主校区", elements);
        return venueService.list(location, "", 1, 10).records().getFirst();
    }

    private String createSeatingScenario(int participantCount, int assignedCount) {
        VenueSummary venue = defaultVenue();
        MeetingSummary meeting = meetingService.create(new CreateMeetingRequest(
                "排座场景测试-" + UUID.randomUUID(),
                venue.id()
        ));
        for (int index = 1; index <= participantCount; index++) {
            participantService.create(
                    meeting.id(),
                    new CreateParticipantRequest(
                            "a%08d".formatted(index),
                            "测试人员" + index,
                            Map.of(),
                            null
                    )
            );
        }
        WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());
        List<ElementView> seats = workspace.layout().elements().stream()
                .filter(value -> "SEAT".equals(value.kind()))
                .limit(assignedCount)
                .toList();
        for (int index = 0; index < assignedCount; index++) {
            seatingService.assign(
                    workspace.plan().id(),
                    new AssignmentRequest(workspace.participants().get(index).id(), seats.get(index).id())
            );
        }
        return meeting.id();
    }

    private String createImportMeeting() {
        VenueSummary venue = defaultVenue();
        return meetingService.create(new CreateMeetingRequest(
                "人员导入测试-" + UUID.randomUUID(),
                venue.id()
        )).id();
    }

    private int commitImportAfterBarrier(
            String meetingId,
            String token,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, SECONDS)) {
            throw new IllegalStateException("并发请求未按时开始");
        }
        return mockMvc.perform(post(
                        "/meetings/{meetingId}/imports/{token}/commit",
                        meetingId,
                        token
                ).header(USER_HEADER, DEFAULT_USER))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int createParticipantAfterBarrier(
            String meetingId,
            String employeeNo,
            String name,
            String attributesJson,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, SECONDS)) {
            throw new IllegalStateException("并发请求未按时开始");
        }
        return mockMvc.perform(post("/meetings/{meetingId}/participants", meetingId)
                        .header(USER_HEADER, DEFAULT_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeNo": "%s",
                                  "name": "%s",
                                  "attributes": %s
                                }
                                """.formatted(employeeNo, name, attributesJson)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String createMeetingAs(String userId, String name) throws Exception {
        String venueId = defaultVenue().id();
        MvcResult result = mockMvc.perform(post("/meetings/create-from-venue")
                        .header(USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new CreateMeetingRequest(name, venueId)
                        )))
                .andExpect(status().isOk())
                .andReturn();
        return responseJson(result).path("id").asText();
    }

    private String createParticipantAs(
            String userId,
            String meetingId,
            String employeeNo,
            String name
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/meetings/{meetingId}/participants", meetingId)
                        .header(USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new CreateParticipantRequest(employeeNo, name, Map.of(), null)
                        )))
                .andExpect(status().isOk())
                .andReturn();
        return responseJson(result).path("id").asText();
    }

    private JsonNode previewAs(String userId, String meetingId, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "participants.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );
        MvcResult result = mockMvc.perform(multipart(
                        "/meetings/{meetingId}/imports/preview",
                        meetingId
                ).file(file).header(USER_HEADER, userId))
                .andExpect(status().isOk())
                .andReturn();
        return responseJson(result);
    }

    private JsonNode responseJson(MvcResult result) {
        try {
            JsonNode envelope = objectMapper.readTree(result.getResponse().getContentAsByteArray());
            assertThat(envelope.path("code").asInt()).isZero();
            assertThat(envelope.path("msg").asText()).isEqualTo("success");
            return envelope.path("data");
        } catch (IOException exception) {
            throw new IllegalStateException("无法解析测试响应", exception);
        }
    }

    private JsonNode workspaceAs(String userId, String meetingId) throws Exception {
        MvcResult result = mockMvc.perform(get("/meetings/{meetingId}/workspace", meetingId)
                        .header(USER_HEADER, userId))
                .andExpect(status().isOk())
                .andReturn();
        return responseJson(result);
    }

    private String assignableSeatId(JsonNode workspace) {
        return StreamSupport.stream(
                        workspace.path("layout").path("elements").spliterator(),
                        false
                )
                .filter(element -> "SEAT".equals(element.path("kind").asText()))
                .findFirst()
                .orElseThrow()
                .path("id")
                .asText();
    }

    private String assignmentJson(String participantId, String targetElementId) {
        return """
                {"participantId":"%s","targetElementId":"%s"}
                """.formatted(participantId, targetElementId);
    }

    private String assignmentsJson(String participantId, String targetElementId) {
        return """
                {"assignments":[%s]}
                """.formatted(assignmentJson(participantId, targetElementId));
    }

    private void assertUnassigned(JsonNode workspace, String participantId) {
        JsonNode participant = StreamSupport.stream(
                        workspace.path("participants").spliterator(),
                        false
                )
                .filter(value -> participantId.equals(value.path("id").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(participant.path("assignedElementId").isNull()).isTrue();
        assertThat(workspace.path("items")).isEmpty();
    }

    private JsonNode previewAndCommit(String meetingId, String header, String row) throws Exception {
        JsonNode preview = preview(meetingId, header + "\n" + row);
        return commit(meetingId, preview.path("token").asText());
    }

    private JsonNode preview(String meetingId, String csv) throws Exception {
        return preview(meetingId, workbook(csv));
    }

    private JsonNode preview(String meetingId, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "participants.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );
        MvcResult result = mockMvc.perform(multipart(
                        "/meetings/{meetingId}/imports/preview",
                        meetingId
                ).file(file).header(USER_HEADER, DEFAULT_USER))
                .andExpect(status().isOk())
                .andReturn();
        return responseJson(result);
    }

    private JsonNode commit(String meetingId, String token) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/meetings/{meetingId}/imports/{token}/commit",
                        meetingId,
                        token
                ).header(USER_HEADER, DEFAULT_USER))
                .andExpect(status().isOk())
                .andReturn();
        return responseJson(result);
    }

    private byte[] workbook(String csv) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("参会人员");
            String[] lines = csv.split("\\R");
            for (int rowIndex = 0; rowIndex < lines.length; rowIndex++) {
                XSSFRow row = sheet.createRow(rowIndex);
                String[] values = lines[rowIndex].split(",", -1);
                for (int columnIndex = 0; columnIndex < values.length; columnIndex++) {
                    row.createCell(columnIndex).setCellValue(values[columnIndex]);
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private Map<String, String> readAttributes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法解析测试人员属性", exception);
        }
    }

    private void saveRecord(String participantId, int recordOrder, Map<String, String> attributes)
            throws Exception {
        ParticipantRecordEntity record = new ParticipantRecordEntity();
        record.setParticipantId(participantId);
        record.setRecordOrder(recordOrder);
        record.setAttributesJson(objectMapper.writeValueAsString(attributes));
        recordRepository.saveAndFlush(record);
    }

    private List<String> cellValues(Row row) {
        ArrayList<String> values = new ArrayList<String>();
        for (int column = 0; column < row.getLastCellNum(); column++) {
            values.add(row.getCell(column) == null ? "" : row.getCell(column).toString());
        }
        return values;
    }

    private List<String> jsonTextValues(JsonNode values) {
        ArrayList<String> result = new ArrayList<String>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    @TestConfiguration
    static class TestUserConfiguration {
        @Bean
        OncePerRequestFilter testCurrentUserFilter() {
            return new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain
                ) throws ServletException, IOException {
                    CurrentUser previous = CurrentUserHolder.get();
                    String userId = request.getHeader(USER_HEADER);
                    if (userId != null) {
                        String normalizedUserId = userId.trim();
                        CurrentUserHolder.set(new CurrentUser(
                                normalizedUserId,
                                normalizedUserId,
                                Set.of()
                        ));
                    }
                    try {
                        filterChain.doFilter(request, response);
                    } finally {
                        CurrentUserHolder.clear();
                        if (previous != null) {
                            CurrentUserHolder.set(previous);
                        }
                    }
                }
            };
        }
    }
}
