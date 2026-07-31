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

/**
 * SeatingService 类。
 */
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
        ParticipantEntity participant = requireAssignableParticipant(plan, request.participantId());
        MeetingElementEntity targetElement = requireSeatElement(plan, request.targetElementId());
        PlanItemEntity currentItem = itemRepository.findByPlanIdAndParticipantIdAndItemType(
                planId, participant.getId(), PlanItemType.PERSON).orElse(null);
        PlanItemTargetEntity currentTarget = currentTarget(currentItem);
        if (currentItem != null && currentItem.isLocked()) {
            throw new ApiException(HttpStatus.CONFLICT, "当前座位已锁定，无法移动");
        }
        if (currentTarget != null && currentTarget.getMeetingElementId().equals(targetElement.getId())) {
            return;
        }

        PlanItemTargetEntity occupiedTarget = targetRepository.findByMeetingElementId(targetElement.getId()).orElse(null);
        if (occupiedTarget != null) {
            swapAssignments(plan, targetElement, currentTarget, occupiedTarget);
            return;
        }

        currentItem = ensurePersonItem(planId, participant, currentItem);
        currentTarget = ensureTarget(currentItem, currentTarget);
        currentTarget.setMeetingElementId(targetElement.getId());
        targetRepository.save(currentTarget);
        touch(plan);
    }

    private ParticipantEntity requireAssignableParticipant(
            SeatingPlanEntity plan,
            String participantId
    ) {
        ParticipantEntity participant = participantRepository.findByIdAndMeetingId(
                        participantId,
                        plan.getMeetingId()
                )
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "人员不存在"));
        if (participant.getAttendanceStatus() == AttendanceStatus.TEMPORARILY_ABSENT) {
            throw new ApiException(HttpStatus.CONFLICT, "临时不出席人员不能安排座位");
        }
        return participant;
    }

    private MeetingElementEntity requireSeatElement(
            SeatingPlanEntity plan,
            String targetElementId
    ) {
        MeetingElementEntity targetElement = elementRepository.findByIdAndMeetingId(
                        targetElementId,
                        plan.getMeetingId()
                )
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "目标座位不存在"));
        if (targetElement.getElementKind() != ElementKind.SEAT) {
            throw new ApiException(HttpStatus.CONFLICT, "目标元素不是可排座座位");
        }
        return targetElement;
    }

    private PlanItemTargetEntity currentTarget(PlanItemEntity currentItem) {
        return currentItem == null
                ? null
                : targetRepository.findAllByPlanItemIdIn(List.of(currentItem.getId()))
                        .stream()
                        .findFirst()
                        .orElse(null);
    }

    private void swapAssignments(
            SeatingPlanEntity plan,
            MeetingElementEntity targetElement,
            PlanItemTargetEntity currentTarget,
            PlanItemTargetEntity occupiedTarget
    ) {
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
        targetRepository.deleteAll(List.of(occupiedTarget, currentTarget));
        targetRepository.flush();
        targetRepository.save(createTarget(occupiedTarget.getPlanItemId(), currentElementId));
        targetRepository.save(createTarget(currentTarget.getPlanItemId(), targetElement.getId()));
        touch(plan);
    }

    private PlanItemEntity ensurePersonItem(
            String planId,
            ParticipantEntity participant,
            PlanItemEntity currentItem
    ) {
        if (currentItem != null) {
            return currentItem;
        }
        PlanItemEntity item = new PlanItemEntity();
        item.setPlanId(planId);
        item.setItemType(PlanItemType.PERSON);
        item.setParticipantId(participant.getId());
        item.setLabel(participant.getName());
        itemRepository.save(item);
        return item;
    }

    private PlanItemTargetEntity ensureTarget(
            PlanItemEntity currentItem,
            PlanItemTargetEntity currentTarget
    ) {
        if (currentTarget != null) {
            return currentTarget;
        }
        PlanItemTargetEntity target = new PlanItemTargetEntity();
        target.setPlanItemId(currentItem.getId());
        return target;
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
        Map<String,ParticipantEntity> participants = participantsById(plan.getMeetingId());
        Map<String,MeetingElementEntity> elements = elementsById(plan.getMeetingId());
        AssignmentValidation validation = validateAssignmentInputs(request.assignments(), participants, elements);
        CurrentPlanItems current = currentPlanItems(planId);
        HashSet<String> lockedParticipantIds = lockedParticipantIds(
                current,
                validation.requestedTargetByParticipant()
        );
        ensureAssignmentTargetsAvailable(validation.targetElementIds(), reservedTargetIds(current));
        deleteEditablePersonItems(current, lockedParticipantIds);
        saveRequestedAssignments(planId, request.assignments(), participants, lockedParticipantIds);
        touch(plan);
    }

    private Map<String, ParticipantEntity> participantsById(String meetingId) {
        return participantRepository.findAllByMeetingIdOrderByNameAsc(meetingId)
                .stream()
                .collect(Collectors.toMap(value -> value.getId(), Function.identity()));
    }

    private Map<String, MeetingElementEntity> elementsById(String meetingId) {
        return elementRepository.findAllByMeetingIdOrderByStartRowAscStartColumnAsc(meetingId)
                .stream()
                .collect(Collectors.toMap(value -> value.getId(), Function.identity()));
    }

    private AssignmentValidation validateAssignmentInputs(
            List<AssignmentInput> assignments,
            Map<String, ParticipantEntity> participants,
            Map<String, MeetingElementEntity> elements
    ) {
        HashSet<String> participantIds = new HashSet<String>();
        HashSet<String> targetElementIds = new HashSet<String>();
        for (AssignmentInput assignment : assignments) {
            validateAssignmentInput(assignment, participants, elements, participantIds, targetElementIds);
        }
        Map<String,String> requestedTargetByParticipant = assignments.stream().collect(Collectors.toMap(
                value -> value.participantId(),
                value -> value.targetElementId()
        ));
        return new AssignmentValidation(participantIds, targetElementIds, requestedTargetByParticipant);
    }

    private void validateAssignmentInput(
            AssignmentInput assignment,
            Map<String, ParticipantEntity> participants,
            Map<String, MeetingElementEntity> elements,
            Set<String> participantIds,
            Set<String> targetElementIds
    ) {
        if (!participantIds.add(assignment.participantId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "同一人员不能同时安排到多个座位");
        }
        if (!targetElementIds.add(assignment.targetElementId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "同一座位不能同时安排多名人员");
        }
        validateAssignmentParticipant(participants.get(assignment.participantId()));
        validateAssignmentElement(elements.get(assignment.targetElementId()));
    }

    private void validateAssignmentParticipant(ParticipantEntity participant) {
        if (participant == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "人员不存在");
        }
        if (participant.getAttendanceStatus() == AttendanceStatus.TEMPORARILY_ABSENT) {
            throw new ApiException(HttpStatus.CONFLICT, "临时不出席人员不能安排座位");
        }
    }

    private void validateAssignmentElement(MeetingElementEntity element) {
        if (element == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "目标座位不存在");
        }
        if (element.getElementKind() != ElementKind.SEAT) {
            throw new ApiException(HttpStatus.CONFLICT, "目标元素不是可排座座位");
        }
    }

    private CurrentPlanItems currentPlanItems(String planId) {
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
        return new CurrentPlanItems(currentItems, currentTargets, targetByItemId);
    }

    private HashSet<String> lockedParticipantIds(
            CurrentPlanItems current,
            Map<String, String> requestedTargetByParticipant
    ) {
        HashSet<String> lockedParticipantIds = new HashSet<String>();
        for (PlanItemEntity item : current.items()) {
            if (item.getItemType() == PlanItemType.PERSON && item.isLocked()) {
                validateLockedAssignment(item, current.targetByItemId(), requestedTargetByParticipant);
                lockedParticipantIds.add(item.getParticipantId());
            }
        }
        return lockedParticipantIds;
    }

    private void validateLockedAssignment(
            PlanItemEntity item,
            Map<String, PlanItemTargetEntity> targetByItemId,
            Map<String, String> requestedTargetByParticipant
    ) {
        PlanItemTargetEntity target = targetByItemId.get(item.getId());
        String requestedTarget = requestedTargetByParticipant.get(item.getParticipantId());
        if (target == null || !target.getMeetingElementId().equals(requestedTarget)) {
            throw new ApiException(HttpStatus.CONFLICT, "已锁定人员的座位不能修改或移除");
        }
    }

    private HashSet<String> reservedTargetIds(CurrentPlanItems current) {
        HashSet<String> reservedTargetIds = new HashSet<String>();
        for (PlanItemEntity item : current.items()) {
            PlanItemTargetEntity target = current.targetByItemId().get(item.getId());
            if (item.getItemType() != PlanItemType.PERSON && target != null) {
                reservedTargetIds.add(target.getMeetingElementId());
            }
        }
        return reservedTargetIds;
    }

    private void ensureAssignmentTargetsAvailable(
            Set<String> targetElementIds,
            Set<String> reservedTargetIds
    ) {
        if (targetElementIds.stream().anyMatch(reservedTargetIds::contains)) {
            throw new ApiException(HttpStatus.CONFLICT, "目标座位已被设备、预留或禁用状态占用");
        }
    }

    private void deleteEditablePersonItems(
            CurrentPlanItems current,
            Set<String> lockedParticipantIds
    ) {
        List<PlanItemEntity> editableItems = current.items().stream()
                .filter(item -> item.getItemType() == PlanItemType.PERSON)
                .filter(item -> !lockedParticipantIds.contains(item.getParticipantId()))
                .toList();
        deletePlanItems(editableItems, current.targets());
    }

    private void deletePlanItems(
            List<PlanItemEntity> items,
            List<PlanItemTargetEntity> targets
    ) {
        Set<String> itemIds = items.stream().map(PlanItemEntity::getId).collect(Collectors.toSet());
        List<PlanItemTargetEntity> itemTargets = targets.stream()
                .filter(target -> itemIds.contains(target.getPlanItemId()))
                .toList();
        if (!itemTargets.isEmpty()) {
            targetRepository.deleteAll(itemTargets);
            targetRepository.flush();
        }
        if (!items.isEmpty()) {
            itemRepository.deleteAll(items);
            itemRepository.flush();
        }
    }

    private void saveRequestedAssignments(
            String planId,
            List<AssignmentInput> assignments,
            Map<String, ParticipantEntity> participants,
            Set<String> lockedParticipantIds
    ) {
        for (AssignmentInput assignment : assignments) {
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
    }

    /**
     * 使用完整区域集合替换当前草稿中的预留区域。
     *
     * @param planId 排座方案ID
     * @param request 区域集合
     */
    @Transactional
    public void replaceReservedAreas(String planId, SaveReservedAreasRequest request) {
        SeatingPlanEntity plan = requirePlan(planId);
        Map<String,MeetingElementEntity> elements = elementsById(plan.getMeetingId());
        List<ReservedAreaInput> reservedAreas = request.reservedAreas() == null
                ? List.of()
                : request.reservedAreas();
        Set<String> requestedTargetIds = validateReservedAreas(reservedAreas, elements);
        CurrentPlanItems current = currentPlanItems(planId);
        ensureReservedTargetsAvailable(requestedTargetIds, occupiedTargetIds(current));
        replaceReservedItems(planId, reservedAreas, current);
        touch(plan);
    }

    private Set<String> validateReservedAreas(
            List<ReservedAreaInput> reservedAreas,
            Map<String, MeetingElementEntity> elements
    ) {
        HashSet<String> requestedTargetIds = new HashSet<String>();
        for (ReservedAreaInput area : reservedAreas) {
            for (String elementId : area.targetElementIds()) {
                MeetingElementEntity element = elements.get(elementId);
                if (element == null || element.getElementKind() != ElementKind.SEAT) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "区域只能选择座位");
                }
                if (!requestedTargetIds.add(elementId)) {
                    throw new ApiException(HttpStatus.CONFLICT, "同一座位不能加入多个区域");
                }
            }
        }
        return requestedTargetIds;
    }

    private Set<String> occupiedTargetIds(CurrentPlanItems current) {
        Set<String> reservedItemIds = current.items().stream()
                .filter(item -> item.getItemType() == PlanItemType.RESERVED)
                .map(PlanItemEntity::getId)
                .collect(Collectors.toSet());
        return current.targets().stream()
                .filter(target -> !reservedItemIds.contains(target.getPlanItemId()))
                .map(PlanItemTargetEntity::getMeetingElementId)
                .collect(Collectors.toSet());
    }

    private void ensureReservedTargetsAvailable(
            Set<String> requestedTargetIds,
            Set<String> occupiedTargetIds
    ) {
        if (requestedTargetIds.stream().anyMatch(occupiedTargetIds::contains)) {
            throw new ApiException(HttpStatus.CONFLICT, "区域不能覆盖已排人员或其他占用");
        }
    }

    private void replaceReservedItems(
            String planId,
            List<ReservedAreaInput> reservedAreas,
            CurrentPlanItems current
    ) {
        List<PlanItemEntity> reservedItems = current.items().stream()
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

    private record AssignmentValidation(
            Set<String> participantIds,
            Set<String> targetElementIds,
            Map<String, String> requestedTargetByParticipant
    ) {
    }

    private record CurrentPlanItems(
            List<PlanItemEntity> items,
            List<PlanItemTargetEntity> targets,
            Map<String, PlanItemTargetEntity> targetByItemId
    ) {
    }

}
