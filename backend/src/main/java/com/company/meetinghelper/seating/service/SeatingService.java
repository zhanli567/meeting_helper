package com.company.meetinghelper.seating.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.meeting.repository.MeetingElementRepository;
import com.company.meetinghelper.meeting.service.MeetingAccessService;
import com.company.meetinghelper.participant.entity.AttendanceStatus;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import com.company.meetinghelper.seating.api.dto.request.AssignmentRequest;
import com.company.meetinghelper.seating.api.dto.request.SaveAssignmentsRequest;
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

import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SeatingService {
    private final SeatingPlanRepository planRepository;
    private final PlanItemRepository itemRepository;
    private final PlanItemTargetRepository targetRepository;
    private final ParticipantRepository participantRepository;
    private final MeetingElementRepository elementRepository;
    private final MeetingAccessService meetingAccessService;

    /**
     * 创建排座服务。
     *
     * @param planRepository 排座方案仓储
     * @param itemRepository 排座明细仓储
     * @param targetRepository 排座目标仓储
     * @param participantRepository 参会人员仓储
     * @param elementRepository 会议元素仓储
     * @param meetingAccessService 会议归属校验服务
     */
    public SeatingService(
            SeatingPlanRepository planRepository,
            PlanItemRepository itemRepository,
            PlanItemTargetRepository targetRepository,
            ParticipantRepository participantRepository,
            MeetingElementRepository elementRepository,
            MeetingAccessService meetingAccessService
    ) {
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
        this.participantRepository = participantRepository;
        this.elementRepository = elementRepository;
        this.meetingAccessService = meetingAccessService;
    }

    /**
     * 将一名人员安排到目标座位；双方均已落座时交换座位。
     *
     * @param planId 排座方案ID
     * @param request 单次排座请求
     */
    @Transactional
    public void assign(String planId, AssignmentRequest request) {
        var plan = requirePlan(planId);
        var participant = participantRepository.findByIdAndMeetingIdAndDeletedFalse(
                        request.participantId(),
                        plan.getMeetingId()
                )
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "人员不存在"));
        if (participant.getAttendanceStatus() == AttendanceStatus.TEMPORARILY_ABSENT) {
            throw new ApiException(HttpStatus.CONFLICT, "临时不出席人员不能安排座位");
        }
        var targetElement = elementRepository.findByIdAndMeetingIdAndDeletedFalse(
                        request.targetElementId(),
                        plan.getMeetingId()
                )
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "目标座位不存在"));
        if (!targetElement.isAssignable()
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
            var currentElementId = currentTarget.getMeetingElementId();
            var occupiedItemId = occupiedTarget.getPlanItemId();
            var currentItemId = currentTarget.getPlanItemId();
            targetRepository.deleteAll(List.of(occupiedTarget, currentTarget));
            targetRepository.flush();
            targetRepository.save(createTarget(occupiedItemId, currentElementId));
            targetRepository.save(createTarget(currentItemId, targetElement.getId()));
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

    /**
     * 使用前端提交的完整人员座位关系替换当前草稿中的可编辑排座。
     *
     * @param planId 排座方案ID
     * @param request 完整人员座位关系
     */
    @Transactional
    public void replaceAssignments(String planId, SaveAssignmentsRequest request) {
        var plan = requirePlan(planId);
        var participants = participantRepository
                .findAllByMeetingIdAndDeletedFalseOrderByNameAsc(plan.getMeetingId())
                .stream()
                .collect(Collectors.toMap(value -> value.getId(), Function.identity()));
        var elements = elementRepository
                .findAllByMeetingIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(plan.getMeetingId())
                .stream()
                .collect(Collectors.toMap(value -> value.getId(), Function.identity()));

        var participantIds = new HashSet<String>();
        var targetElementIds = new HashSet<String>();
        for (var assignment : request.assignments()) {
            if (!participantIds.add(assignment.participantId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "同一人员不能同时安排到多个座位");
            }
            if (!targetElementIds.add(assignment.targetElementId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "同一座位不能同时安排多名人员");
            }
            var participant = participants.get(assignment.participantId());
            if (participant == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "人员不存在");
            }
            if (participant.getAttendanceStatus() == AttendanceStatus.TEMPORARILY_ABSENT) {
                throw new ApiException(HttpStatus.CONFLICT, "临时不出席人员不能安排座位");
            }
            var element = elements.get(assignment.targetElementId());
            if (element == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "目标座位不存在");
            }
            if (!element.isAssignable() || element.getCapacity() != 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "提交的目标位置不是可用的单人座席");
            }
        }

        var currentItems = itemRepository.findAllByPlanIdAndDeletedFalseOrderByCreatedAtAsc(planId);
        var itemIds = currentItems.stream().map(PlanItemEntity::getId).toList();
        var currentTargets = itemIds.isEmpty()
                ? List.<PlanItemTargetEntity>of()
                : targetRepository.findAllByPlanItemIdInAndDeletedFalse(itemIds);
        var targetByItemId = currentTargets.stream().collect(Collectors.toMap(
                PlanItemTargetEntity::getPlanItemId,
                Function.identity(),
                (left, right) -> left
        ));
        var requestedTargetByParticipant = request.assignments().stream().collect(Collectors.toMap(
                value -> value.participantId(),
                value -> value.targetElementId()
        ));

        var reservedTargetIds = new HashSet<String>();
        var editableItems = new java.util.ArrayList<PlanItemEntity>();
        var lockedParticipantIds = new HashSet<String>();
        for (var item : currentItems) {
            var target = targetByItemId.get(item.getId());
            if (item.getItemType() != PlanItemType.PERSON) {
                if (target != null) {
                    reservedTargetIds.add(target.getMeetingElementId());
                }
                continue;
            }
            if (item.isLocked()) {
                var requestedTarget = requestedTargetByParticipant.get(item.getParticipantId());
                if (target == null || !target.getMeetingElementId().equals(requestedTarget)) {
                    throw new ApiException(HttpStatus.CONFLICT, "已锁定人员的座位不能修改或移除");
                }
                lockedParticipantIds.add(item.getParticipantId());
            } else {
                editableItems.add(item);
            }
        }
        if (targetElementIds.stream().anyMatch(reservedTargetIds::contains)) {
            throw new ApiException(HttpStatus.CONFLICT, "目标座位已被设备、预留或禁用状态占用");
        }

        var editableItemIds = editableItems.stream().map(PlanItemEntity::getId).collect(Collectors.toSet());
        var editableTargets = currentTargets.stream()
                .filter(target -> editableItemIds.contains(target.getPlanItemId()))
                .toList();
        if (!editableTargets.isEmpty()) {
            targetRepository.deleteAll(editableTargets);
            targetRepository.flush();
        }
        if (!editableItems.isEmpty()) {
            itemRepository.deleteAll(editableItems);
            itemRepository.flush();
        }

        for (var assignment : request.assignments()) {
            if (lockedParticipantIds.contains(assignment.participantId())) {
                continue;
            }
            var participant = participants.get(assignment.participantId());
            var item = new PlanItemEntity();
            item.setPlanId(planId);
            item.setItemType(PlanItemType.PERSON);
            item.setParticipantId(participant.getId());
            item.setLabel(participant.getName());
            itemRepository.save(item);
            targetRepository.save(createTarget(item.getId(), assignment.targetElementId()));
        }
        touch(plan);
    }

    /**
     * 将指定人员从当前座位移回待排列表。
     *
     * @param planId 排座方案ID
     * @param participantId 参会人员ID
     */
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

    /**
     * 设置人员座位的锁定状态。
     *
     * @param planId 排座方案ID
     * @param participantId 参会人员ID
     * @param locked 是否锁定
     */
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

    private PlanItemTargetEntity createTarget(String planItemId, String meetingElementId) {
        var target = new PlanItemTargetEntity();
        target.setPlanItemId(planItemId);
        target.setMeetingElementId(meetingElementId);
        return target;
    }

    private SeatingPlanEntity requirePlan(String planId) {
        return meetingAccessService.requireOwnedPlan(planId);
    }

    private void touch(SeatingPlanEntity plan) {
        plan.setUpdatedById("demo-secretary");
        plan.setUpdatedByName("演示秘书");
        planRepository.save(plan);
    }

}
