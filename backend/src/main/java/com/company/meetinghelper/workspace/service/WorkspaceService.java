package com.company.meetinghelper.workspace.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.meeting.entity.MeetingElementEntity;
import com.company.meetinghelper.meeting.entity.MeetingEntity;
import com.company.meetinghelper.meeting.repository.MeetingElementRepository;
import com.company.meetinghelper.meeting.service.MeetingAccessService;
import com.company.meetinghelper.participant.entity.MeetingParticipantFieldEntity;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import com.company.meetinghelper.participant.entity.ParticipantRecordEntity;
import com.company.meetinghelper.participant.repository.MeetingParticipantFieldRepository;
import com.company.meetinghelper.participant.repository.ParticipantRecordRepository;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import com.company.meetinghelper.seating.entity.PlanItemEntity;
import com.company.meetinghelper.seating.entity.PlanItemTargetEntity;
import com.company.meetinghelper.seating.entity.PlanItemType;
import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import com.company.meetinghelper.seating.repository.PlanItemRepository;
import com.company.meetinghelper.seating.repository.PlanItemTargetRepository;
import com.company.meetinghelper.seating.repository.PlanVersionRepository;
import com.company.meetinghelper.seating.repository.SeatingPlanRepository;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse.FieldDefinitionView;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse.ParticipantRecordView;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse.ParticipantView;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse.PlanItemView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Represents the workspace service class.
 */
