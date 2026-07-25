package com.company.meetinghelper.participant.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.participant.api.dto.request.CreateParticipantRequest;
import com.company.meetinghelper.participant.api.dto.request.UpdateAttendanceRequest;
import com.company.meetinghelper.participant.api.dto.response.ParticipantResult;
import com.company.meetinghelper.participant.entity.AttendanceStatus;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import com.company.meetinghelper.seating.api.dto.request.AssignmentRequest;
import com.company.meetinghelper.seating.repository.PlanItemRepository;
import com.company.meetinghelper.seating.repository.PlanItemTargetRepository;
import com.company.meetinghelper.seating.entity.PlanItemType;
import com.company.meetinghelper.seating.repository.SeatingPlanRepository;
import com.company.meetinghelper.seating.service.SeatingService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
public class ParticipantService {
    private final MeetingRepository meetingRepository;
    private final ParticipantRepository participantRepository;
    private final SeatingPlanRepository planRepository;
    private final PlanItemRepository itemRepository;
    private final PlanItemTargetRepository targetRepository;
    private final SeatingService seatingService;
    private final ObjectMapper objectMapper;

    /**
     * 创建参会人员服务。
     *
     * @param meetingRepository 会议仓储
     * @param participantRepository 参会人员仓储
     * @param planRepository 排座方案仓储
     * @param itemRepository 排座明细仓储
     * @param targetRepository 排座目标仓储
     * @param seatingService 排座服务
     * @param objectMapper JSON序列化器
     */
    public ParticipantService(
            MeetingRepository meetingRepository,
            ParticipantRepository participantRepository,
            SeatingPlanRepository planRepository,
            PlanItemRepository itemRepository,
            PlanItemTargetRepository targetRepository,
            SeatingService seatingService,
            ObjectMapper objectMapper
    ) {
        this.meetingRepository = meetingRepository;
        this.participantRepository = participantRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
        this.seatingService = seatingService;
        this.objectMapper = objectMapper;
    }

    /**
     * 向指定会议添加一名参会人员。
     *
     * @param meetingId 会议ID
     * @param request 人员创建请求
     * @return 新增人员信息
     */
    @Transactional
    public ParticipantResult create(String meetingId, CreateParticipantRequest request) {
        meetingRepository.findById(meetingId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会议不存在"));
        var employeeNo = request.employeeNo().trim();
        if (participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, employeeNo)
                .isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "该工号已在会议名单中");
        }
        var participant = new ParticipantEntity();
        participant.setMeetingId(meetingId);
        participant.setEmployeeNo(employeeNo);
        participant.setName(request.name());
        participant.setLevelValue(request.level());
        participant.setDepartment(request.department());
        participant.setParticipantType(request.participantType());
        participant.setTags(request.tags());
        try {
            participant.setCustomAttributesJson(objectMapper.writeValueAsString(
                    request.attributes() == null ? Map.of() : request.attributes()));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "扩展属性格式不正确");
        }
        try {
            participantRepository.saveAndFlush(participant);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "该工号已在会议名单中");
        }
        if (request.targetElementId() != null && !request.targetElementId().isBlank()) {
            var plan = planRepository.findFirstByMeetingIdAndDeletedFalseOrderByCreatedAtAsc(meetingId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "排座方案不存在"));
            seatingService.assign(
                    plan.getId(),
                    new AssignmentRequest(participant.getId(), request.targetElementId())
            );
        }
        return new ParticipantResult(participant.getId(), participant.getEmployeeNo(), participant.getName());
    }

    /**
     * 更新参会人员的出席状态；标记为临时不出席时同步释放其座位。
     *
     * @param meetingId 会议ID
     * @param participantId 参会人员ID
     * @param request 出席状态请求
     */
    @Transactional
    public void updateAttendance(
            String meetingId,
            String participantId,
            UpdateAttendanceRequest request
    ) {
        var participant = participantRepository.findById(participantId)
                .filter(value -> !value.isDeleted() && value.getMeetingId().equals(meetingId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "人员不存在"));
        participant.setAttendanceStatus(request.attendanceStatus());
        if (request.attendanceStatus() == AttendanceStatus.TEMPORARILY_ABSENT) {
            removeAssignment(meetingId, participantId);
            participant.setLocked(false);
        }
        participantRepository.save(participant);
    }

    /**
     * 从会议名单中删除人员及其排座关系。
     *
     * @param meetingId 会议ID
     * @param participantId 参会人员ID
     */
    @Transactional
    public void delete(String meetingId, String participantId) {
        var participant = participantRepository.findById(participantId)
                .filter(value -> !value.isDeleted() && value.getMeetingId().equals(meetingId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "人员不存在"));
        removeAssignment(meetingId, participantId);
        participant.setDeleted(true);
        participantRepository.save(participant);
    }

    private void removeAssignment(String meetingId, String participantId) {
        var plan = planRepository.findFirstByMeetingIdAndDeletedFalseOrderByCreatedAtAsc(meetingId).orElse(null);
        if (plan == null) {
            return;
        }
        itemRepository.findByPlanIdAndParticipantIdAndItemTypeAndDeletedFalse(
                plan.getId(), participantId, PlanItemType.PERSON).ifPresent(item -> {
            targetRepository.deleteAllByPlanItemId(item.getId());
            itemRepository.delete(item);
        });
    }

}
