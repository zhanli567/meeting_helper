package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.meetinghelper.common.context.CurrentUserHolder;
import com.company.meetinghelper.common.security.CurrentUser;
import com.company.meetinghelper.export.api.dto.request.ExportExcelRequest;
import com.company.meetinghelper.export.service.ExportService;
import com.company.meetinghelper.meeting.api.dto.request.CreateMeetingRequest;
import com.company.meetinghelper.meeting.api.dto.response.MeetingSummary;
import com.company.meetinghelper.meeting.service.MeetingService;
import com.company.meetinghelper.participant.api.dto.request.CreateParticipantRequest;
import com.company.meetinghelper.participant.service.ParticipantService;
import com.company.meetinghelper.seating.api.dto.request.AssignmentRequest;
import com.company.meetinghelper.seating.service.SeatingService;
import com.company.meetinghelper.support.PostgreSqlTestDatabaseInitializer;
import com.company.meetinghelper.venue.api.dto.ElementInput;
import com.company.meetinghelper.venue.api.dto.request.CreateVenueRequest;
import com.company.meetinghelper.venue.api.dto.response.VenueDetail;
import com.company.meetinghelper.venue.service.VenueService;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse.ElementView;
import com.company.meetinghelper.workspace.service.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ContextConfiguration(initializers = PostgreSqlTestDatabaseInitializer.class)
class ExportExcelStyleIntegrationTests {
    private static final String DEFAULT_USER = "demo-secretary";

    @Autowired
    private VenueService venueService;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private ParticipantService participantService;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private SeatingService seatingService;

    @Autowired
    private ExportService exportService;

    @BeforeEach
    void bindDefaultUserToDirectServiceCalls() {
        CurrentUserHolder.set(new CurrentUser(DEFAULT_USER, DEFAULT_USER, Set.of()));
    }

    @AfterEach
    void clearDirectServiceUser() {
        CurrentUserHolder.clear();
    }

    @Test
    void exportedParticipantAndSeatDetailKeyColumnsAreCentered() throws Exception {
        MeetingSummary meeting = createAssignedParticipantMeeting();

        byte[] exported = exportService.exportExcel(meeting.id(), new ExportExcelRequest(
                null,
                new ExportExcelRequest.SheetSelection(
                        new ExportExcelRequest.ParticipantSheet(true, List.of("部门"), true, true),
                        new ExportExcelRequest.LayoutSheet(false, List.of(), List.of()),
                        new ExportExcelRequest.SeatDetailSheet(true, List.of("部门"), true, true, true)
                )
        ));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            XSSFSheet participantSheet = workbook.getSheet("人员名单");
            assertCellsCentered(participantSheet.getRow(0), 0, 4);
            assertCellsCentered(participantSheet.getRow(1), 0, 1);

            XSSFSheet seatDetailSheet = workbook.getSheet("座位明细");
            assertCellsCentered(seatDetailSheet.getRow(0), 0, 5);
            assertCellsCentered(seatDetailSheet.getRow(1), 3, 4);
        }
    }

    private MeetingSummary createAssignedParticipantMeeting() {
        VenueDetail venue = venueService.create(new CreateVenueRequest(
                "导出样式场馆-" + UUID.randomUUID(),
                "主校区",
                "",
                "",
                1,
                "",
                "",
                "",
                "",
                "",
                "",
                5,
                5,
                List.of(seat())
        ));
        MeetingSummary meeting = meetingService.create(new CreateMeetingRequest(
                "导出样式会议-" + UUID.randomUUID(),
                venue.id()
        ));
        participantService.create(
                meeting.id(),
                new CreateParticipantRequest("a12345678", "张三", Map.of("部门", "研发部"), null)
        );
        WorkspaceResponse workspace = workspaceService.getWorkspace(meeting.id());
        ElementView seat = workspace.layout().elements().getFirst();
        seatingService.assign(
                workspace.plan().id(),
                new AssignmentRequest(workspace.participants().getFirst().id(), seat.id())
        );
        return meeting;
    }

    private ElementInput seat() {
        return new ElementInput("SEAT", "座位", 1, 1, 1, 1, "#ffffff");
    }

    private void assertCellsCentered(Row row, int firstColumn, int lastColumn) {
        for (int column = firstColumn; column <= lastColumn; column++) {
            assertThat(row.getCell(column).getCellStyle().getAlignment())
                    .isEqualTo(HorizontalAlignment.CENTER);
        }
    }
}
