package com.company.meetinghelper.participant.service;

import static java.util.Locale.ROOT;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.meeting.service.MeetingAccessService;
import com.company.meetinghelper.participant.api.dto.request.CreateParticipantRequest;
import com.company.meetinghelper.participant.api.dto.request.ParticipantRecordInput;
import com.company.meetinghelper.participant.api.dto.request.UpdateAttendanceRequest;
import com.company.meetinghelper.participant.api.dto.request.UpdateParticipantRequest;
import com.company.meetinghelper.participant.api.dto.response.ParticipantResult;
import com.company.meetinghelper.participant.entity.AttendanceStatus;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import com.company.meetinghelper.participant.entity.ParticipantRecordEntity;
import com.company.meetinghelper.participant.repository.ParticipantRecordRepository;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import com.company.meetinghelper.seating.api.dto.request.AssignmentRequest;
import com.company.meetinghelper.seating.entity.PlanItemType;
import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import com.company.meetinghelper.seating.repository.PlanItemRepository;
import com.company.meetinghelper.seating.repository.PlanItemTargetRepository;
import com.company.meetinghelper.seating.repository.SeatingPlanRepository;
import com.company.meetinghelper.seating.service.SeatingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Represents the participant service class.
 */
@Service
public class ParticipantService {
    private final MeetingAccessService meetingAccessService;
    private final ParticipantRepository participantRepository;
    private final ParticipantFieldRegistrationService fieldRegistrationService;
    private final ParticipantRecordRepository recordRepository;
    private final SeatingPlanRepository planRepository;
    private final PlanItemRepository itemRepository;
    private final PlanItemTargetRepository targetRepository;
    private final SeatingService seatingService;
    private final ParticipantRecordMerger recordMerger;
    private final ObjectMapper objectMapper;

    public ParticipantService(
            MeetingAccessService meetingAccessService,
            ParticipantRepository participantRepository,
            ParticipantFieldRegistrationService fieldRegistrationService,
            ParticipantRecordRepository recordRepository,
            SeatingPlanRepository planRepository,
            PlanItemRepository itemRepository,
            PlanItemTargetRepository targetRepository,
            SeatingService seatingService,
            ParticipantRecordMerger recordMerger,
            ObjectMapper objectMapper
    ) {
        this.meetingAccessService = meetingAccessService;
        this.participantRepository = participantRepository;
        this.fieldRegistrationService = fieldRegistrationService;
        this.recordRepository = recordRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
        this.seatingService = seatingService;
        this.recordMerger = recordMerger;
        this.objectMapper = objectMapper;
    }

/**
 * Handles create.
 *
 * @param meetingId meeting id
 * @param request request
 * @return result
 */
    @Transactional
    public ParticipantResult create(String meetingId, CreateParticipantRequest request) {
        meetingAccessService.requireOwnedMeeting(meetingId);
        CreateParticipantContext context = createParticipantContext(meetingId, request);
        ParticipantEntity existing = participantRepository
                .findByMeetingIdAndEmployeeNoIgnoreCase(meetingId, context.employeeNo())
                .orElse(null);
        if (existing != null) {
            return createExistingParticipantResult(meetingId, existing, context, request.targetElementId());
        }

        ParticipantEntity participant = saveNewParticipant(meetingId, context);
        saveInitialRecord(participant.getId(), context.attributes());
        boolean assigned = assignTargetElementIfPresent(
                meetingId,
                participant.getId(),
                request.targetElementId()
        );
        return createdParticipantResult(participant, assigned);
    }

    private CreateParticipantContext createParticipantContext(
            String meetingId,
            CreateParticipantRequest request
    ) {
        Map<String,String> incomingAttributes = request.attributes();
        Map<String,String> canonicalNames = fieldRegistrationService.registerFields(
                meetingId,
                incomingAttributes == null ? List.of() : incomingAttributes.keySet()
        );
        String employeeNo = request.employeeNo().trim();
        String name = request.name().trim();
        Map<String,String> attributes = canonicalAttributes(incomingAttributes, canonicalNames);
        return new CreateParticipantContext(employeeNo, name, attributes);
    }

    private ParticipantResult createExistingParticipantResult(
            String meetingId,
            ParticipantEntity existing,
            CreateParticipantContext context,
            String targetElementId
    ) {
        if (!existing.getName().equals(context.name())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "工号" + context.employeeNo() + "已对应人员" + existing.getName()
            );
        }
        return updateExistingParticipantRecord(
                meetingId,
                existing,
                context.attributes(),
                targetElementId
        );
    }

    private ParticipantEntity saveNewParticipant(
            String meetingId,
            CreateParticipantContext context
    ) {
        ParticipantEntity participant = new ParticipantEntity();
        participant.setMeetingId(meetingId);
        participant.setEmployeeNo(context.employeeNo());
        participant.setName(context.name());
        try {
            participantRepository.saveAndFlush(participant);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "该工号已在会议名单中");
        }
        return participant;
    }

    private void saveInitialRecord(String participantId, Map<String, String> attributes) {
        if (!attributes.isEmpty()) {
            ParticipantRecordEntity record = new ParticipantRecordEntity();
            record.setParticipantId(participantId);
            record.setRecordOrder(1);
            record.setAttributesJson(writeAttributes(attributes));
            recordRepository.save(record);
        }
    }

    private ParticipantResult createdParticipantResult(
            ParticipantEntity participant,
            boolean assigned
    ) {
        return new ParticipantResult(
                participant.getId(),
                participant.getEmployeeNo(),
                participant.getName(),
                "CREATED",
                assigned ? "人员已新增，并安排到所选座位" : "人员已新增"
        );
    }

