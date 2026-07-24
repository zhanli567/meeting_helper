package com.company.meetinghelper.meeting.api;

import com.company.meetinghelper.meeting.api.dto.request.CreateMeetingRequest;
import com.company.meetinghelper.meeting.api.dto.response.MeetingSummary;
import com.company.meetinghelper.meeting.service.MeetingService;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import com.company.meetinghelper.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {
    private final MeetingService meetingService;
    private final WorkspaceService workspaceService;

    public MeetingController(MeetingService meetingService, WorkspaceService workspaceService) {
        this.meetingService = meetingService;
        this.workspaceService = workspaceService;
    }

    @GetMapping
    List<MeetingSummary> list() {
        return meetingService.list();
    }

    @PostMapping
    MeetingSummary create(@Valid @RequestBody CreateMeetingRequest request) {
        return meetingService.create(request);
    }

    @GetMapping("/{meetingId}/workspace")
    WorkspaceResponse workspace(@PathVariable String meetingId) {
        return workspaceService.getWorkspace(meetingId);
    }
}
