package com.company.meetinghelper.meeting.api;

import com.company.meetinghelper.meeting.api.dto.request.CreateMeetingRequest;
import com.company.meetinghelper.meeting.api.dto.request.UpdateMeetingNameRequest;
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
@RequestMapping("/meetings")
public class MeetingController {
    private final MeetingService meetingService;
    private final WorkspaceService workspaceService;

    /**
     * 创建会议接口控制器。
     *
     * @param meetingService 会议服务
     * @param workspaceService 工作区服务
     */
    public MeetingController(MeetingService meetingService, WorkspaceService workspaceService) {
        this.meetingService = meetingService;
        this.workspaceService = workspaceService;
    }

    /**
     * 查询当前用户可访问的会议。
     *
     * @return 会议列表
     */
    @GetMapping
    public List<MeetingSummary> list() {
        return meetingService.list();
    }

    /**
     * 创建会议及其独立场馆快照。
     *
     * @param request 会议创建请求
     * @return 新建会议信息
     */
    @PostMapping("/create-from-venue")
    public MeetingSummary createFromVenue(@Valid @RequestBody CreateMeetingRequest request) {
        return meetingService.create(request);
    }

    /**
     * 更新会议名称。
     *
     * @param meetingId 会议ID
     * @param request 会议名称更新请求
     * @return 更新后的会议信息
     */
    @PostMapping("/{meetingId}/name/update")
    public MeetingSummary updateName(
            @PathVariable String meetingId,
            @Valid @RequestBody UpdateMeetingNameRequest request
    ) {
        return meetingService.updateName(meetingId, request);
    }

    /**
     * 查询会议当前草稿工作区。
     *
     * @param meetingId 会议ID
     * @return 工作区数据
     */
    @GetMapping("/{meetingId}/workspace")
    public WorkspaceResponse workspace(@PathVariable String meetingId) {
        return workspaceService.getWorkspace(meetingId);
    }
}
