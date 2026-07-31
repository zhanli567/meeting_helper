package com.company.meetinghelper.participant.api;

import com.company.meetinghelper.common.api.ApiResponse;
import com.company.meetinghelper.participant.api.dto.request.CreateParticipantRequest;
import com.company.meetinghelper.participant.api.dto.request.UpdateAttendanceRequest;
import com.company.meetinghelper.participant.api.dto.request.UpdateParticipantRequest;
import com.company.meetinghelper.participant.api.dto.response.ParticipantResult;
import com.company.meetinghelper.participant.service.ParticipantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ParticipantController 类。
 */
@RestController
@RequestMapping("/meetings/{meetingId}/participants")
public class ParticipantController {
    private final ParticipantService participantService;

    /**
     * 创建参会人员接口控制器。
     *
     * @param participantService 参会人员服务
     */
    public ParticipantController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    /**
     * 向会议添加一名参会人员。
     *
     * @param meetingId 会议ID
     * @param request 人员创建请求
     * @return 新增人员信息
     */
    @PostMapping
    public ParticipantResult create(
            @PathVariable String meetingId,
            @Valid @RequestBody CreateParticipantRequest request
    ) {
        return participantService.create(meetingId, request);
    }

    /**
     * 更新参会人员姓名和动态字段记录。
     *
     * @param meetingId 会议ID
     * @param participantId 参会人员ID
     * @param request 人员更新请求
     * @return 更新后的人员基础信息
     */
    @PostMapping("/{participantId}/update")
    public ParticipantResult update(
            @PathVariable String meetingId,
            @PathVariable String participantId,
            @Valid @RequestBody UpdateParticipantRequest request
    ) {
        return participantService.update(meetingId, participantId, request);
    }

    /**
     * 更新参会人员的临时出席状态。
     *
     * @param meetingId 会议ID
     * @param participantId 参会人员ID
     * @param request 出席状态请求
     * @return 空响应
     */
    @PostMapping("/{participantId}/attendance")
    public ApiResponse<Void> updateAttendance(
            @PathVariable String meetingId,
            @PathVariable String participantId,
            @Valid @RequestBody UpdateAttendanceRequest request
    ) {
        participantService.updateAttendance(meetingId, participantId, request);
        return ApiResponse.success(null);
    }

    /**
     * 从会议名单中删除人员。
     *
     * @param meetingId 会议ID
     * @param participantId 参会人员ID
     * @return 空响应
     */
    @PostMapping("/{participantId}/delete")
    public ApiResponse<Void> delete(@PathVariable String meetingId, @PathVariable String participantId) {
        participantService.delete(meetingId, participantId);
        return ApiResponse.success(null);
    }
}
