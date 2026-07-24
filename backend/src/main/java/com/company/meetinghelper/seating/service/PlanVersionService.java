package com.company.meetinghelper.seating.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.meeting.repository.MeetingElementRepository;
import com.company.meetinghelper.participant.entity.AttendanceStatus;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import com.company.meetinghelper.seating.api.dto.request.CreateVersionRequest;
import com.company.meetinghelper.seating.api.dto.response.RestoreVersionResult;
import com.company.meetinghelper.seating.api.dto.response.VersionResult;
import com.company.meetinghelper.seating.entity.PlanItemEntity;
import com.company.meetinghelper.seating.entity.PlanItemTargetEntity;
import com.company.meetinghelper.seating.entity.PlanItemType;
import com.company.meetinghelper.seating.entity.PlanVersionEntity;
import com.company.meetinghelper.seating.repository.PlanItemRepository;
import com.company.meetinghelper.seating.repository.PlanItemTargetRepository;
import com.company.meetinghelper.seating.repository.PlanVersionRepository;
import com.company.meetinghelper.seating.repository.SeatingPlanRepository;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import com.company.meetinghelper.workspace.service.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class PlanVersionService {
    private final SeatingPlanRepository planRepository;
    private final PlanVersionRepository versionRepository;
    private final PlanItemRepository itemRepository;
    private final PlanItemTargetRepository targetRepository;
    private final ParticipantRepository participantRepository;
    private final MeetingElementRepository elementRepository;
    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    /**
     * 创建排座版本服务。
     *
     * @param planRepository 排座方案仓储
     * @param versionRepository 方案版本仓储
     * @param itemRepository 排座明细仓储
     * @param targetRepository 排座目标仓储
     * @param participantRepository 参会人员仓储
     * @param elementRepository 会议元素仓储
     * @param workspaceService 工作区服务
     * @param objectMapper JSON序列化器
     */
    public PlanVersionService(
            SeatingPlanRepository planRepository,
            PlanVersionRepository versionRepository,
            PlanItemRepository itemRepository,
            PlanItemTargetRepository targetRepository,
            ParticipantRepository participantRepository,
            MeetingElementRepository elementRepository,
            WorkspaceService workspaceService,
            ObjectMapper objectMapper
    ) {
        this.planRepository = planRepository;
        this.versionRepository = versionRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
        this.participantRepository = participantRepository;
        this.elementRepository = elementRepository;
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验排座完成情况并发布只读版本。
     *
     * @param planId 排座方案ID
     * @param request 版本创建请求
     * @return 新版本信息
     */
    @Transactional
    public VersionResult create(String planId, CreateVersionRequest request) {
        var plan = planRepository.findById(planId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "排座方案不存在"));
        var nextVersion = versionRepository.findFirstByPlanIdAndDeletedFalseOrderByVersionNoDesc(planId)
                .map(value -> value.getVersionNo() + 1)
                .orElse(1);
        var workspace = workspaceService.getWorkspace(plan.getMeetingId());
        var assignedCount = (int) workspace.participants().stream()
                .filter(participant -> participant.assignedElementId() != null
                        && !"TEMPORARILY_ABSENT".equals(participant.attendanceStatus()))
                .count();
        var totalCount = (int) workspace.participants().stream()
                .filter(participant -> !"TEMPORARILY_ABSENT".equals(participant.attendanceStatus()))
                .count();
        var unassignedCount = totalCount - assignedCount;
        if (unassignedCount > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "当前还有 " + unassignedCount + " 位参会人员尚未排座，全部完成排座后才能发布"
            );
        }
        var version = new PlanVersionEntity();
        version.setPlanId(planId);
        version.setVersionNo(nextVersion);
        version.setVersionName(request.versionName());
        version.setChangeNote(request.changeNote());
        version.setAutomatic(request.automatic());
        try {
            version.setSnapshotJson(objectMapper.writeValueAsString(workspace));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "生成方案快照失败");
        }
        version.setAssignedCount(assignedCount);
        version.setUnassignedCount(unassignedCount);
        versionRepository.save(version);
        plan.setCurrentVersionNo(nextVersion);
        plan.setUpdatedById("demo-secretary");
        plan.setUpdatedByName("演示秘书");
        planRepository.save(plan);
        return new VersionResult(
                version.getId(), nextVersion, version.getVersionName(),
                version.getAssignedCount(), version.getUnassignedCount());
    }

    /**
     * 将历史版本快照恢复为当前草稿。
     *
     * @param planId 排座方案ID
     * @param versionId 版本ID
     * @return 恢复结果
     */
    @Transactional
    public RestoreVersionResult restore(String planId, String versionId) {
        var plan = planRepository.findById(planId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "排座方案不存在"));
        var version = versionRepository.findById(versionId)
                .filter(value -> !value.isDeleted() && value.getPlanId().equals(planId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "方案版本不存在"));

        var snapshot = readSnapshot(version);
        if (!snapshot.meeting().id().equals(plan.getMeetingId())) {
            throw new ApiException(HttpStatus.CONFLICT, "方案版本不属于当前会议");
        }

        var currentParticipants = participantRepository
                .findAllByMeetingIdAndDeletedFalseOrderByNameAsc(plan.getMeetingId());
        var participantIds = currentParticipants.stream()
                .map(value -> value.getId())
                .collect(java.util.stream.Collectors.toSet());
        var elementIds = elementRepository
                .findAllByMeetingIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(plan.getMeetingId())
                .stream()
                .map(value -> value.getId())
                .collect(java.util.stream.Collectors.toSet());

        var currentItems = itemRepository.findAllByPlanIdAndDeletedFalseOrderByCreatedAtAsc(planId);
        currentItems.forEach(item -> targetRepository.deleteAllByPlanItemId(item.getId()));
        targetRepository.flush();
        itemRepository.deleteAll(currentItems);
        itemRepository.flush();

        var snapshotParticipants = snapshot.participants().stream()
                .collect(java.util.stream.Collectors.toMap(
                        WorkspaceResponse.ParticipantView::id,
                        value -> value,
                        (left, right) -> left
                ));
        currentParticipants.forEach(participant -> {
            var source = snapshotParticipants.get(participant.getId());
            if (source != null && source.attendanceStatus() != null) {
                participant.setAttendanceStatus(AttendanceStatus.valueOf(source.attendanceStatus()));
            }
        });
        participantRepository.saveAll(currentParticipants);

        var restoredItems = 0;
        for (var source : snapshot.items()) {
            if (source.participantId() != null && !participantIds.contains(source.participantId())) {
                continue;
            }
            var validTargets = source.targetElementIds().stream().filter(elementIds::contains).toList();
            if (validTargets.isEmpty()) {
                continue;
            }
            var item = new PlanItemEntity();
            item.setPlanId(planId);
            item.setItemType(PlanItemType.valueOf(source.type()));
            item.setParticipantId(source.participantId());
            item.setLabel(source.label());
            item.setLocked(source.locked());
            item.setBackgroundColor(source.backgroundColor());
            item.setTextColor(source.textColor());
            item.setBold(source.bold());
            itemRepository.save(item);
            for (var elementId : validTargets) {
                var target = new PlanItemTargetEntity();
                target.setPlanItemId(item.getId());
                target.setMeetingElementId(elementId);
                targetRepository.save(target);
            }
            restoredItems++;
        }

        plan.setUpdatedById("demo-secretary");
        plan.setUpdatedByName("演示秘书");
        planRepository.save(plan);
        return new RestoreVersionResult(
                version.getId(), version.getVersionNo(), version.getVersionName(), restoredItems);
    }

    /**
     * 查询排座方案指定版本的工作区快照。
     *
     * @param planId 排座方案ID
     * @param versionId 版本ID
     * @return 工作区快照
     */
    @Transactional(readOnly = true)
    public WorkspaceResponse getSnapshot(String planId, String versionId) {
        var plan = planRepository.findById(planId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "排座方案不存在"));
        var version = versionRepository.findById(versionId)
                .filter(value -> !value.isDeleted() && value.getPlanId().equals(planId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "方案版本不存在"));
        var snapshot = readSnapshot(version);
        if (!snapshot.meeting().id().equals(plan.getMeetingId())) {
            throw new ApiException(HttpStatus.CONFLICT, "方案版本不属于当前会议");
        }
        return snapshot;
    }

    /**
     * 根据会议和版本查询工作区快照。
     *
     * @param meetingId 会议ID
     * @param versionId 版本ID
     * @return 工作区快照
     */
    @Transactional(readOnly = true)
    public WorkspaceResponse getSnapshotForMeeting(String meetingId, String versionId) {
        var plan = planRepository.findFirstByMeetingIdAndDeletedFalseOrderByCreatedAtAsc(meetingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会议尚未建立排座方案"));
        return getSnapshot(plan.getId(), versionId);
    }

    private WorkspaceResponse readSnapshot(PlanVersionEntity version) {
        try {
            return objectMapper.readValue(version.getSnapshotJson(), WorkspaceResponse.class);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取方案快照失败");
        }
    }

}
