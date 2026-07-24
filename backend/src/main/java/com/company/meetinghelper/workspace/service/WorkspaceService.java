package com.company.meetinghelper.workspace.service;

import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.award.entity.AwardRecordEntity;
import com.company.meetinghelper.award.repository.AwardRecordRepository;
import com.company.meetinghelper.meeting.entity.MeetingElementEntity;
import com.company.meetinghelper.meeting.repository.MeetingElementRepository;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import com.company.meetinghelper.seating.repository.PlanItemRepository;
import com.company.meetinghelper.seating.repository.PlanItemTargetRepository;
import com.company.meetinghelper.seating.entity.PlanItemType;
import com.company.meetinghelper.seating.repository.PlanVersionRepository;
import com.company.meetinghelper.seating.repository.SeatingPlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkspaceService {
    private static final List<String> BATCH_COLORS = List.of(
            "#DBEAFE", "#E0F2FE", "#CFFAFE", "#E0E7FF", "#EDE9FE",
            "#ECFEFF", "#EFF6FF", "#EEF2FF", "#F0F9FF", "#F5F3FF"
    );

    private final MeetingRepository meetingRepository;
    private final MeetingElementRepository elementRepository;
    private final ParticipantRepository participantRepository;
    private final AwardRecordRepository awardRepository;
    private final SeatingPlanRepository planRepository;
    private final PlanItemRepository itemRepository;
    private final PlanItemTargetRepository targetRepository;
    private final PlanVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    public WorkspaceService(
            MeetingRepository meetingRepository,
            MeetingElementRepository elementRepository,
            ParticipantRepository participantRepository,
            AwardRecordRepository awardRepository,
            SeatingPlanRepository planRepository,
            PlanItemRepository itemRepository,
            PlanItemTargetRepository targetRepository,
            PlanVersionRepository versionRepository,
            ObjectMapper objectMapper
    ) {
        this.meetingRepository = meetingRepository;
        this.elementRepository = elementRepository;
        this.participantRepository = participantRepository;
        this.awardRepository = awardRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(String meetingId) {
        var meeting = meetingRepository.findById(meetingId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会议不存在"));
        var plan = planRepository.findFirstByMeetingIdAndDeletedFalseOrderByCreatedAtAsc(meetingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会议尚未建立排座方案"));
        var elements = elementRepository.findAllByMeetingIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(meetingId);
        var participants = participantRepository.findAllByMeetingIdAndDeletedFalseOrderByNameAsc(meetingId);
        var participantIds = participants.stream().map(ParticipantEntity::getId).toList();
        var awards = participantIds.isEmpty()
                ? List.<AwardRecordEntity>of()
                : awardRepository.findAllByParticipantIdInAndDeletedFalseOrderByBatchOrderAsc(participantIds);
        var awardsByParticipant = awards.stream().collect(Collectors.groupingBy(
                AwardRecordEntity::getParticipantId,
                LinkedHashMap::new,
                Collectors.toList()
        ));

        var items = itemRepository.findAllByPlanIdAndDeletedFalseOrderByCreatedAtAsc(plan.getId());
        var itemIds = items.stream().map(item -> item.getId()).toList();
        var targets = itemIds.isEmpty()
                ? List.<com.company.meetinghelper.seating.entity.PlanItemTargetEntity>of()
                : targetRepository.findAllByPlanItemIdInAndDeletedFalse(itemIds);
        var targetsByItem = targets.stream().collect(Collectors.groupingBy(
                target -> target.getPlanItemId(),
                LinkedHashMap::new,
                Collectors.mapping(target -> target.getMeetingElementId(), Collectors.toList())
        ));
        var assignedByParticipant = items.stream()
                .filter(item -> item.getItemType() == PlanItemType.PERSON && item.getParticipantId() != null)
                .collect(Collectors.toMap(
                        item -> item.getParticipantId(),
                        item -> targetsByItem.getOrDefault(item.getId(), List.of()).stream().findFirst().orElse(null),
                        (left, right) -> left
                ));
        var lockedByParticipant = items.stream()
                .filter(item -> item.getItemType() == PlanItemType.PERSON && item.getParticipantId() != null)
                .collect(Collectors.toMap(
                        item -> item.getParticipantId(),
                        item -> item.isLocked(),
                        (left, right) -> left
                ));

        var participantViews = participants.stream()
                .map(participant -> toParticipantView(
                        participant,
                        awardsByParticipant.getOrDefault(participant.getId(), List.of()),
                        assignedByParticipant.get(participant.getId()),
                        lockedByParticipant.getOrDefault(participant.getId(), false)
                ))
                .toList();
        var itemViews = items.stream().map(item -> new WorkspaceResponse.PlanItemView(
                item.getId(),
                item.getItemType().name(),
                item.getParticipantId(),
                item.getLabel(),
                item.isLocked(),
                item.getBackgroundColor(),
                item.getTextColor(),
                item.isBold(),
                targetsByItem.getOrDefault(item.getId(), List.of())
        )).toList();
        var styleRules = participantViews.stream()
                .filter(view -> view.primaryBatchName() != null && view.displayColor() != null)
                .collect(Collectors.toMap(
                        WorkspaceResponse.ParticipantView::primaryBatchName,
                        WorkspaceResponse.ParticipantView::displayColor,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .entrySet().stream()
                .map(entry -> new WorkspaceResponse.StyleRuleView(
                        "primaryBatchName", entry.getKey(), entry.getValue(), "#172033"))
                .toList();

        return new WorkspaceResponse(
                new WorkspaceResponse.MeetingView(
                        meeting.getId(), meeting.getName(), meeting.getStatus(), meeting.getLayoutName(),
                        meeting.getLayoutVersion(), meeting.getUpdatedAt(), meeting.getUpdatedByName()),
                new WorkspaceResponse.PlanView(plan.getId(), plan.getName(), plan.getStatus(), plan.getCurrentVersionNo()),
                new WorkspaceResponse.LayoutView(
                        meeting.getGridRows(), meeting.getGridColumns(), meeting.getCellSize(),
                        elements.stream().map(this::toElementView).toList()),
                participantViews,
                itemViews,
                versionRepository.findAllByPlanIdAndDeletedFalseOrderByVersionNoDesc(plan.getId()).stream()
                        .map(version -> new WorkspaceResponse.VersionView(
                                version.getId(), version.getVersionNo(), version.getVersionName(), version.getChangeNote(),
                                version.isAutomatic(), version.getAssignedCount(), version.getUnassignedCount(),
                                version.getCreatedAt(), version.getCreatedByName()))
                        .toList(),
                fieldDefinitions(participantViews),
                styleRules
        );
    }

    private WorkspaceResponse.ElementView toElementView(MeetingElementEntity element) {
        return new WorkspaceResponse.ElementView(
                element.getId(), element.getElementType().name(), element.getCode(), element.getLabel(),
                element.getGridRow(), element.getGridColumn(), element.getRowSpan(), element.getColumnSpan(),
                element.getRotation(), element.getCapacity(), element.isAssignable(), element.isWalkable(),
                element.getGroupCode(), element.getGroupLabel(), element.getSequenceNo(),
                element.getBackgroundColor(), element.getBorderColor()
        );
    }

    private WorkspaceResponse.ParticipantView toParticipantView(
            ParticipantEntity participant,
            List<AwardRecordEntity> records,
            String assignedElementId,
            boolean locked
    ) {
        var sorted = records.stream()
                .sorted(java.util.Comparator.comparingInt(AwardRecordEntity::getBatchOrder))
                .toList();
        var primary = sorted.stream().findFirst().orElse(null);
        var repeated = primary == null ? List.<String>of() : sorted.stream()
                .filter(record -> record.getBatchOrder() != primary.getBatchOrder())
                .map(AwardRecordEntity::getBatchName)
                .distinct()
                .toList();
        var guest = participant.getParticipantType() != null
                && participant.getParticipantType().contains("嘉宾");
        var color = primary == null || guest
                ? null
                : BATCH_COLORS.get(Math.floorMod(primary.getBatchOrder() - 1, BATCH_COLORS.size()));
        return new WorkspaceResponse.ParticipantView(
                participant.getId(),
                participant.getEmployeeNo(),
                participant.getName(),
                participant.getLevelValue(),
                participant.getDepartment(),
                participant.getParticipantType(),
                splitTags(participant.getTags()),
                readAttributes(participant.getCustomAttributesJson()),
                locked,
                assignedElementId,
                primary == null ? null : primary.getBatchOrder(),
                primary == null ? null : primary.getBatchName(),
                color,
                repeated,
                sorted.stream().map(record -> new WorkspaceResponse.AwardView(
                        record.getId(), record.getBatchOrder(), record.getBatchName(), record.getAwardName(),
                        record.getAwardLevel(), record.getProjectName(), record.getTeamSize())).toList()
        );
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split("[,，]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private Map<String, String> readAttributes(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private List<WorkspaceResponse.FieldDefinitionView> fieldDefinitions(
            List<WorkspaceResponse.ParticipantView> participants
    ) {
        var fields = new ArrayList<WorkspaceResponse.FieldDefinitionView>();
        fields.add(new WorkspaceResponse.FieldDefinitionView("name", "姓名", "TEXT", true, false, true, true));
        fields.add(new WorkspaceResponse.FieldDefinitionView("employeeNo", "工号", "TEXT", true, false, true, false));
        fields.add(new WorkspaceResponse.FieldDefinitionView("level", "职级", "NUMBER", false, true, true, true));
        fields.add(new WorkspaceResponse.FieldDefinitionView("department", "部门", "TEXT", true, true, true, true));
        fields.add(new WorkspaceResponse.FieldDefinitionView(
                "participantType", "人员类型", "ENUM", true, true, true, true));
        if (participants.stream().anyMatch(value -> value.primaryBatchName() != null)) {
            fields.add(new WorkspaceResponse.FieldDefinitionView(
                    "primaryBatchName", "主排座批次", "ENUM", true, true, true, true));
        }
        fields.add(new WorkspaceResponse.FieldDefinitionView("tags", "标签", "MULTI_ENUM", true, true, true, false));
        participants.stream()
                .flatMap(participant -> participant.attributes().keySet().stream())
                .distinct()
                .sorted()
                .forEach(code -> fields.add(new WorkspaceResponse.FieldDefinitionView(
                        code, code, "TEXT", true, true, true, false)));
        return fields;
    }
}