/**
 * Handles update.
 *
 * @param meetingId meeting id
 * @param participantId participant id
 * @param request request
 * @return result
 */
    @Transactional
    public ParticipantResult update(
            String meetingId,
            String participantId,
            UpdateParticipantRequest request
    ) {
        meetingAccessService.requireOwnedMeeting(meetingId);
        ParticipantEntity participant = participantRepository.findByIdAndMeetingId(participantId, meetingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "人员不存在"));
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "姓名不能为空");
        }
        List<ParticipantRecordInput> records = request.records() == null
                ? List.of()
                : request.records();
        List<String> explicitFieldNames = request.fieldNames() == null
                ? List.of()
                : request.fieldNames();
        List<String> fieldNames = Stream.concat(
                        explicitFieldNames.stream(),
                        records.stream().flatMap(record -> recordAttributes(record).keySet().stream())
                )
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        Map<String,String> canonicalNames = fieldRegistrationService.registerFields(meetingId, fieldNames);

        participant.setName(name);
        participantRepository.save(participant);
        recordRepository.deleteAll(
                recordRepository.findAllByParticipantIdOrderByRecordOrderAsc(participantId)
        );

        int order = 0;
        for (ParticipantRecordInput source : records) {
            Map<String,String> attributes = canonicalAttributesForUpdate(
                    recordAttributes(source),
                    canonicalNames
            );
            if (attributes.isEmpty()) {
                continue;
            }
            ParticipantRecordEntity record = new ParticipantRecordEntity();
            record.setParticipantId(participantId);
            record.setRecordOrder(++order);
            record.setAttributesJson(writeAttributes(attributes));
            recordRepository.save(record);
        }
        return new ParticipantResult(participant.getId(), participant.getEmployeeNo(), participant.getName());
    }

/**
 * Handles update attendance.
 *
 * @param meetingId meeting id
 * @param participantId participant id
 * @param request request
 */
    @Transactional
    public void updateAttendance(
            String meetingId,
            String participantId,
            UpdateAttendanceRequest request
    ) {
        meetingAccessService.requireOwnedMeeting(meetingId);
        ParticipantEntity participant = participantRepository.findById(participantId)
                .filter(value -> value.getMeetingId().equals(meetingId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "人员不存在"));
        participant.setAttendanceStatus(request.attendanceStatus());
        if (request.attendanceStatus() == AttendanceStatus.TEMPORARILY_ABSENT) {
            removeAssignment(meetingId, participantId);
        }
        participantRepository.save(participant);
    }

