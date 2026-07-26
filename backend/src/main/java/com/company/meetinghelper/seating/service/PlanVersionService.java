package com.company.meetinghelper.seating.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.meeting.repository.MeetingElementRepository;
import com.company.meetinghelper.meeting.service.MeetingAccessService;
import com.company.meetinghelper.participant.entity.AttendanceStatus;
import com.company.meetinghelper.participant.entity.MeetingParticipantFieldEntity;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import com.company.meetinghelper.participant.entity.ParticipantRecordEntity;
import com.company.meetinghelper.participant.repository.MeetingParticipantFieldRepository;
import com.company.meetinghelper.participant.repository.ParticipantRecordRepository;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import com.company.meetinghelper.participant.service.ParticipantFieldRegistrationService;
import com.company.meetinghelper.seating.api.dto.request.CreateVersionRequest;
import com.company.meetinghelper.seating.api.dto.response.RestoreVersionResult;
import com.company.meetinghelper.seating.api.dto.response.VersionResult;
import com.company.meetinghelper.seating.entity.PlanItemEntity;
import com.company.meetinghelper.seating.entity.PlanItemTargetEntity;
import com.company.meetinghelper.seating.entity.PlanItemType;
import com.company.meetinghelper.seating.entity.PlanVersionEntity;
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
import com.company.meetinghelper.workspace.service.WorkspaceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanVersionService {
    private final SeatingPlanRepository planRepository;
    private final PlanVersionRepository versionRepository;
    private final PlanItemRepository itemRepository;
    private final PlanItemTargetRepository targetRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantFieldRegistrationService fieldRegistrationService;
    private final MeetingParticipantFieldRepository fieldRepository;
    private final ParticipantRecordRepository recordRepository;
    private final MeetingElementRepository elementRepository;
    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;
    private final MeetingAccessService meetingAccessService;

    /**
     * 创建排座版本服务。
     *
     * @param planRepository 排座方案仓储
     * @param versionRepository 方案版本仓储
     * @param itemRepository 排座明细仓储
     * @param targetRepository 排座目标仓储
     * @param participantRepository 参会人员仓储
     * @param fieldRegistrationService 人员动态字段注册服务
     * @param fieldRepository 会议人员字段仓储
     * @param recordRepository 人员动态记录仓储
     * @param elementRepository 会议元素仓储
     * @param workspaceService 工作区服务
     * @param objectMapper JSON序列化器
     * @param meetingAccessService 会议归属校验服务
     */
    public PlanVersionService(
            SeatingPlanRepository planRepository,
            PlanVersionRepository versionRepository,
            PlanItemRepository itemRepository,
            PlanItemTargetRepository targetRepository,
            ParticipantRepository participantRepository,
            ParticipantFieldRegistrationService fieldRegistrationService,
            MeetingParticipantFieldRepository fieldRepository,
            ParticipantRecordRepository recordRepository,
            MeetingElementRepository elementRepository,
            WorkspaceService workspaceService,
            ObjectMapper objectMapper,
            MeetingAccessService meetingAccessService
    ) {
        this.planRepository = planRepository;
        this.versionRepository = versionRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
        this.participantRepository = participantRepository;
        this.fieldRegistrationService = fieldRegistrationService;
        this.fieldRepository = fieldRepository;
        this.recordRepository = recordRepository;
        this.elementRepository = elementRepository;
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper;
        this.meetingAccessService = meetingAccessService;
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
        SeatingPlanEntity plan = meetingAccessService.requireOwnedPlan(planId);
        String versionName = request.versionName().trim();
        if (versionRepository.existsByPlanIdAndVersionNameIgnoreCaseAndDeletedFalse(planId, versionName)) {
            throw new ApiException(HttpStatus.CONFLICT, "版本名称已存在，请使用其他名称");
        }
        Integer nextVersion = versionRepository.findFirstByPlanIdAndDeletedFalseOrderByVersionNoDesc(planId)
                .map(value -> value.getVersionNo() + 1)
                .orElse(1);
        WorkspaceResponse workspace = workspaceService.getWorkspace(plan.getMeetingId());
        int assignedCount = (int) workspace.participants().stream()
                .filter(participant -> participant.assignedElementId() != null
                        && !"TEMPORARILY_ABSENT".equals(participant.attendanceStatus()))
                .count();
        int totalCount = (int) workspace.participants().stream()
                .filter(participant -> !"TEMPORARILY_ABSENT".equals(participant.attendanceStatus()))
                .count();
        int unassignedCount = totalCount - assignedCount;
        if (unassignedCount > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "当前还有 " + unassignedCount + " 位参会人员尚未排座，全部完成排座后才能发布"
            );
        }
        PlanVersionEntity version = new PlanVersionEntity();
        version.setPlanId(planId);
        version.setVersionNo(nextVersion);
        version.setVersionName(versionName);
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
        SeatingPlanEntity plan = meetingAccessService.requireOwnedPlan(planId);
        PlanVersionEntity version = versionRepository.findById(versionId)
                .filter(value -> !value.isDeleted() && value.getPlanId().equals(planId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "方案版本不存在"));

        WorkspaceResponse snapshot = readSnapshot(version);
        if (!snapshot.meeting().id().equals(plan.getMeetingId())) {
            throw new ApiException(HttpStatus.CONFLICT, "方案版本不属于当前会议");
        }

        fieldRegistrationService.lockMeeting(plan.getMeetingId());
        List<ParticipantEntity> currentParticipants = participantRepository
                .findAllByMeetingIdAndDeletedFalseOrderByNameAsc(plan.getMeetingId());
        List<ParticipantEntity> participantsIncludingDeleted = participantRepository
                .findAllByMeetingIdOrderByDeletedAscNameAsc(plan.getMeetingId());
        Set<String> elementIds = elementRepository
                .findAllByMeetingIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(plan.getMeetingId())
                .stream()
                .map(value -> value.getId())
                .collect(Collectors.toSet());

        List<PlanItemEntity> currentItems = itemRepository.findAllByPlanIdAndDeletedFalseOrderByCreatedAtAsc(planId);
        currentItems.forEach(item -> targetRepository.deleteAllByPlanItemId(item.getId()));
        targetRepository.flush();
        itemRepository.deleteAll(currentItems);
        itemRepository.flush();

        restoreFieldDefinitions(plan.getMeetingId(), snapshot, currentParticipants);
        LinkedHashMap<String,String> participantIdsBySnapshotId = restoreParticipants(
                plan.getMeetingId(),
                participantsIncludingDeleted,
                snapshot
        );

        int restoredItems = 0;
        for (PlanItemView source : snapshot.items()) {
            String participantId = source.participantId() == null
                    ? null
                    : participantIdsBySnapshotId.get(source.participantId());
            if (source.participantId() != null && participantId == null) {
                continue;
            }
            List<String> validTargets = source.targetElementIds().stream().filter(elementIds::contains).toList();
            if (validTargets.isEmpty()) {
                continue;
            }
            PlanItemEntity item = new PlanItemEntity();
            item.setPlanId(planId);
            item.setItemType(PlanItemType.valueOf(source.type()));
            item.setParticipantId(participantId);
            item.setLabel(source.label());
            item.setLocked(source.locked());
            item.setBackgroundColor(source.backgroundColor());
            item.setTextColor(source.textColor());
            item.setBold(source.bold());
            itemRepository.save(item);
            for (String elementId : validTargets) {
                PlanItemTargetEntity target = new PlanItemTargetEntity();
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
        SeatingPlanEntity plan = meetingAccessService.requireOwnedPlan(planId);
        PlanVersionEntity version = versionRepository.findById(versionId)
                .filter(value -> !value.isDeleted() && value.getPlanId().equals(planId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "方案版本不存在"));
        WorkspaceResponse snapshot = readSnapshot(version);
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
        meetingAccessService.requireOwnedMeeting(meetingId);
        SeatingPlanEntity plan = planRepository.findFirstByMeetingIdAndDeletedFalseOrderByCreatedAtAsc(meetingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会议尚未建立排座方案"));
        return getSnapshot(plan.getId(), versionId);
    }

    private WorkspaceResponse readSnapshot(PlanVersionEntity version) {
        try {
            JsonNode snapshotNode = objectMapper.readTree(version.getSnapshotJson());
            adaptLegacySnapshot(snapshotNode);
            return objectMapper.readValue(
                    objectMapper.writeValueAsString(snapshotNode),
                    WorkspaceResponse.class
            );
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取方案快照失败");
        }
    }

    private void adaptLegacySnapshot(JsonNode snapshotNode) {
        if (!(snapshotNode instanceof ObjectNode root)
                || !root.path("participants").isArray()
                || !hasLegacyParticipants(root.path("participants"))) {
            return;
        }
        List<LegacyField> legacyFields = legacyFields(root);
        root.set("fieldDefinitions", legacyFieldDefinitions(legacyFields));
        for (JsonNode participantNode : root.path("participants")) {
            if (!(participantNode instanceof ObjectNode participant)) {
                continue;
            }
            JsonNode attributesNode = participant.path("attributes");
            LinkedHashMap<String,String> record = new LinkedHashMap<String, String>();
            for (LegacyField field : legacyFields) {
                String value = text(attributesNode.path(field.sourceCode()));
                if (value.isBlank()) {
                    value = text(attributesNode.path(field.label()));
                }
                putNonBlank(
                        record,
                        field.label(),
                        value
                );
            }
            ArrayList<LinkedHashMap<String,String>> records = new ArrayList<LinkedHashMap<String, String>>();
            if (!record.isEmpty()) {
                records.add(record);
            }
            writeLegacyParticipantRecords(participant, legacyFields, records);
        }
    }

    private boolean hasLegacyParticipants(JsonNode participants) {
        for (JsonNode participant : participants) {
            if (!participant.has("records") && participant.path("attributes").isObject()) {
                return true;
            }
        }
        return false;
    }

    private List<LegacyField> legacyFields(
            ObjectNode root
    ) {
        ArrayList<LegacyField> fields = new ArrayList<LegacyField>();
        JsonNode definitions = root.path("fieldDefinitions");
        if (definitions.isArray() && !definitions.isEmpty()) {
            for (JsonNode definition : definitions) {
                String code = text(definition.path("code"));
                if ("name".equals(code) || "employeeNo".equals(code)) {
                    continue;
                }
                String label = text(definition.path("label"));
                addLegacyField(fields, code, label.isBlank() ? code : label);
            }
        }
        for (JsonNode participant : root.path("participants")) {
            JsonNode attributes = participant.path("attributes");
            if (!attributes.isObject()) {
                continue;
            }
            for (Entry<String,JsonNode> entry : attributes.properties()) {
                addLegacyField(fields, entry.getKey(), entry.getKey());
            }
        }
        return fields;
    }

    private void addLegacyField(
            List<LegacyField> fields,
            String sourceCode,
            String label
    ) {
        if (sourceCode == null || sourceCode.isBlank() || label == null || label.isBlank()
                || fields.stream().anyMatch(value -> normalize(value.label())
                .equals(normalize(label)))) {
            return;
        }
        fields.add(new LegacyField(sourceCode, label));
    }

    private ArrayNode legacyFieldDefinitions(
            List<LegacyField> legacyFields
    ) {
        ArrayNode definitions = objectMapper.createArrayNode();
        definitions.add(fieldDefinitionNode("name", "姓名", false, true));
        definitions.add(fieldDefinitionNode("employeeNo", "工号", false, false));
        legacyFields.forEach(field -> definitions.add(
                fieldDefinitionNode(field.label(), field.label(), true, false)
        ));
        return definitions;
    }

    private ObjectNode fieldDefinitionNode(
            String code,
            String label,
            boolean filterable,
            boolean cardVisible
    ) {
        ObjectNode field = objectMapper.createObjectNode();
        field.put("code", code);
        field.put("label", label);
        field.put("type", "TEXT");
        field.put("searchable", true);
        field.put("filterable", filterable);
        field.put("sortable", true);
        field.put("cardVisible", cardVisible);
        return field;
    }

    private void writeLegacyParticipantRecords(
            ObjectNode participant,
            List<LegacyField> fields,
            List<LinkedHashMap<String, String>> records
    ) {
        ObjectNode primaryAttributes = objectMapper.createObjectNode();
        ObjectNode attributeValues = objectMapper.createObjectNode();
        for (LegacyField field : fields) {
            List<String> values = records.stream()
                    .map(record -> record.get(field.label()))
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .toList();
            if (values.isEmpty()) {
                continue;
            }
            primaryAttributes.put(field.label(), values.getFirst());
            ArrayNode valueArray = objectMapper.createArrayNode();
            values.forEach(valueArray::add);
            attributeValues.set(field.label(), valueArray);
        }
        ArrayNode recordNodes = objectMapper.createArrayNode();
        String participantId = text(participant.path("id"));
        for (int index = 0; index < records.size(); index++) {
            ObjectNode recordNode = objectMapper.createObjectNode();
            recordNode.put("id", "legacy-" + participantId + "-" + (index + 1));
            recordNode.put("recordOrder", index + 1);
            ObjectNode attributes = objectMapper.createObjectNode();
            records.get(index).forEach(attributes::put);
            recordNode.set("attributes", attributes);
            recordNodes.add(recordNode);
        }
        participant.set("primaryAttributes", primaryAttributes);
        participant.set("attributeValues", attributeValues);
        participant.set("records", recordNodes);
    }

    private void putNonBlank(
            Map<String, String> attributes,
            String fieldName,
            String value
    ) {
        if (value != null && !value.isBlank()) {
            attributes.put(fieldName, value);
        }
    }

    private String text(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode()
                ? ""
                : value.asText();
    }

    private void restoreFieldDefinitions(
            String meetingId,
            WorkspaceResponse snapshot,
            List<ParticipantEntity> currentParticipants
    ) {
        if (snapshot.fieldDefinitions() == null) {
            return;
        }
        List<MeetingParticipantFieldEntity> currentFields = fieldRepository
                .findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(meetingId);
        Set<String> snapshotEmployeeNumbers = snapshot.participants() == null
                ? Set.<String>of()
                : snapshot.participants().stream()
                        .map(value -> normalize(value.employeeNo()))
                        .collect(Collectors.toSet());
        List<String> laterParticipantIds = currentParticipants.stream()
                .filter(value -> !snapshotEmployeeNumbers.contains(
                        normalize(value.getEmployeeNo())
                ))
                .map(ParticipantEntity::getId)
                .toList();
        LinkedHashSet<String> laterFieldNames = new LinkedHashSet<String>();
        if (!laterParticipantIds.isEmpty()) {
            recordRepository
                    .findAllByParticipantIdInAndDeletedFalseOrderByParticipantIdAscRecordOrderAsc(
                            laterParticipantIds
                    )
                    .forEach(record -> readRecordAttributes(record.getAttributesJson())
                            .keySet()
                            .forEach(value -> laterFieldNames.add(normalize(value))));
        }
        fieldRepository.deleteAll(currentFields);
        fieldRepository.flush();

        LinkedHashSet<String> restoredNames = new LinkedHashSet<String>();
        int sortOrder = 0;
        for (FieldDefinitionView source : snapshot.fieldDefinitions()) {
            if ("name".equals(source.code()) || "employeeNo".equals(source.code())) {
                continue;
            }
            String fieldName = source.label() == null || source.label().isBlank()
                    ? source.code()
                    : source.label();
            if (fieldName == null || fieldName.isBlank()
                    || !restoredNames.add(normalize(fieldName))) {
                continue;
            }
            MeetingParticipantFieldEntity field = new MeetingParticipantFieldEntity();
            field.setMeetingId(meetingId);
            field.setFieldName(fieldName);
            field.setSortOrder(++sortOrder);
            fieldRepository.save(field);
        }
        for (MeetingParticipantFieldEntity source : currentFields) {
            if (!laterFieldNames.contains(normalize(source.getFieldName()))
                    || !restoredNames.add(normalize(source.getFieldName()))) {
                continue;
            }
            MeetingParticipantFieldEntity field = new MeetingParticipantFieldEntity();
            field.setMeetingId(meetingId);
            field.setFieldName(source.getFieldName());
            field.setSortOrder(++sortOrder);
            fieldRepository.save(field);
        }
        fieldRepository.flush();
    }

    private Map<String, String> readRecordAttributes(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<Map<String, String>>() {
                    }
            );
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "读取人员动态记录失败"
            );
        }
    }

    private LinkedHashMap<String, String> restoreParticipants(
            String meetingId,
            List<ParticipantEntity> currentParticipants,
            WorkspaceResponse snapshot
    ) {
        LinkedHashMap<String,ParticipantEntity> participantByEmployeeNo = new LinkedHashMap<String, ParticipantEntity>();
        currentParticipants.forEach(participant -> participantByEmployeeNo.putIfAbsent(
                normalize(participant.getEmployeeNo()),
                participant
        ));
        LinkedHashMap<String,String> participantIdsBySnapshotId = new LinkedHashMap<String, String>();
        ArrayList<ParticipantEntity> restoredParticipants = new ArrayList<ParticipantEntity>();
        ArrayList<ParticipantRecordEntity> recordsToDelete = new ArrayList<ParticipantRecordEntity>();
        List<ParticipantView> snapshotParticipants = snapshot.participants() == null
                ? List.<WorkspaceResponse.ParticipantView>of()
                : snapshot.participants();
        for (ParticipantView source : snapshotParticipants) {
            ParticipantEntity participant = participantByEmployeeNo.get(normalize(source.employeeNo()));
            if (participant == null) {
                participant = new ParticipantEntity();
                participant.setMeetingId(meetingId);
                participant.setEmployeeNo(source.employeeNo());
                participantByEmployeeNo.put(normalize(source.employeeNo()), participant);
            }
            participant.setName(source.name());
            participant.setAttendanceStatus(attendanceStatus(source.attendanceStatus()));
            participant.setDeleted(false);
            restoredParticipants.add(participant);
            if (participant.getId() != null && source.records() != null) {
                recordsToDelete.addAll(recordRepository
                        .findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(
                                participant.getId()
                        ));
            }
        }
        participantRepository.saveAll(restoredParticipants);
        participantRepository.flush();
        restoredParticipants.forEach(participant -> {
            ParticipantView source = snapshotParticipants.stream()
                    .filter(value -> normalize(value.employeeNo())
                            .equals(normalize(participant.getEmployeeNo())))
                    .findFirst()
                    .orElseThrow();
            participantIdsBySnapshotId.put(source.id(), participant.getId());
        });

        recordRepository.deleteAll(recordsToDelete);
        recordRepository.flush();
        for (ParticipantView source : snapshotParticipants) {
            if (source.records() == null) {
                continue;
            }
            String participantId = participantIdsBySnapshotId.get(source.id());
            int recordOrder = 0;
            for (ParticipantRecordView sourceRecord : source.records().stream()
                    .sorted(Comparator.comparingInt(
                            WorkspaceResponse.ParticipantRecordView::recordOrder
                    ))
                    .toList()) {
                ParticipantRecordEntity record = new ParticipantRecordEntity();
                record.setParticipantId(participantId);
                record.setRecordOrder(++recordOrder);
                try {
                    record.setAttributesJson(objectMapper.writeValueAsString(
                            sourceRecord.attributes() == null
                                    ? Map.of()
                                    : sourceRecord.attributes()
                    ));
                } catch (Exception exception) {
                    throw new ApiException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "恢复人员动态记录失败"
                    );
                }
                recordRepository.save(record);
            }
        }
        recordRepository.flush();
        return participantIdsBySnapshotId;
    }

    private AttendanceStatus attendanceStatus(String value) {
        if (value == null || value.isBlank()) {
            return AttendanceStatus.PRESENT;
        }
        try {
            return AttendanceStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return AttendanceStatus.PRESENT;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record LegacyField(String sourceCode, String label) {
    }

}
