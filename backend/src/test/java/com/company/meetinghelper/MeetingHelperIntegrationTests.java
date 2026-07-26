package com.company.meetinghelper;

import com.company.meetinghelper.bootstrap.DemoDataInitializer;
import com.company.meetinghelper.support.PostgreSqlTestDatabaseInitializer;
import com.company.meetinghelper.export.service.ExportService;
import com.company.meetinghelper.meeting.api.dto.request.CreateMeetingRequest;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.meeting.service.MeetingService;
import com.company.meetinghelper.participant.api.dto.request.CreateParticipantRequest;
import com.company.meetinghelper.participant.api.dto.request.UpdateAttendanceRequest;
import com.company.meetinghelper.participant.entity.AttendanceStatus;
import com.company.meetinghelper.participant.entity.MeetingParticipantFieldEntity;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import com.company.meetinghelper.participant.entity.ParticipantRecordEntity;
import com.company.meetinghelper.participant.repository.MeetingParticipantFieldRepository;
import com.company.meetinghelper.participant.repository.ParticipantRecordRepository;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import com.company.meetinghelper.participant.service.ParticipantFieldRegistrationService;
import com.company.meetinghelper.participant.service.ParticipantService;
import com.company.meetinghelper.seating.api.dto.request.AssignmentRequest;
import com.company.meetinghelper.seating.api.dto.request.AssignmentInput;
import com.company.meetinghelper.seating.api.dto.request.CreateVersionRequest;
import com.company.meetinghelper.seating.api.dto.request.SaveAssignmentsRequest;
import com.company.meetinghelper.seating.repository.PlanVersionRepository;
import com.company.meetinghelper.seating.service.PlanVersionService;
import com.company.meetinghelper.seating.service.SeatingService;
import com.company.meetinghelper.venue.api.dto.ElementInput;
import com.company.meetinghelper.venue.api.dto.request.CreateVenueRequest;
import com.company.meetinghelper.venue.service.VenueService;
import com.company.meetinghelper.workspace.service.WorkspaceService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MeetingHelperIntegrationTests.OptimisticLockTestController.class)
@ContextConfiguration(initializers = PostgreSqlTestDatabaseInitializer.class)
class MeetingHelperIntegrationTests {
    private static final String USER_HEADER = "X-User-Id";
    private static final String DEFAULT_USER = "demo-secretary";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private DemoDataInitializer demoDataInitializer;

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
    private MeetingParticipantFieldRepository fieldRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void bindDefaultUserToDirectServiceCalls() {
        var request = new MockHttpServletRequest();
        request.addHeader(USER_HEADER, DEFAULT_USER);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearDirectServiceUser() {
        RequestContextHolder.resetRequestAttributes();
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
    void demoMeetingInitializesGenericFieldsAndSeparateSecondAndThirdBatchRecords() {
        assertThat(venueService.list().getFirst())
                .satisfies(value -> {
                    assertThat(value.id()).isEqualTo("preset-auditorium-hall");
                    assertThat(value.name()).isEqualTo("多功能礼堂");
                });
        var demoMeeting = meetingRepository
                .findAllByCreatedByIdAndDeletedFalseOrderByUpdatedAtDesc(DEFAULT_USER)
                .stream()
                .filter(value -> value.getName().equals("2026年度荣誉表彰大会"))
                .findFirst()
                .orElseThrow();

        assertThat(fieldRepository
                .findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(demoMeeting.getId()))
                .extracting(MeetingParticipantFieldEntity::getFieldName)
                .containsExactly("部门", "人员类型", "职级", "批次", "奖项名称");

        var repeatedParticipant = participantRepository
                .findAllByMeetingIdAndDeletedFalseOrderByNameAsc(demoMeeting.getId())
                .stream()
                .filter(participant -> recordRepository
                        .findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(
                                participant.getId()
                        )
                        .stream()
                        .map(record -> readAttributes(record.getAttributesJson()).get("批次"))
                        .toList()
                        .equals(java.util.List.of("第二批", "第三批")))
                .findFirst()
                .orElseThrow();

        assertThat(recordRepository
                .findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(
                        repeatedParticipant.getId()
                ))
                .extracting(value -> readAttributes(value.getAttributesJson()))
                .containsExactly(
                        Map.of(
                                "部门", "平台研发部",
                                "人员类型", "特邀嘉宾",
                                "职级", "20",
                                "批次", "第二批",
                                "奖项名称", "卓越创新奖"
                        ),
                        Map.of(
                                "部门", "平台研发部",
                                "人员类型", "特邀嘉宾",
                                "职级", "20",
                                "批次", "第三批",
                                "奖项名称", "特别贡献奖"
                        )
                );
    }

    @Test
    void demoMeetingInitializationIsIdempotentAndGivesEveryParticipantARecord() {
        var demoMeetings = meetingRepository
                .findAllByCreatedByIdAndDeletedFalseOrderByUpdatedAtDesc(DEFAULT_USER)
                .stream()
                .filter(value -> value.getName().equals("2026年度荣誉表彰大会"))
                .toList();
        var demoMeeting = demoMeetings.getFirst();
        var participants = participantRepository
                .findAllByMeetingIdAndDeletedFalseOrderByNameAsc(demoMeeting.getId());
        var participantIds = participants.stream().map(ParticipantEntity::getId).toList();
        var records = recordRepository
                .findAllByParticipantIdInAndDeletedFalseOrderByParticipantIdAscRecordOrderAsc(
                        participantIds
                );

        assertThat(demoMeetings).hasSize(1);
        assertThat(participants).hasSize(28);
        assertThat(records).hasSize(33);
        assertThat(participants).allSatisfy(participant -> assertThat(records)
                .anySatisfy(record -> assertThat(record.getParticipantId())
                        .isEqualTo(participant.getId())));

        demoDataInitializer.run(null);

        assertThat(meetingRepository
                .findAllByCreatedByIdAndDeletedFalseOrderByUpdatedAtDesc(DEFAULT_USER)
                .stream()
                .filter(value -> value.getName().equals("2026年度荣誉表彰大会")))
                .hasSize(1);
        assertThat(fieldRepository
                .findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(demoMeeting.getId()))
                .hasSize(5);
        assertThat(participantRepository
                .findAllByMeetingIdAndDeletedFalseOrderByNameAsc(demoMeeting.getId()))
                .hasSize(28);
        assertThat(recordRepository
                .findAllByParticipantIdInAndDeletedFalseOrderByParticipantIdAscRecordOrderAsc(
                        participantIds
                ))
                .hasSize(33);
    }

    @Test
    void meetingApisRequireANonBlankCurrentUserHeader() throws Exception {
        mockMvc.perform(get("/meetings"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/meetings").header(USER_HEADER, "   "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meetingAndAllChildResourcesAreIsolatedByOwner() throws Exception {
        var suffix = java.util.UUID.randomUUID();
        var userAMeetingName = "用户A会议-" + suffix;
        var userBMeetingName = "用户B会议-" + suffix;
        var userAMeetingId = createMeetingAs("user-a", userAMeetingName);
        var userBMeetingId = createMeetingAs("user-b", userBMeetingName);

        var userAList = responseJson(mockMvc.perform(
                        get("/meetings").header(USER_HEADER, "user-a"))
                .andExpect(status().isOk())
                .andReturn());
        var userBList = responseJson(mockMvc.perform(
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

        var participantId = createParticipantAs(
                "user-b",
                userBMeetingId,
                "b00000001",
                "用户B人员"
        );
        var workspace = responseJson(mockMvc.perform(
                        get("/meetings/{meetingId}/workspace", userBMeetingId)
                                .header(USER_HEADER, "user-b"))
                .andExpect(status().isOk())
                .andReturn());
        var planId = workspace.path("plan").path("id").asText();
        var seatId = java.util.stream.StreamSupport.stream(
                        workspace.path("layout").path("elements").spliterator(),
                        false
                )
                .filter(element -> element.path("assignable").asBoolean()
                        && element.path("capacity").asInt() == 1)
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
        mockMvc.perform(put(
                        "/meetings/{meetingId}/participants/{participantId}/attendance",
                        userBMeetingId,
                        participantId
                ).header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendanceStatus\":\"TEMPORARILY_ABSENT\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(
                        "/meetings/{meetingId}/participants/{participantId}",
                        userBMeetingId,
                        participantId
                ).header(USER_HEADER, "user-a"))
                .andExpect(status().isNotFound());

        var importContent = workbook("工号,姓名,批次\nb00000001,用户B人员,第一批");
        mockMvc.perform(multipart("/meetings/{meetingId}/imports/preview", userBMeetingId)
                        .file(new MockMultipartFile(
                                "file",
                                "participants.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                importContent
                        ))
                        .header(USER_HEADER, "user-a"))
                .andExpect(status().isNotFound());
        var preview = previewAs("user-b", userBMeetingId, importContent);
        var token = preview.path("token").asText();
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

        var assignmentJson = """
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
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/plans/{planId}/assignments", planId)
                        .header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignments":[%s]}
                                """.formatted(assignmentJson)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(
                        "/plans/{planId}/participants/{participantId}/assignment",
                        planId,
                        participantId
                ).header(USER_HEADER, "user-a"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(
                        "/plans/{planId}/participants/{participantId}/lock",
                        planId,
                        participantId
                ).param("locked", "true").header(USER_HEADER, "user-a"))
                .andExpect(status().isNotFound());

        var versionRequest = """
                {"versionName":"用户B发布版","changeNote":"","automatic":false}
                """;
        mockMvc.perform(post("/plans/{planId}/versions", planId)
                        .header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionRequest))
                .andExpect(status().isNotFound());
        var version = responseJson(mockMvc.perform(post("/plans/{planId}/versions", planId)
                        .header(USER_HEADER, "user-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionRequest))
                .andExpect(status().isOk())
                .andReturn());
        var versionId = version.path("id").asText();
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
        mockMvc.perform(get("/meetings/{meetingId}/exports/pdf", userBMeetingId)
                        .param("versionId", versionId)
                        .header(USER_HEADER, "user-a"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/meetings/{meetingId}/workspace", userAMeetingId)
                        .header(USER_HEADER, "user-a"))
                .andExpect(status().isOk());
    }

    @Test
    void seatingAssignmentsDoNotRevealForeignOrMissingParticipantAndElementIds() throws Exception {
        var suffix = java.util.UUID.randomUUID();
        var userAMeetingId = createMeetingAs("user-a", "座位侧信道A-" + suffix);
        var userBMeetingId = createMeetingAs("user-b", "座位侧信道B-" + suffix);
        var userAParticipantId = createParticipantAs(
                "user-a", userAMeetingId, "a90000001", "用户A人员"
        );
        var userBParticipantId = createParticipantAs(
                "user-b", userBMeetingId, "b90000001", "用户B人员"
        );
        var userAWorkspace = workspaceAs("user-a", userAMeetingId);
        var userBWorkspace = workspaceAs("user-b", userBMeetingId);
        var userAPlanId = userAWorkspace.path("plan").path("id").asText();
        var userAElementId = assignableSeatId(userAWorkspace);
        var userBElementId = assignableSeatId(userBWorkspace);
        var missingId = "missing-" + suffix;

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

        mockMvc.perform(put("/plans/{planId}/assignments", userAPlanId)
                        .header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentsJson(userBParticipantId, userAElementId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/plans/{planId}/assignments", userAPlanId)
                        .header(USER_HEADER, "user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentsJson(userAParticipantId, userBElementId)))
                .andExpect(status().isNotFound());

        assertUnassigned(workspaceAs("user-a", userAMeetingId), userAParticipantId);
        assertUnassigned(workspaceAs("user-b", userBMeetingId), userBParticipantId);
    }

    @Test
    void optimisticLockFailuresReturnAFriendlyConflict() throws Exception {
        mockMvc.perform(get("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("数据已更新"))
                .andExpect(jsonPath("$.detail").value("数据已发生变化，请刷新后重试"));
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
                        "12345678", "测试人员", java.util.Map.of(), null
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

        var after = workspaceService.getWorkspace(meeting.id());
        var participant = after.participants().stream()
                .filter(value -> value.employeeNo().equals("87654321"))
                .findFirst()
                .orElseThrow();
        assertThat(participant.assignedElementId()).isEqualTo(seat.id());
        assertThat(participant.primaryAttributes())
                .containsEntry("活动角色", "主持人")
                .containsEntry("批次", "第一批");
        assertThat(participant.records()).hasSize(1);
        assertThat(participant.records().getFirst().recordOrder()).isEqualTo(1);
        assertThat(fieldRepository.findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(meeting.id()))
                .extracting(value -> value.getFieldName())
                .containsExactly("活动角色", "批次");
        assertThat(after.items().stream()
                .filter(value -> participant.id().equals(value.participantId()))
                .findFirst()
                .orElseThrow()
                .targetElementIds()).containsExactly(seat.id());
    }

    @Test
    void workspaceAggregatesDynamicParticipantRecordsByRecordAndFieldOrder() throws Exception {
        var meetingId = createImportMeeting();
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

        var workspace = workspaceService.getWorkspace(meetingId);
        var person = workspace.participants().stream()
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
        var meetingId = createImportMeeting();
        previewAndCommit(
                meetingId,
                "工号,姓名,批次,奖项名称",
                "a12345678,张三,第二批,创新奖"
        );
        var participant = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, "a12345678")
                .orElseThrow();
        var blankRecord = new ParticipantRecordEntity();
        blankRecord.setParticipantId(participant.getId());
        blankRecord.setRecordOrder(0);
        blankRecord.setAttributesJson(objectMapper.writeValueAsString(
                java.util.Map.of("批次", "   ", "奖项名称", "")
        ));
        recordRepository.saveAndFlush(blankRecord);

        var person = workspaceService.getWorkspace(meetingId).participants().stream()
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
        var venue = venueService.list().getFirst();
        var meeting = meetingService.create(new CreateMeetingRequest(
                "旧人员锁兼容测试-" + java.util.UUID.randomUUID(),
                venue.id()
        ));
        var participant = participantService.create(
                meeting.id(),
                new CreateParticipantRequest(
                        "12345678", "测试人员", Map.of(), null
                )
        );
        var entity = participantRepository.findById(participant.id()).orElseThrow();
        entity.setLocked(true);
        participantRepository.saveAndFlush(entity);
        var workspace = workspaceService.getWorkspace(meeting.id());
        var seat = workspace.layout().elements().stream()
                .filter(value -> value.assignable() && value.capacity() == 1)
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
    void workspaceContainsRealisticDemoLayoutAndParticipants() {
        var meeting = meetingRepository
                .findAllByCreatedByIdAndDeletedFalseOrderByUpdatedAtDesc(DEFAULT_USER)
                .getFirst();
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
        var meeting = meetingRepository
                .findAllByCreatedByIdAndDeletedFalseOrderByUpdatedAtDesc(DEFAULT_USER)
                .getFirst();
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
        var meeting = meetingRepository
                .findAllByCreatedByIdAndDeletedFalseOrderByUpdatedAtDesc(DEFAULT_USER)
                .getFirst();
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
        var meeting = meetingRepository
                .findAllByCreatedByIdAndDeletedFalseOrderByUpdatedAtDesc(DEFAULT_USER)
                .getFirst();
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
        var meeting = meetingRepository
                .findAllByCreatedByIdAndDeletedFalseOrderByUpdatedAtDesc(DEFAULT_USER)
                .getFirst();
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
    void publishedVersionFreezesAndRestoresDynamicParticipantStateByEmployeeNumber() throws Exception {
        var meetingId = createImportMeeting();
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
        var original = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, "a12345678")
                .orElseThrow();
        var before = workspaceService.getWorkspace(meetingId);
        var seat = before.layout().elements().stream()
                .filter(value -> value.assignable() && value.capacity() == 1)
                .findFirst()
                .orElseThrow();
        seatingService.assign(
                before.plan().id(),
                new AssignmentRequest(original.getId(), seat.id())
        );
        var version = planVersionService.create(
                before.plan().id(),
                new CreateVersionRequest("动态记录恢复版", "", false)
        );

        var frozen = planVersionService.getSnapshot(before.plan().id(), version.id());
        var frozenPerson = frozen.participants().stream()
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
        var replacement = new ParticipantEntity();
        replacement.setMeetingId(meetingId);
        replacement.setEmployeeNo("A12345678");
        replacement.setName("草稿姓名");
        replacement.setAttendanceStatus(AttendanceStatus.TEMPORARILY_ABSENT);
        participantRepository.saveAndFlush(replacement);
        saveRecord(replacement.getId(), 7, Map.of("批次", "草稿批次"));
        saveRecord(replacement.getId(), 8, Map.of("批次", "残留批次"));

        var draftFields = fieldRepository
                .findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(meetingId);
        draftFields.get(0).setSortOrder(20);
        draftFields.get(1).setSortOrder(10);
        fieldRepository.saveAll(draftFields);
        var staleField = new MeetingParticipantFieldEntity();
        staleField.setMeetingId(meetingId);
        staleField.setFieldName("草稿新增字段");
        staleField.setSortOrder(1);
        fieldRepository.saveAndFlush(staleField);

        var laterParticipant = participantService.create(
                meetingId,
                new CreateParticipantRequest(
                        "87654321",
                        "后来新增人员",
                        Map.of("后来字段", "保留值"),
                        null
                )
        );

        var stillFrozen = planVersionService.getSnapshot(before.plan().id(), version.id());
        var stillFrozenPerson = stillFrozen.participants().stream()
                .filter(value -> value.employeeNo().equals("a12345678"))
                .findFirst()
                .orElseThrow();
        assertThat(stillFrozenPerson.id()).isEqualTo(original.getId());
        assertThat(stillFrozenPerson.name()).isEqualTo("张三");
        assertThat(stillFrozenPerson.records()).hasSize(2);

        planVersionService.restore(before.plan().id(), version.id());

        var restored = workspaceService.getWorkspace(meetingId);
        var restoredPerson = restored.participants().stream()
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
    void publishedVersionReactivatesSoftDeletedParticipantWithOriginalIdentityRecordsAndSeat()
            throws Exception {
        var meetingId = createImportMeeting();
        previewAndCommit(
                meetingId,
                "工号,姓名,批次,奖项名称",
                "a12345678,张三,第二批,优秀项目奖"
        );
        var original = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, "a12345678")
                .orElseThrow();
        var before = workspaceService.getWorkspace(meetingId);
        var seat = before.layout().elements().stream()
                .filter(value -> value.assignable() && value.capacity() == 1)
                .findFirst()
                .orElseThrow();
        seatingService.assign(
                before.plan().id(),
                new AssignmentRequest(original.getId(), seat.id())
        );
        var version = planVersionService.create(
                before.plan().id(),
                new CreateVersionRequest("软删除人员恢复版", "", false)
        );

        participantService.delete(meetingId, original.getId());

        assertThat(participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, "a12345678"))
                .isEmpty();
        assertThat(participantRepository.findById(original.getId()))
                .get()
                .extracting(ParticipantEntity::isDeleted)
                .isEqualTo(true);

        planVersionService.restore(before.plan().id(), version.id());

        var restored = workspaceService.getWorkspace(meetingId);
        var restoredPerson = restored.participants().stream()
                .filter(value -> value.employeeNo().equalsIgnoreCase("a12345678"))
                .findFirst()
                .orElseThrow();
        assertThat(restored.participants().stream()
                .filter(value -> value.employeeNo().equalsIgnoreCase("a12345678")))
                .hasSize(1);
        assertThat(restoredPerson.id()).isEqualTo(original.getId());
        assertThat(restoredPerson.name()).isEqualTo("张三");
        assertThat(restoredPerson.records())
                .extracting(value -> value.attributes().get("批次"))
                .containsExactly("第二批");
        assertThat(restoredPerson.assignedElementId()).isEqualTo(seat.id());
        assertThat(restored.items().stream()
                .filter(value -> original.getId().equals(value.participantId()))
                .flatMap(value -> value.targetElementIds().stream()))
                .containsExactly(seat.id());
    }

    @Test
    void legacyGenericAttributesAreAdaptedToDynamicFieldsForExportAndRestore()
            throws Exception {
        var meetingId = createImportMeeting();
        var participant = participantService.create(
                meetingId,
                new CreateParticipantRequest(
                        "a12345678",
                        "张三",
                        Map.of("部门", "研发部", "备注", "重点"),
                        null
                )
        );
        var before = workspaceService.getWorkspace(meetingId);
        var seat = before.layout().elements().stream()
                .filter(value -> value.assignable() && value.capacity() == 1)
                .findFirst()
                .orElseThrow();
        seatingService.assign(
                before.plan().id(),
                new AssignmentRequest(participant.id(), seat.id())
        );
        var version = planVersionService.create(
                before.plan().id(),
                new CreateVersionRequest("旧快照兼容版", "", false)
        );
        var versionEntity = planVersionRepository.findById(version.id()).orElseThrow();
        var legacySnapshot = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper
                .readTree(versionEntity.getSnapshotJson());
        legacySnapshot.remove("fieldDefinitions");
        legacySnapshot.withArray("participants").forEach(value -> {
            var participantNode = (com.fasterxml.jackson.databind.node.ObjectNode) value;
            participantNode.remove("primaryAttributes");
            participantNode.remove("attributeValues");
            participantNode.remove("records");
            participantNode.putObject("attributes")
                    .put("部门", "研发部")
                    .put("备注", "重点");
        });
        versionEntity.setSnapshotJson(objectMapper.writeValueAsString(legacySnapshot));
        planVersionRepository.saveAndFlush(versionEntity);

        var adaptedSnapshot = planVersionService.getSnapshot(before.plan().id(), version.id());
        assertThat(adaptedSnapshot.fieldDefinitions())
                .extracting(value -> value.label())
                .containsExactly("姓名", "工号", "部门", "备注");
        assertThat(adaptedSnapshot.participants().getFirst().primaryAttributes())
                .containsEntry("部门", "研发部")
                .containsEntry("备注", "重点");
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(
                exportService.exportExcel(meetingId, version.id())
        ))) {
            assertThat(cellValues(workbook.getSheet("人员名单").getRow(0)))
                    .containsExactly("工号", "姓名", "部门", "备注");
            assertThat(cellValues(workbook.getSheet("人员名单").getRow(1)))
                    .containsExactly("a12345678", "张三", "研发部", "重点");
        }
        assertThat(exportService.exportPdf(meetingId, version.id()).length)
                .isGreaterThan(5_000);
        planVersionService.restore(before.plan().id(), version.id());

        assertThat(fieldRepository
                .findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(meetingId))
                .extracting(value -> value.getFieldName())
                .containsExactly("部门", "备注");
        assertThat(recordRepository
                .findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(participant.id()))
                .extracting(value -> readAttributes(value.getAttributesJson()))
                .singleElement()
                .satisfies(attributes -> assertThat(attributes)
                        .containsEntry("部门", "研发部")
                        .containsEntry("备注", "重点"));
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
        mockMvc.perform(get("/meetings/{meetingId}/exports/excel", meeting.id())
                        .header(USER_HEADER, DEFAULT_USER))
                .andExpect(status().isBadRequest());
    }

    @Test
    void genericParticipantTemplateAndExportsAreGenerated() throws Exception {
        var template = mockMvc.perform(get("/imports/template"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(template))) {
            assertThat(workbook.getSheet("参会人员")).isNotNull();
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
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
    void exportedParticipantSheetKeepsDynamicRecordsAndCanBeReimported() throws Exception {
        var meetingId = createImportMeeting();
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
        var workspace = workspaceService.getWorkspace(meetingId);
        var seats = workspace.layout().elements().stream()
                .filter(value -> value.assignable() && value.capacity() == 1)
                .iterator();
        workspace.participants().forEach(participant -> seatingService.assign(
                workspace.plan().id(),
                new AssignmentRequest(participant.id(), seats.next().id())
        ));
        var version = planVersionService.create(
                workspace.plan().id(),
                new CreateVersionRequest("动态记录导出版", "", false)
        );

        var exported = exportService.exportExcel(meetingId, version.id());
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(exported))) {
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("人员名单");
            assertThat(workbook.getSheet("获奖名单")).isNull();
            var sheet = workbook.getSheet("人员名单");
            assertThat(cellValues(sheet.getRow(0)))
                    .containsExactly("工号", "姓名", "批次", "奖项名称");
            var rows = new java.util.ArrayList<java.util.List<String>>();
            for (var rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                var values = cellValues(sheet.getRow(rowIndex));
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
            assertThat(java.util.stream.IntStream.rangeClosed(1, sheet.getLastRowNum())
                    .mapToObj(sheet::getRow)
                    .map(this::cellValues)
                    .filter(values -> values.getFirst().equals("87654321"))
                    .findFirst())
                    .contains(java.util.List.of("87654321", "无动态记录人员", "", ""));
        }

        var importedMeetingId = createImportMeeting();
        var importedPreview = preview(importedMeetingId, exported);
        assertThat(importedPreview.path("errors").isEmpty()).isTrue();
        commit(importedMeetingId, importedPreview.path("token").asText());
        var imported = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(
                        importedMeetingId,
                        "a12345678"
                )
                .orElseThrow();
        assertThat(fieldRepository
                .findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(importedMeetingId))
                .extracting(value -> value.getFieldName())
                .containsExactly("批次", "奖项名称");
        assertThat(recordRepository
                .findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(imported.getId()))
                .extracting(value -> readAttributes(value.getAttributesJson()))
                .containsExactly(
                        Map.of("批次", "第二批", "奖项名称", "优秀项目奖"),
                        Map.of("批次", "第三批", "奖项名称", "创新奖")
                );
    }

    @Test
    void exportedLayoutAndPdfUseOnlyFirstNonBlankDynamicSummary() throws Exception {
        var meetingId = createImportMeeting();
        previewAndCommit(
                meetingId,
                "工号,姓名,空字段,活动角色,批次",
                "12345678,导出人员,,主持人,第一批"
        );
        var workspace = workspaceService.getWorkspace(meetingId);
        var seat = workspace.layout().elements().stream()
                .filter(value -> value.assignable() && value.capacity() == 1)
                .findFirst()
                .orElseThrow();
        var participant = workspace.participants().getFirst();
        seatingService.assign(
                workspace.plan().id(),
                new AssignmentRequest(participant.id(), seat.id())
        );
        var version = planVersionService.create(
                workspace.plan().id(),
                new CreateVersionRequest("排座图文本边界版", "", false)
        );

        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(
                exportService.exportExcel(meetingId, version.id())
        ))) {
            var cellText = workbook.getSheet("排座图")
                    .getRow(seat.row() - 1)
                    .getCell(seat.column() - 1)
                    .getStringCellValue();
            assertThat(cellText).isEqualTo(seat.code() + "\n导出人员\n主持人");
            assertThat(cellText).doesNotContain("第一批");
        }
        try (var document = Loader.loadPDF(exportService.exportPdf(meetingId, version.id()))) {
            var text = new PDFTextStripper().getText(document);
            assertThat(text).contains("主持人");
            assertThat(text).doesNotContain("第一批");
        }
    }

    @Test
    void compatibleParticipantImportsMergeIntoOneRecordAndKeepFieldOrder() throws Exception {
        var meetingId = createImportMeeting();

        var first = previewAndCommit(
                meetingId,
                "工号,姓名,字段1",
                "a12345678,张三,值1"
        );
        var second = previewAndCommit(
                meetingId,
                "工号,姓名,字段1,字段2",
                "a12345678,张三,值1,值2"
        );

        var participant = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, "a12345678")
                .orElseThrow();
        var records = recordRepository
                .findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(participant.getId());
        var fields = fieldRepository.findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(meetingId);

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
        var meetingId = createImportMeeting();
        previewAndCommit(meetingId, "工号,姓名,字段1", "a12345678,张三,初始值");
        previewAndCommit(meetingId, "工号,姓名,字段1", "a12345678,张三,冲突值1");
        var third = previewAndCommit(
                meetingId,
                "工号,姓名,字段1",
                "a12345678,张三,冲突值2"
        );
        var repeated = previewAndCommit(
                meetingId,
                "工号,姓名,字段1",
                "a12345678,张三,冲突值2"
        );

        var participant = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, "a12345678")
                .orElseThrow();
        var records = recordRepository
                .findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(participant.getId());

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
        var meetingId = createImportMeeting();
        previewAndCommit(meetingId, "工号,姓名,已有字段", "a12345678,张三,已有值");

        var preview = preview(
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
        var meetingId = createImportMeeting();
        previewAndCommit(meetingId, "工号,姓名,Department", "a12345678,张三,研发部");

        previewAndCommit(
                meetingId,
                "工号,姓名,department,字段2",
                "a12345678,张三,研发部,值2"
        );

        var participant = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, "a12345678")
                .orElseThrow();
        var record = recordRepository
                .findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(participant.getId())
                .getFirst();
        assertThat(fieldRepository
                .findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(meetingId))
                .extracting(value -> value.getFieldName())
                .containsExactly("Department", "字段2");
        assertThat(readAttributes(record.getAttributesJson()))
                .containsEntry("Department", "研发部")
                .containsEntry("字段2", "值2")
                .doesNotContainKey("department");
    }

    @Test
    void participantNameConflictRejectsCommitWithoutDatabaseWrites() throws Exception {
        var meetingId = createImportMeeting();
        participantService.create(
                meetingId,
                new CreateParticipantRequest(
                        "a12345678", "张三", Map.of(), null
                )
        );
        var preview = preview(meetingId, "工号,姓名,字段1\na12345678,李四,值1");
        var token = preview.path("token").asText();

        assertThat(jsonTextValues(preview.path("errors")))
                .containsExactly("工号a12345678已对应人员张三");
        mockMvc.perform(post(
                        "/meetings/{meetingId}/imports/{token}/commit",
                        meetingId,
                        token
                ).header(USER_HEADER, DEFAULT_USER))
                .andExpect(status().isConflict());

        var participant = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, "a12345678")
                .orElseThrow();
        assertThat(participant.getName()).isEqualTo("张三");
        assertThat(recordRepository
                .findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(participant.getId()))
                .isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void participantNameConflictInsideWorkbookCommitsAsConflictWithoutWrites() throws Exception {
        var meetingId = createImportMeeting();
        var preview = preview(
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

        assertThat(participantRepository.countByMeetingIdAndDeletedFalse(meetingId)).isZero();
        assertThat(fieldRepository.findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(meetingId))
                .isEmpty();
    }

    @Test
    void participantPreviewSimulatesEarlierRowsInTheSameWorkbook() throws Exception {
        var meetingId = createImportMeeting();
        var preview = preview(
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

        var participant = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, "a12345678")
                .orElseThrow();
        var records = recordRepository
                .findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(participant.getId());
        assertThat(records).hasSize(1);
        assertThat(readAttributes(records.getFirst().getAttributesJson()))
                .containsEntry("字段1", "值1")
                .containsEntry("字段2", "值2");
    }

    @Test
    void previewErrorsRejectCommitWithoutDatabaseWrites() throws Exception {
        var meetingId = createImportMeeting();
        var preview = preview(meetingId, "工号,姓名,新字段\n错误工号,张三,值1");

        assertThat(preview.path("errors").isEmpty()).isFalse();
        mockMvc.perform(post(
                        "/meetings/{meetingId}/imports/{token}/commit",
                        meetingId,
                        preview.path("token").asText()
                ).header(USER_HEADER, DEFAULT_USER))
                .andExpect(status().isBadRequest());

        assertThat(participantRepository.countByMeetingIdAndDeletedFalse(meetingId)).isZero();
        assertThat(fieldRepository.findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(meetingId))
                .isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void participantNameConflictRollsBackEarlierWritesInRequestTransaction() throws Exception {
        var meetingId = createImportMeeting();
        participantService.create(
                meetingId,
                new CreateParticipantRequest(
                        "a12345678", "张三", Map.of(), null
                )
        );
        var preview = preview(
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
                .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, "12345678"))
                .isEmpty();
        assertThat(fieldRepository.findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(meetingId))
                .isEmpty();
        var existing = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, "a12345678")
                .orElseThrow();
        assertThat(recordRepository
                .findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(existing.getId()))
                .isEmpty();
    }

    @Test
    void importingAssignedParticipantReusesParticipantId() throws Exception {
        var meetingId = createImportMeeting();
        var participant = participantService.create(
                meetingId,
                new CreateParticipantRequest(
                        "a12345678", "张三", Map.of(), null
                )
        );
        var workspace = workspaceService.getWorkspace(meetingId);
        var seat = workspace.layout().elements().stream()
                .filter(value -> value.assignable() && value.capacity() == 1)
                .findFirst()
                .orElseThrow();
        seatingService.assign(
                workspace.plan().id(),
                new AssignmentRequest(participant.id(), seat.id())
        );

        previewAndCommit(meetingId, "工号,姓名,字段1", "a12345678,张三,值1");

        var imported = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, "a12345678")
                .orElseThrow();
        var refreshedWorkspace = workspaceService.getWorkspace(meetingId);
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
        var meetingId = createImportMeeting();
        var importPreview = preview(
                meetingId,
                "工号,姓名,SharedField,ImportField\n"
                        + "a11111111,并发导入人员,导入共享值,导入字段值"
        );
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> commitImportAfterBarrier(
                    meetingId,
                    importPreview.path("token").asText(),
                    ready,
                    start
            ));
            var second = executor.submit(() -> createParticipantAfterBarrier(
                    meetingId,
                    "87654321",
                    "并发新增人员",
                    """
                            {"sharedfield":"新增共享值","ParticipantField":"新增字段值"}
                            """,
                    ready,
                    start
            ));

            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }

        var fields = fieldRepository
                .findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(meetingId);
        assertThat(fields)
                .extracting(value -> value.getFieldName().toLowerCase(java.util.Locale.ROOT))
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
        var meetingId = createImportMeeting();
        var importPreview = preview(
                meetingId,
                "工号,姓名,SharedField,ImportField\n"
                        + "a22222222,并发导入姓名,导入共享值,导入字段值"
        );
        var bothRequestsAtMeetingLock = new java.util.concurrent.CountDownLatch(2);
        var importHasMeetingLock = new java.util.concurrent.CountDownLatch(1);
        ParticipantFieldRegistrationService registrationTarget =
                org.springframework.test.util.AopTestUtils
                        .getUltimateTargetObject(fieldRegistrationService);
        org.mockito.Mockito.doAnswer(invocation -> {
            var request = ((ServletRequestAttributes) RequestContextHolder
                    .currentRequestAttributes())
                    .getRequest();
            var importRequest = request.getRequestURI().contains("/imports/");
            bothRequestsAtMeetingLock.countDown();
            if (!bothRequestsAtMeetingLock.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("两个请求未同时到达会议锁");
            }
            if (importRequest) {
                var lockedMeeting = invocation.callRealMethod();
                importHasMeetingLock.countDown();
                return lockedMeeting;
            }
            if (!importHasMeetingLock.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("导入请求未先取得会议锁");
            }
            return invocation.callRealMethod();
        }).when(registrationTarget).registerFields(
                org.mockito.ArgumentMatchers.eq(meetingId),
                org.mockito.ArgumentMatchers.any()
        );
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        int importStatus;
        int participantStatus;
        try {
            var importFuture = executor.submit(() -> commitImportAfterBarrier(
                    meetingId,
                    importPreview.path("token").asText(),
                    ready,
                    start
            ));
            var participantFuture = executor.submit(() -> createParticipantAfterBarrier(
                    meetingId,
                    "a22222222",
                    "并发新增姓名",
                    """
                            {"sharedfield":"新增共享值","ParticipantField":"新增字段值"}
                            """,
                    ready,
                    start
            ));

            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            importStatus = importFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
            participantStatus = participantFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }

        assertThat(java.util.List.of(importStatus, participantStatus))
                .containsExactlyInAnyOrder(200, 409);
        var participants = participantRepository
                .findAllByMeetingIdAndDeletedFalseOrderByNameAsc(meetingId)
                .stream()
                .filter(value -> value.getEmployeeNo().equalsIgnoreCase("a22222222"))
                .toList();
        assertThat(participants).hasSize(1);

        var fields = fieldRepository
                .findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(meetingId);
        var normalizedFieldNames = fields.stream()
                .map(value -> value.getFieldName().toLowerCase(java.util.Locale.ROOT))
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

    private String createImportMeeting() {
        var venue = venueService.list().getFirst();
        return meetingService.create(new CreateMeetingRequest(
                "人员导入测试-" + java.util.UUID.randomUUID(),
                venue.id()
        )).id();
    }

    private int commitImportAfterBarrier(
            String meetingId,
            String token,
            java.util.concurrent.CountDownLatch ready,
            java.util.concurrent.CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
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
            java.util.concurrent.CountDownLatch ready,
            java.util.concurrent.CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
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
        var venueId = venueService.list().getFirst().id();
        var result = mockMvc.perform(post("/meetings")
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
        var result = mockMvc.perform(post("/meetings/{meetingId}/participants", meetingId)
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
        var file = new MockMultipartFile(
                "file",
                "participants.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );
        var result = mockMvc.perform(multipart(
                        "/meetings/{meetingId}/imports/preview",
                        meetingId
                ).file(file).header(USER_HEADER, userId))
                .andExpect(status().isOk())
                .andReturn();
        return responseJson(result);
    }

    private JsonNode responseJson(org.springframework.test.web.servlet.MvcResult result) {
        try {
            return objectMapper.readTree(result.getResponse().getContentAsByteArray());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法解析测试响应", exception);
        }
    }

    private JsonNode workspaceAs(String userId, String meetingId) throws Exception {
        var result = mockMvc.perform(get("/meetings/{meetingId}/workspace", meetingId)
                        .header(USER_HEADER, userId))
                .andExpect(status().isOk())
                .andReturn();
        return responseJson(result);
    }

    private String assignableSeatId(JsonNode workspace) {
        return java.util.stream.StreamSupport.stream(
                        workspace.path("layout").path("elements").spliterator(),
                        false
                )
                .filter(element -> element.path("assignable").asBoolean()
                        && element.path("capacity").asInt() == 1)
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
        var participant = java.util.stream.StreamSupport.stream(
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
        var preview = preview(meetingId, header + "\n" + row);
        return commit(meetingId, preview.path("token").asText());
    }

    private JsonNode preview(String meetingId, String csv) throws Exception {
        return preview(meetingId, workbook(csv));
    }

    private JsonNode preview(String meetingId, byte[] content) throws Exception {
        var file = new MockMultipartFile(
                "file",
                "participants.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );
        var result = mockMvc.perform(multipart(
                        "/meetings/{meetingId}/imports/preview",
                        meetingId
                ).file(file).header(USER_HEADER, DEFAULT_USER))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode commit(String meetingId, String token) throws Exception {
        var result = mockMvc.perform(post(
                        "/meetings/{meetingId}/imports/{token}/commit",
                        meetingId,
                        token
                ).header(USER_HEADER, DEFAULT_USER))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private byte[] workbook(String csv) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("参会人员");
            var lines = csv.split("\\R");
            for (var rowIndex = 0; rowIndex < lines.length; rowIndex++) {
                var row = sheet.createRow(rowIndex);
                var values = lines[rowIndex].split(",", -1);
                for (var columnIndex = 0; columnIndex < values.length; columnIndex++) {
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
        var record = new ParticipantRecordEntity();
        record.setParticipantId(participantId);
        record.setRecordOrder(recordOrder);
        record.setAttributesJson(objectMapper.writeValueAsString(attributes));
        recordRepository.saveAndFlush(record);
    }

    private java.util.List<String> cellValues(org.apache.poi.ss.usermodel.Row row) {
        var values = new java.util.ArrayList<String>();
        for (var column = 0; column < row.getLastCellNum(); column++) {
            values.add(row.getCell(column) == null ? "" : row.getCell(column).toString());
        }
        return values;
    }

    private java.util.List<String> jsonTextValues(JsonNode values) {
        var result = new java.util.ArrayList<String>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    @RestController
    static class OptimisticLockTestController {
        @GetMapping("/test/optimistic-lock")
        void failWithOptimisticLock() {
            throw new ObjectOptimisticLockingFailureException("Meeting", "test-id");
        }
    }
}