/**
 * Handles delete.
 *
 * @param meetingId meeting id
 * @param participantId participant id
 */
    @Transactional
    public void delete(String meetingId, String participantId) {
        meetingAccessService.requireOwnedMeeting(meetingId);
        ParticipantEntity participant = participantRepository.findById(participantId)
                .filter(value -> value.getMeetingId().equals(meetingId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "人员不存在"));
        removeAssignment(meetingId, participantId);
        recordRepository.deleteAll(
                recordRepository.findAllByParticipantIdOrderByRecordOrderAsc(participantId)
        );
        participantRepository.delete(participant);
    }

    private ParticipantResult updateExistingParticipantRecord(
            String meetingId,
            ParticipantEntity participant,
            Map<String,String> attributes,
            String targetElementId
    ) {
        ParticipantRecordMerger.Action action = ParticipantRecordMerger.Action.SKIP;
        if (!attributes.isEmpty()) {
            List<ParticipantRecordEntity> records = recordRepository
                    .findAllByParticipantIdOrderByRecordOrderAsc(participant.getId());
            ParticipantRecordMerger.MergeDecision decision = recordMerger.decide(
                    attributes,
                    mergerValues(records)
            );
            action = decision.action();
            switch (decision.action()) {
                case SKIP -> {
                    // Same dynamic record already exists. Keep data unchanged and tell the user.
                }
                case MERGE -> {
                    ParticipantRecordEntity target = records.stream()
                            .filter(record -> record.getId().equals(decision.targetRecordId()))
                            .findFirst()
                            .orElseThrow(() -> new ApiException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    "待合并人员记录不存在"
                            ));
                    target.setAttributesJson(writeAttributes(decision.mergedAttributes()));
                    recordRepository.save(target);
                }
                case APPEND -> {
                    ParticipantRecordEntity record = new ParticipantRecordEntity();
                    record.setParticipantId(participant.getId());
                    record.setRecordOrder(records.stream()
                            .mapToInt(ParticipantRecordEntity::getRecordOrder)
                            .max()
                            .orElse(0) + 1);
                    record.setAttributesJson(writeAttributes(decision.mergedAttributes()));
                    recordRepository.save(record);
                }
                default -> throw new IllegalStateException("Unsupported merge action: " + decision.action());
            }
        }
        boolean assigned = assignTargetElementIfPresent(
                meetingId,
                participant.getId(),
                targetElementId
        );
        return new ParticipantResult(
                participant.getId(),
                participant.getEmployeeNo(),
                participant.getName(),
                existingParticipantActionCode(action),
                existingParticipantMessage(action, assigned)
        );
    }

    private String existingParticipantActionCode(ParticipantRecordMerger.Action action) {
        return switch (action) {
            case SKIP -> "SKIPPED";
            case MERGE -> "MERGED";
            case APPEND -> "APPENDED";
            default -> throw new IllegalStateException("Unsupported merge action: " + action);
        };
    }

    private String existingParticipantMessage(
            ParticipantRecordMerger.Action action,
            boolean assigned
    ) {
        String message = switch (action) {
            case SKIP -> "该人员记录已存在，未重复添加";
            case MERGE -> "已合并到已有人员记录";
            case APPEND -> "已为已有人员追加一条记录";
            default -> throw new IllegalStateException("Unsupported merge action: " + action);
        };
        return assigned ? message + "，并安排到所选座位" : message;
    }

    private boolean assignTargetElementIfPresent(
            String meetingId,
            String participantId,
            String targetElementId
    ) {
        if (targetElementId == null || targetElementId.isBlank()) {
            return false;
        }
        SeatingPlanEntity plan = planRepository.findFirstByMeetingIdOrderByCreatedAtAsc(meetingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "排座方案不存在"));
        seatingService.assign(plan.getId(), new AssignmentRequest(participantId, targetElementId));
        return true;
    }

    private void removeAssignment(String meetingId, String participantId) {
        SeatingPlanEntity plan = planRepository.findFirstByMeetingIdOrderByCreatedAtAsc(meetingId).orElse(null);
        if (plan == null) {
            return;
        }
        itemRepository.findByPlanIdAndParticipantIdAndItemType(
                plan.getId(), participantId, PlanItemType.PERSON).ifPresent(item -> {
            targetRepository.deleteAllByPlanItemId(item.getId());
            itemRepository.delete(item);
        });
    }

    private List<ParticipantRecordMerger.RecordValue> mergerValues(
            List<ParticipantRecordEntity> records
    ) {
        return records.stream()
                .map(record -> new ParticipantRecordMerger.RecordValue(
                        record.getId(),
                        record.getRecordOrder(),
                        readAttributes(record.getAttributesJson())
                ))
                .toList();
    }

    private Map<String, String> canonicalAttributes(
            Map<String, String> incomingAttributes,
            Map<String, String> canonicalNames
    ) {
        if (incomingAttributes == null || incomingAttributes.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String,String> attributes = new LinkedHashMap<String, String>();
        for (Entry<String,String> entry : incomingAttributes.entrySet()) {
            String fieldName = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue();
            if (fieldName.isBlank() || value == null || value.isBlank()) {
                continue;
            }
            String canonicalName = canonicalNames.get(fieldName.toLowerCase(ROOT));
            attributes.put(canonicalName, value.trim());
        }
        return attributes;
    }

    private Map<String, String> canonicalAttributesForUpdate(
            Map<String, String> incomingAttributes,
            Map<String, String> canonicalNames
    ) {
        if (incomingAttributes == null || incomingAttributes.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String,String> attributes = new LinkedHashMap<String, String>();
        for (Entry<String,String> entry : incomingAttributes.entrySet()) {
            String fieldName = entry.getKey() == null ? "" : entry.getKey().trim();
            if (fieldName.isBlank()) {
                continue;
            }
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "请填写该人员在新增列中的值");
            }
            String canonicalName = canonicalNames.get(fieldName.toLowerCase(ROOT));
            attributes.put(canonicalName, value.trim());
        }
        return attributes;
    }

    private Map<String, String> recordAttributes(ParticipantRecordInput record) {
        return record == null || record.attributes() == null ? Map.of() : record.attributes();
    }

    private Map<String, String> readAttributes(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "人员动态记录格式不正确");
        }
    }

    private String writeAttributes(Map<String, String> attributes) {
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "人员动态记录无法保存");
        }
    }

    private record CreateParticipantContext(
            String employeeNo,
            String name,
            Map<String, String> attributes
    ) {
    }
}
