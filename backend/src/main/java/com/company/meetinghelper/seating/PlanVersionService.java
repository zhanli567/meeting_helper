package com.company.meetinghelper.seating;

import com.company.meetinghelper.api.ApiException;
import com.company.meetinghelper.participant.ParticipantRepository;
import com.company.meetinghelper.workspace.WorkspaceService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class PlanVersionService {
    private final SeatingPlanRepository planRepository;
    private final PlanVersionRepository versionRepository;
    private final PlanItemRepository itemRepository;
    private final ParticipantRepository participantRepository;
    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public PlanVersionService(
            SeatingPlanRepository planRepository,
            PlanVersionRepository versionRepository,
            PlanItemRepository itemRepository,
            ParticipantRepository participantRepository,
            WorkspaceService workspaceService,
            ObjectMapper objectMapper
    ) {
        this.planRepository = planRepository;
        this.versionRepository = versionRepository;
        this.itemRepository = itemRepository;
        this.participantRepository = participantRepository;
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public VersionResult create(String planId, CreateVersionRequest request) {
        var plan = planRepository.findById(planId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "排座方案不存在"));
        var nextVersion = plan.getCurrentVersionNo() + 1;
        var workspace = workspaceService.getWorkspace(plan.getMeetingId());
        var assignedCount = (int) workspace.participants().stream()
                .filter(participant -> participant.assignedElementId() != null)
                .count();
        var totalCount = (int) participantRepository.countByMeetingIdAndDeletedFalse(plan.getMeetingId());
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
        version.setUnassignedCount(totalCount - assignedCount);
        versionRepository.save(version);
        plan.setCurrentVersionNo(nextVersion);
        plan.setUpdatedById("demo-secretary");
        plan.setUpdatedByName("演示秘书");
        planRepository.save(plan);
        return new VersionResult(
                version.getId(), nextVersion, version.getVersionName(),
                version.getAssignedCount(), version.getUnassignedCount());
    }

    public record CreateVersionRequest(
            @NotBlank String versionName,
            String changeNote,
            boolean automatic
    ) {
    }

    public record VersionResult(
            String id,
            int versionNo,
            String versionName,
            int assignedCount,
            int unassignedCount
    ) {
    }
}