@Service
public class WorkspaceService {
    private final MeetingAccessService meetingAccessService;
    private final MeetingElementRepository elementRepository;
    private final ParticipantRepository participantRepository;
    private final MeetingParticipantFieldRepository fieldRepository;
    private final ParticipantRecordRepository recordRepository;
    private final SeatingPlanRepository planRepository;
    private final PlanItemRepository itemRepository;
    private final PlanItemTargetRepository targetRepository;
    private final PlanVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建会议工作区聚合服务。
     *
     * @param meetingAccessService 会议归属校验服务
     * @param elementRepository 会议元素仓储
     * @param participantRepository 参会人员仓储
     * @param fieldRepository 人员动态字段仓储
     * @param recordRepository 人员动态记录仓储
     * @param planRepository 排座方案仓储
     * @param itemRepository 排座明细仓储
     * @param targetRepository 排座目标仓储
     * @param versionRepository 方案版本仓储
     * @param objectMapper JSON序列化器
     */
    public WorkspaceService(
            MeetingAccessService meetingAccessService,
            MeetingElementRepository elementRepository,
            ParticipantRepository participantRepository,
            MeetingParticipantFieldRepository fieldRepository,
            ParticipantRecordRepository recordRepository,
            SeatingPlanRepository planRepository,
            PlanItemRepository itemRepository,
            PlanItemTargetRepository targetRepository,
            PlanVersionRepository versionRepository,
            ObjectMapper objectMapper
    ) {
        this.meetingAccessService = meetingAccessService;
        this.elementRepository = elementRepository;
        this.participantRepository = participantRepository;
        this.fieldRepository = fieldRepository;
        this.recordRepository = recordRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 聚合会议布局、人员、排座、样式和版本数据。
     *
     * @param meetingId 会议ID
     * @return 当前草稿工作区
     */
    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(String meetingId) {
        MeetingEntity meeting = meetingAccessService.requireOwnedMeeting(meetingId);
        SeatingPlanEntity plan = planRepository.findFirstByMeetingIdOrderByCreatedAtAsc(meetingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会议尚未建立排座方案"));
        WorkspaceData data = workspaceData(meetingId, plan.getId());
        return new WorkspaceResponse(
                meetingView(meeting),
                new WorkspaceResponse.PlanView(
                        plan.getId(),
                        plan.getName(),
                        plan.getStatus(),
                        plan.getCurrentVersionNo()
                ),
                layoutView(meeting, data.elements()),
                participantViews(data),
                itemViews(data),
                versionViews(plan.getId()),
                fieldDefinitions(data.participantFields()),
                List.of()
        );
    }

    private WorkspaceData workspaceData(String meetingId, String planId) {
        List<MeetingElementEntity> elements = elementRepository
                .findAllByMeetingIdOrderByStartRowAscStartColumnAsc(meetingId);
        List<ParticipantEntity> participants = participantRepository.findAllByMeetingIdOrderByNameAsc(meetingId);
        List<String> participantIds = participants.stream().map(ParticipantEntity::getId).toList();
        List<MeetingParticipantFieldEntity> participantFields = fieldRepository
                .findAllByMeetingIdOrderBySortOrderAsc(meetingId);
        List<ParticipantRecordEntity> participantRecords = participantIds.isEmpty()
                ? List.<ParticipantRecordEntity>of()
                : recordRepository
                        .findAllByParticipantIdInOrderByParticipantIdAscRecordOrderAsc(
                                participantIds
                        );
        LinkedHashMap<String,List<ParticipantRecordEntity>> recordsByParticipant = participantRecords.stream().collect(Collectors.groupingBy(
                ParticipantRecordEntity::getParticipantId,
                LinkedHashMap::new,
                Collectors.toList()
        ));

        List<PlanItemEntity> items = itemRepository.findAllByPlanIdOrderByCreatedAtAsc(planId);
        List<String> itemIds = items.stream().map(item -> item.getId()).toList();
        List<PlanItemTargetEntity> targets = itemIds.isEmpty()
                ? List.<PlanItemTargetEntity>of()
                : targetRepository.findAllByPlanItemIdIn(itemIds);
        LinkedHashMap<String,List<String>> targetsByItem = targets.stream().collect(Collectors.groupingBy(
                target -> target.getPlanItemId(),
                LinkedHashMap::new,
                Collectors.mapping(target -> target.getMeetingElementId(), Collectors.toList())
        ));
        return new WorkspaceData(
                elements,
                participants,
                participantFields,
                recordsByParticipant,
                items,
                targetsByItem
        );
    }

    private WorkspaceResponse.MeetingView meetingView(MeetingEntity meeting) {
        return new WorkspaceResponse.MeetingView(
                meeting.getId(),
                meeting.getName(),
                meeting.getStatus(),
                meeting.getLayoutName(),
                meeting.getLayoutVersion(),
                meeting.getUpdatedAt(),
                meeting.getUpdatedByName()
        );
    }

    private WorkspaceResponse.LayoutView layoutView(
            MeetingEntity meeting,
            List<MeetingElementEntity> elements
    ) {
        return new WorkspaceResponse.LayoutView(
                meeting.getGridRows(),
                meeting.getGridColumns(),
                elements.stream().map(this::toElementView).toList()
        );
    }

    private List<ParticipantView> participantViews(WorkspaceData data) {
        Map<String,String> assignedByParticipant = data.items().stream()
                .filter(item -> item.getItemType() == PlanItemType.PERSON && item.getParticipantId() != null)
                .collect(Collectors.toMap(
                        item -> item.getParticipantId(),
                        item -> data.targetsByItem().getOrDefault(item.getId(), List.of())
                                .stream()
                                .findFirst()
                                .orElse(null),
                        (left, right) -> left
                ));
        Map<String,Boolean> lockedByParticipant = data.items().stream()
                .filter(item -> item.getItemType() == PlanItemType.PERSON && item.getParticipantId() != null)
                .collect(Collectors.toMap(
                        item -> item.getParticipantId(),
                        item -> item.isLocked(),
                        (left, right) -> left
                ));

        return data.participants().stream()
                .map(participant -> toParticipantView(
                        participant,
                        data.participantFields(),
                        data.recordsByParticipant().getOrDefault(participant.getId(), List.of()),
                        assignedByParticipant.get(participant.getId()),
                        lockedByParticipant.getOrDefault(participant.getId(), false)
                ))
                .toList();
    }

    private List<PlanItemView> itemViews(WorkspaceData data) {
        return data.items().stream().map(item -> new WorkspaceResponse.PlanItemView(
                item.getId(),
                item.getItemType().name(),
                item.getParticipantId(),
                item.getLabel(),
                item.isLocked(),
                item.getBackgroundColor(),
                item.getTextColor(),
                item.isBold(),
                data.targetsByItem().getOrDefault(item.getId(), List.of())
        )).toList();
    }

    private List<WorkspaceResponse.VersionView> versionViews(String planId) {
        return versionRepository.findAllByPlanIdOrderByVersionNoDesc(planId).stream()
                .map(version -> new WorkspaceResponse.VersionView(
                        version.getId(),
                        version.getVersionNo(),
                        version.getVersionName(),
                        version.getChangeNote(),
                        version.isAutomatic(),
                        version.getAssignedCount(),
                        version.getUnassignedCount(),
                        version.getCreatedAt(),
                        version.getCreatedByName()
                ))
                .toList();
    }

    private WorkspaceResponse.ElementView toElementView(MeetingElementEntity element) {
        return new WorkspaceResponse.ElementView(
                element.getId(), element.getElementKind().name(), element.getElementName(),
                element.getStartRow(), element.getStartColumn(), element.getRowSpan(),
                element.getColumnSpan(), element.getFillColor()
        );
    }

    private WorkspaceResponse.ParticipantView toParticipantView(
            ParticipantEntity participant,
            List<MeetingParticipantFieldEntity> fields,
            List<ParticipantRecordEntity> records,
            String assignedElementId,
            boolean locked
    ) {
        LinkedHashMap<String,List<String>> values = new LinkedHashMap<String, List<String>>();
        fields.forEach(field -> values.put(field.getFieldName(), new ArrayList<>()));
        ArrayList<ParticipantRecordView> recordViews = new ArrayList<WorkspaceResponse.ParticipantRecordView>();
        records.stream()
                .sorted(Comparator.comparingInt(ParticipantRecordEntity::getRecordOrder))
                .forEach(record -> {
                    Map<String,String> attributes = orderedAttributes(readAttributes(record.getAttributesJson()), fields);
                    recordViews.add(new WorkspaceResponse.ParticipantRecordView(
                            record.getId(),
                            record.getRecordOrder(),
                            attributes
                    ));
                    attributes.forEach((fieldName, value) -> {
                        if (value == null || value.isBlank()) {
                            return;
                        }
                        List<String> fieldValues = values.get(fieldName);
                        if (!fieldValues.contains(value)) {
                            fieldValues.add(value);
                        }
                    });
                });
        LinkedHashMap<String,String> primaryAttributes = new LinkedHashMap<String, String>();
        LinkedHashMap<String,List<String>> attributeValues = new LinkedHashMap<String, List<String>>();
        values.forEach((fieldName, fieldValues) -> {
            if (!fieldValues.isEmpty()) {
                primaryAttributes.put(fieldName, fieldValues.getFirst());
                attributeValues.put(fieldName, List.copyOf(fieldValues));
            }
        });
        return new WorkspaceResponse.ParticipantView(
                participant.getId(),
                participant.getEmployeeNo(),
                participant.getName(),
                Collections.unmodifiableMap(primaryAttributes),
                Collections.unmodifiableMap(attributeValues),
                List.copyOf(recordViews),
                participant.getAttendanceStatus() == null ? "PRESENT" : participant.getAttendanceStatus().name(),
                locked,
                assignedElementId
        );
    }

    private Map<String, String> orderedAttributes(
            Map<String, String> attributes,
            List<MeetingParticipantFieldEntity> fields
    ) {
        LinkedHashMap<String,String> ordered = new LinkedHashMap<String, String>();
        for (MeetingParticipantFieldEntity field : fields) {
            String value = attributes.get(field.getFieldName());
            if (value != null) {
                ordered.put(field.getFieldName(), value);
            }
        }
        return Collections.unmodifiableMap(ordered);
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
            List<MeetingParticipantFieldEntity> participantFields
    ) {
        ArrayList<FieldDefinitionView> fields = new ArrayList<WorkspaceResponse.FieldDefinitionView>();
        fields.add(new WorkspaceResponse.FieldDefinitionView("name", "姓名", "TEXT", true, false, true, true));
        fields.add(new WorkspaceResponse.FieldDefinitionView("employeeNo", "工号", "TEXT", true, false, true, false));
        participantFields.forEach(field -> fields.add(new WorkspaceResponse.FieldDefinitionView(
                field.getFieldName(),
                field.getFieldName(),
                "TEXT",
                true,
                true,
                true,
                false
        )));
        return List.copyOf(fields);
    }

    private record WorkspaceData(
            List<MeetingElementEntity> elements,
            List<ParticipantEntity> participants,
            List<MeetingParticipantFieldEntity> participantFields,
            Map<String, List<ParticipantRecordEntity>> recordsByParticipant,
            List<PlanItemEntity> items,
            Map<String, List<String>> targetsByItem
    ) {
    }
}
