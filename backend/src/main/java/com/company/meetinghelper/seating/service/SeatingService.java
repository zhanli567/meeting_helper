package com.company.meetinghelper.seating.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.meeting.repository.MeetingElementRepository;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import com.company.meetinghelper.seating.api.dto.request.AssignmentRequest;
import com.company.meetinghelper.seating.entity.PlanItemEntity;
import com.company.meetinghelper.seating.entity.PlanItemTargetEntity;
import com.company.meetinghelper.seating.entity.PlanItemType;
import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import com.company.meetinghelper.seating.repository.PlanItemRepository;
import com.company.meetinghelper.seating.repository.PlanItemTargetRepository;
import com.company.meetinghelper.seating.repository.SeatingPlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatingService {
    private final SeatingPlanRepository planRepository;
    private final PlanItemRepository itemRepository;
    private final PlanItemTargetRepository targetRepository;
    private final ParticipantRepository participantRepository;
    private final MeetingElementRepository elementRepository;

    public SeatingService(
            SeatingPlanRepository planRepository,
            PlanItemRepository itemRepository,
            PlanItemTargetRepository targetRepository,
            ParticipantRepository participantRepository,
            MeetingElementRepository elementRepository
    ) {
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
        this.participantRepository = participantRepository;
        this.elementRepository = elementRepository;
    }

    @Transactional
    public void assign(String planId, AssignmentRequest request) {
        var plan = requirePlan(planId);
        var participant = participantRepository.findById(request.participantId())
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "人员不存在"));
        if (!participant.getMeetingId().equals(plan.getMeetingId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "人员不属于当前会议");
        }
        if (participant.isLocked()) {
            throw new ApiException(HttpStatus.CONFLICT, "该人员已锁定，无法移动");
        }
        var targetElement = elementRepository.findById(request.targetElementId())
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "目标座位不存在"));
        if (!targetElement.getMeetingId().equals(plan.getMeetingId())
                || !targetElement.isAssignable()
                || targetElement.getCapacity() != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "目标位置不是可用的单人座席");
        }

        var currentItem = itemRepository.findByPlanIdAndParticipantIdAndItemTypeAndDeletedFalse(
                planId, participant.getId(), PlanItemType.PERSON).orElse(null);
        var currentTarget = currentItem == null
                ? null
                : targetRepository.findAllByPlanItemIdInAndDeletedFalse(java.util.List.of(currentItem.getId()))
                .stream().findFirst().orElse(null);
        if (currentItem != null && currentItem.isLocked()) {
            throw new ApiException(HttpStatus.CONFLICT, "当前座位已锁定，无法移动");
        }
        if (currentTarget != null && currentTarget.getMeetingElementId().equals(targetElement.getId())) {
            return;
        }

        var occupiedTarget = targetRepository.findByMeetingElementIdAndDeletedFalse(targetElement.getId()).orElse(null);
        if (occupiedTarget != null) {
            var occupiedItem = itemRepository.findById(occupiedTarget.getPlanItemId())
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "座位占用数据不完整"));
            if (occupiedItem.isLocked()) {
                throw new ApiException(HttpStatus.CONFLICT, "目标座位已锁定");
            }
            if (occupiedItem.getItemType() != PlanItemType.PERSON) {
                throw new ApiException(HttpStatus.CONFLICT, "目标座位已被设备、预留或禁用状态占用");
            }
            if (currentTarget == null) {
                throw new ApiException(HttpStatus.CONFLICT, "待排人员只能拖入空座位");
            }
            occupiedTarget.setMeetingElementId(currentTarget.getMeetingElementId());
            currentTarget.setMeetingElementId(targetElement.getId());
            targetRepository.save(occupiedTarget);
            targetRepository.save(currentTarget);
            touch(plan);
            return;
        }

        if (currentItem == null) {
            currentItem = new PlanItemEntity();
            currentItem.setPlanId(planId);
            currentItem.setItemType(PlanItemType.PERSON);
            currentItem.setParticipantId(participant.getId());
            currentItem.setLabel(participant.getName());
            itemRepository.save(currentItem);
        }
        if (currentTarget == null) {
            currentTarget = new PlanItemTargetEntity();
            currentTarget.setPlanItemId(currentItem.getId());
        }
        currentTarget.setMeetingElementId(targetElement.getId());
        targetRepository.save(currentTarget);
        touch(plan);
    }

    @Transactional
    public void unassign(String planId, String participantId) {
        var plan = requirePlan(planId);
        var item = itemRepository.findByPlanIdAndParticipantIdAndItemTypeAndDeletedFalse(
                planId, participantId, PlanItemType.PERSON).orElse(null);
        if (item == null) {
            return;
        }
        if (item.isLocked()) {
            throw new ApiException(HttpStatus.CONFLICT, "该座位已锁定");
        }
        targetRepository.deleteAllByPlanItemId(item.getId());
        itemRepository.delete(item);
        touch(plan);
    }

    @Transactional
    public void setLocked(String planId, String participantId, boolean locked) {
        var plan = requirePlan(planId);
        var item = itemRepository.findByPlanIdAndParticipantIdAndItemTypeAndDeletedFalse(
                        planId, participantId, PlanItemType.PERSON)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "人员尚未排座"));
        item.setLocked(locked);
        itemRepository.save(item);
        touch(plan);
    }

    private SeatingPlanEntity requirePlan(String planId) {
        return planRepository.findById(planId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "排座方案不存在"));
    }

    private void touch(SeatingPlanEntity plan) {
        plan.setUpdatedById("demo-secretary");
        plan.setUpdatedByName("演示秘书");
        planRepository.save(plan);
    }

}
