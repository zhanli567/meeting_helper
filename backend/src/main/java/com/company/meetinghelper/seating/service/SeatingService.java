package com.company.meetinghelper.seating.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.meeting.entity.MeetingElementEntity;
import com.company.meetinghelper.meeting.repository.MeetingElementRepository;
import com.company.meetinghelper.meeting.service.MeetingAccessService;
import com.company.meetinghelper.participant.entity.AttendanceStatus;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import com.company.meetinghelper.seating.api.dto.request.AssignmentInput;
import com.company.meetinghelper.seating.api.dto.request.AssignmentRequest;
import com.company.meetinghelper.seating.api.dto.request.ReservedAreaInput;
import com.company.meetinghelper.seating.api.dto.request.SaveAssignmentsRequest;
import com.company.meetinghelper.seating.api.dto.request.SaveReservedAreasRequest;
import com.company.meetinghelper.seating.entity.PlanItemEntity;
import com.company.meetinghelper.seating.entity.PlanItemTargetEntity;
import com.company.meetinghelper.seating.entity.PlanItemType;
import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import com.company.meetinghelper.seating.repository.PlanItemRepository;
import com.company.meetinghelper.seating.repository.PlanItemTargetRepository;
import com.company.meetinghelper.seating.repository.SeatingPlanRepository;
import com.company.meetinghelper.venue.entity.ElementKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        SeatingPlanEntity plan = requirePlan(planId);
        ParticipantEntity participant = participantRepository.findByIdAndMeetingId(
                        request.participantId(),
                        plan.getMeetingId()
                )
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "人员不存在"));
        if (participant.getAttendanceStatus() == AttendanceStatus.TEMPORARILY_ABSENT) {
            throw new ApiException(HttpStatus.CONFLICT, "临时不出席人员不能安排座位");
        }
        MeetingElementEntity targetElement = elementRepository.findByIdAndMeetingId(
                        request.targetElementId(),
                        plan.getMeetingId()
                )
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "目标座位不存在"));
        if (targetElement.getElementKind() != ElementKind.SEAT) {
            throw new ApiException(HttpStatus.CONFLICT, "目标元素不是可排座座位");
        }

        PlanItemEntity currentItem = itemRepository.findByPlanIdAndParticipantIdAndItemType(
                planId, participant.getId(), PlanItemType.PERSON).orElse(null);
        PlanItemTargetEntity currentTarget = currentItem == null
                ? null
                : targetRepository.findAllByPlanItemIdIn(List.of(currentItem.getId()))
                .stream().findFirst().orElse(null);
        if (currentItem != null && currentItem.isLocked()) {
            throw new ApiException(HttpStatus.CONFLICT, "当前座位已锁定，无法移动");
        }
        if (currentTarget != null && currentTarget.getMeetingElementId().equals(targetElement.getId())) {
            return;
        }

        PlanItemTargetEntity occupiedTarget = targetRepository.findByMeetingElementId(targetElement.getId()).orElse(null);
        if (occupiedTarget != null) {
            PlanItemEntity occupiedItem = itemRepository.findById(occupiedTarget.getPlanItemId())
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
            String currentElementId = currentTarget.getMeetingElementId();
            String occupiedItemId = occupiedTarget.getPlanItemId();
            String currentItemId = currentTarget.getPlanItemId();
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
        SeatingPlanEntity plan = requirePlan(planId);
        Map<String,ParticipantEntity> participants = participantRepository
                .findAllByMeetingIdOrderByNameAsc(plan.getMeetingId())
                .stream()
                .collect(Collectors.toMap(value -> value.getId(), Function.identity()));
        Map<String,MeetingElementEntity> elements = elementRepository
                .findAllByMeetingIdOrderByStartRowAscStartColumnAsc(plan.getMeetingId())
                .stream()
                .collect(Collectors.toMap(value -> value.getId(), Function.identity()));

        HashSet<String> participantIds = new HashSet<String>();
        HashSet<String> targetElementIds = new HashSet<String>();
        for (AssignmentInput assignment : request.assignments()) {
            if (!participantIds.add(assignment.participantId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "同一人员不能同时安排到多个座位");
            }
            if (!targetElementIds.add(assignment.targetElementId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "同一座位不能同时安排多名人员");
            }
            ParticipantEntity participant = participants.get(assignment.participantId());
            if (participant == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "人员不存在");
            }
            if (participant.getAttendanceStatus() == AttendanceStatus.TEMPORARILY_ABSENT) {
                throw new ApiException(HttpStatus.CONFLICT, "临时不出席人员不能安排座位");
            }
            MeetingElementEntity element = elements.get(assignment.targetElementId());
            if (element == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "目标座位不存在");
            }
            if (element.getElementKind() != ElementKind.SEAT) {
                throw new ApiException(HttpStatus.CONFLICT, "目标元素不是可排座座位");
            }
        }

        List<PlanItemEntity> currentItems = itemRepository.findAllByPlanIdOrderByCreatedAtAsc(planId);
        List<String> itemIds = currentItems.stream().map(PlanItemEntity::getId).toList();
        List<PlanItemTargetEntity> currentTargets = itemIds.isEmpty()
                ? List.<PlanItemTargetEntity>of()
                : targetRepository.findAllByPlanItemIdIn(itemIds);
        Map<String,PlanItemTargetEntity> targetByItemId = currentTargets.stream().collect(Collectors.toMap(
                PlanItemTargetEntity::getPlanItemId,
                Function.identity(),
                (left, right) -> left
        ));
        Map<String,String> requestedTargetByParticipant = request.assignments().stream().collect(Collectors.toMap(
                value -> value.participantId(),
                value -> value.targetElementId()
        ));

        HashSet<String> reservedTargetIds = new HashSet<String>();
        ArrayList<PlanItemEntity> editableItems = new ArrayList<PlanItemEntity>();
        HashSet<String> lockedParticipantIds = new HashSet<String>();
        for (PlanItemEntity item : currentItems) {
            PlanItemTargetEntity target = targetByItemId.get(item.getId());
            if (item.getItemType() != PlanItemType.PERSON) {
                if (target != null) {
                    reservedTargetIds.add(target.getMeetingElementId());
                }
                continue;
            }
            if (item.isLocked()) {
                String requestedTarget = requestedTargetByParticipant.get(item.getParticipantId());
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

        Set<String> editableItemIds = editableItems.stream().map(PlanItemEntity::getId).collect(Collectors.toSet());
        List<PlanItemTargetEntity> editableTargets = currentTargets.stream()
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

        for (AssignmentInput assignment : request.assignments()) {
            if (lockedParticipantIds.contains(assignment.participantId())) {
                continue;
            }
            ParticipantEntity participant = participants.get(assignment.participantId());
            PlanItemEntity item = new PlanItemEntity();
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
     * 使用完整区域标记集合替换当前草稿中的预留区域。
     *
     * @param planId 排座方案ID
     * @param request 区域标记集合
     */
    @Transactional
    public void replaceReservedAreas(String planId, SaveReservedAreasRequest request) {
        SeatingPlanEntity plan = requirePlan(planId);
        Map<String,MeetingElementEntity> elements = elementRepository
                .findAllByMeetingIdOrderByStartRowAscStartColumnAsc(plan.getMeetingId())
                .stream()
                .collect(Collectors.toMap(MeetingElementEntity::getId, Function.identity()));
        List<ReservedAreaInput> reservedAreas = request.reservedAreas() == null
                ? List.of()
                : request.reservedAreas();
        HashSet<String> requestedTargetIds = new HashSet<String>();
        for (ReservedAreaInput area : reservedAreas) {
            for (String elementId : area.targetElementIds()) {
                MeetingElementEntity element = elements.get(elementId);
                if (element == null || element.getElementKind() != ElementKind.SEAT) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "区域标记只能选择座位");
                }
                if (!requestedTargetIds.add(elementId)) {
                    throw new ApiException(HttpStatus.CONFLICT, "同一座位不能加入多个区域标记");
                }
            }
        }

        List<PlanItemEntity> currentItems = itemRepository.findAllByPlanIdOrderByCreatedAtAsc(planId);
        List<String> currentItemIds = currentItems.stream().map(PlanItemEntity::getId).toList();
        List<PlanItemTargetEntity> currentTargets = currentItemIds.isEmpty()
                ? List.<PlanItemTargetEntity>of()
                : targetRepository.findAllByPlanItemIdIn(currentItemIds);
        Set<String> reservedItemIds = currentItems.stream()
                .filter(item -> item.getItemType() == PlanItemType.RESERVED)
                .map(PlanItemEntity::getId)
                .collect(Collectors.toSet());
        Set<String> occupiedTargetIds = currentTargets.stream()
                .filter(target -> !reservedItemIds.contains(target.getPlanItemId()))
                .map(PlanItemTargetEntity::getMeetingElementId)
                .collect(Collectors.toSet());
        if (requestedTargetIds.stream().anyMatch(occupiedTargetIds::contains)) {
            throw new ApiException(HttpStatus.CONFLICT, "区域标记不能覆盖已排人员或其他占用");
        }

        List<PlanItemEntity> reservedItems = currentItems.stream()
                .filter(item -> item.getItemType() == PlanItemType.RESERVED)
                .toList();
        reservedItems.forEach(item -> targetRepository.deleteAllByPlanItemId(item.getId()));
        itemRepository.deleteAll(reservedItems);
        for (ReservedAreaInput area : reservedAreas) {
            PlanItemEntity item = new PlanItemEntity();
            item.setPlanId(planId);
            item.setItemType(PlanItemType.RESERVED);
            item.setLabel(area.label().trim());
            item.setBackgroundColor(area.backgroundColor());
            item.setTextColor(area.textColor());
            item.setBold(area.bold());
            itemRepository.save(item);
            for (String targetId : area.targetElementIds()) {
                targetRepository.save(createTarget(item.getId(), targetId));
            }
        }
        touch(plan);
    }

    /**
     * 布局元素被删除或不再是座位时，释放指向这些元素的占用关系。
     *
     * @param meetingId 会议ID
     * @param removedElementIds 已删除或不可排座的元素ID
     */
    @Transactional
    public void releaseTargetsForRemovedElements(String meetingId, Set<String> removedElementIds) {
        if (removedElementIds == null || removedElementIds.isEmpty()) {
            return;
        }
        SeatingPlanEntity plan = planRepository.findFirstByMeetingIdOrderByCreatedAtAsc(meetingId).orElse(null);
        if (plan == null) {
            return;
        }
        List<PlanItemEntity> currentItems = itemRepository.findAllByPlanIdOrderByCreatedAtAsc(plan.getId());
        List<String> itemIds = currentItems.stream().map(PlanItemEntity::getId).toList();
        if (itemIds.isEmpty()) {
            return;
        }
        List<PlanItemTargetEntity> currentTargets = targetRepository.findAllByPlanItemIdIn(itemIds);
        List<PlanItemTargetEntity> removedTargets = currentTargets.stream()
                .filter(target -> removedElementIds.contains(target.getMeetingElementId()))
                .toList();
        if (removedTargets.isEmpty()) {
            return;
        }
        Set<String> removedTargetIds = removedTargets.stream()
                .map(PlanItemTargetEntity::getId)
                .collect(Collectors.toSet());
        targetRepository.deleteAll(removedTargets);
        targetRepository.flush();
        Set<String> itemIdsWithTargets = currentTargets.stream()
                .filter(target -> !removedTargetIds.contains(target.getId()))
                .map(PlanItemTargetEntity::getPlanItemId)
                .collect(Collectors.toSet());
        List<PlanItemEntity> emptyItems = currentItems.stream()
                .filter(item -> !itemIdsWithTargets.contains(item.getId()))
                .toList();
        itemRepository.deleteAll(emptyItems);
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
        SeatingPlanEntity plan = requirePlan(planId);
        PlanItemEntity item = itemRepository.findByPlanIdAndParticipantIdAndItemType(
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
        SeatingPlanEntity plan = requirePlan(planId);
        PlanItemEntity item = itemRepository.findByPlanIdAndParticipantIdAndItemType(
                        planId, participantId, PlanItemType.PERSON)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "人员尚未排座"));
        item.setLocked(locked);
        itemRepository.save(item);
        touch(plan);
    }

    private PlanItemTargetEntity createTarget(String planItemId, String meetingElementId) {
        PlanItemTargetEntity target = new PlanItemTargetEntity();
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
