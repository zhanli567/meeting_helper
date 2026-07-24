package com.company.meetinghelper.participant.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.participant.api.dto.request.CreateParticipantRequest;
import com.company.meetinghelper.participant.api.dto.response.ParticipantResult;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import com.company.meetinghelper.seating.repository.PlanItemRepository;
import com.company.meetinghelper.seating.repository.PlanItemTargetRepository;
import com.company.meetinghelper.seating.entity.PlanItemType;
import com.company.meetinghelper.seating.repository.SeatingPlanRepository;
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
    private final ObjectMapper objectMapper;

    public ParticipantService(
            MeetingRepository meetingRepository,
            ParticipantRepository participantRepository,
            SeatingPlanRepository planRepository,
            PlanItemRepository itemRepository,
            PlanItemTargetRepository targetRepository,
            ObjectMapper objectMapper
    ) {
        this.meetingRepository = meetingRepository;
        this.participantRepository = participantRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ParticipantResult create(String meetingId, CreateParticipantRequest request) {
        meetingRepository.findById(meetingId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会议不存在"));
        if (participantRepository.findByMeetingIdAndEmployeeNoAndDeletedFalse(meetingId, request.employeeNo()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "该工号已在会议名单中");
        }
        var participant = new ParticipantEntity();
        participant.setMeetingId(meetingId);
        participant.setEmployeeNo(request.employeeNo().toUpperCase());
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
        participantRepository.save(participant);
        return new ParticipantResult(participant.getId(), participant.getEmployeeNo(), participant.getName());
    }

    @Transactional
    public void delete(String meetingId, String participantId) {
        var participant = participantRepository.findById(participantId)
                .filter(value -> !value.isDeleted() && value.getMeetingId().equals(meetingId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "人员不存在"));
        var plan = planRepository.findFirstByMeetingIdAndDeletedFalseOrderByCreatedAtAsc(meetingId).orElse(null);
        if (plan != null) {
            itemRepository.findByPlanIdAndParticipantIdAndItemTypeAndDeletedFalse(
                    plan.getId(), participantId, PlanItemType.PERSON).ifPresent(item -> {
                targetRepository.deleteAllByPlanItemId(item.getId());
                itemRepository.delete(item);
            });
        }
        participant.setDeleted(true);
        participantRepository.save(participant);
    }

}
