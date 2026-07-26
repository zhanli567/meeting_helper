package com.company.meetinghelper.importing.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.importing.api.dto.response.CommitResult;
import com.company.meetinghelper.importing.api.dto.response.ImportPreview;
import com.company.meetinghelper.importing.api.dto.response.ParticipantRow;
import com.company.meetinghelper.importing.repository.ImportPreviewStore;
import com.company.meetinghelper.importing.service.model.ParsedParticipantRow;
import com.company.meetinghelper.importing.service.model.ParsedParticipantWorkbook;
import com.company.meetinghelper.meeting.service.MeetingAccessService;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import com.company.meetinghelper.participant.entity.ParticipantRecordEntity;
import com.company.meetinghelper.participant.repository.MeetingParticipantFieldRepository;
import com.company.meetinghelper.participant.repository.ParticipantRecordRepository;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import com.company.meetinghelper.participant.service.ParticipantFieldRegistrationService;
import com.company.meetinghelper.participant.service.ParticipantRecordMerger;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ImportService {
    private final ParticipantWorkbookParser workbookParser;
    private final MeetingAccessService meetingAccessService;
    private final ParticipantRepository participantRepository;
    private final MeetingParticipantFieldRepository fieldRepository;
    private final ParticipantFieldRegistrationService fieldRegistrationService;
    private final ParticipantRecordRepository recordRepository;
    private final ParticipantRecordMerger recordMerger;
    private final ImportPreviewStore previewStore;
    private final ObjectMapper objectMapper;

    /**
     * 创建通用人员导入服务。
     *
     * @param workbookParser 通用人员工作簿解析器
     * @param meetingAccessService 会议归属校验服务
     * @param participantRepository 参会人员仓储
     * @param fieldRepository 会议人员字段仓储
     * @param fieldRegistrationService 人员动态字段注册服务
     * @param recordRepository 人员动态记录仓储
     * @param recordMerger 人员动态记录合并器
     * @param previewStore 导入预览存储
     * @param objectMapper JSON序列化器
     */
    public ImportService(
            ParticipantWorkbookParser workbookParser,
            MeetingAccessService meetingAccessService,
            ParticipantRepository participantRepository,
            MeetingParticipantFieldRepository fieldRepository,
            ParticipantFieldRegistrationService fieldRegistrationService,
            ParticipantRecordRepository recordRepository,
            ParticipantRecordMerger recordMerger,
            ImportPreviewStore previewStore,
            ObjectMapper objectMapper
    ) {
        this.workbookParser = workbookParser;
        this.meetingAccessService = meetingAccessService;
        this.participantRepository = participantRepository;
        this.fieldRepository = fieldRepository;
        this.fieldRegistrationService = fieldRegistrationService;
        this.recordRepository = recordRepository;
        this.recordMerger = recordMerger;
        this.previewStore = previewStore;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成单一通用人员导入模板。
     *
     * @return Excel模板字节
     */
    public byte[] template() {
        try (var workbook = workbookParser.createTemplate();
             var output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "生成导入模板失败");
        }
    }

    /**
     * 解析通用人员工作簿并生成可提交预览。
     *
     * @param meetingId 会议ID
     * @param file 上传的Excel文件
     * @return 导入预览
     */
    public ImportPreview preview(String meetingId, MultipartFile file) {
        meetingAccessService.requireOwnedMeeting(meetingId);
        ParsedParticipantWorkbook parsed;
        try (var workbook = new XSSFWorkbook(file.getInputStream())) {
            parsed = workbookParser.parse(workbook);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Excel文件无法读取，请使用系统下载的模板");
        }

        var registeredFields = fieldRepository
                .findAllByMeetingIdAndDeletedFalseOrderBySortOrderAsc(meetingId);
        var canonicalFieldNames = new LinkedHashMap<String, String>();
        registeredFields.forEach(field -> canonicalFieldNames.put(
                normalize(field.getFieldName()),
                field.getFieldName()
        ));
        var newFields = parsed.fieldNames().stream()
                .filter(field -> !canonicalFieldNames.containsKey(normalize(field)))
                .toList();
        var existingFields = parsed.fieldNames().stream()
                .filter(field -> canonicalFieldNames.containsKey(normalize(field)))
                .map(field -> canonicalFieldNames.get(normalize(field)))
                .toList();
        parsed.fieldNames().forEach(field -> canonicalFieldNames.putIfAbsent(
                normalize(field),
                field
        ));
        var previewErrors = new ArrayList<>(parsed.errors());
        var rows = previewRows(
                meetingId,
                parsed.rows(),
                canonicalFieldNames,
                previewErrors
        );
        var participantCount = (int) parsed.rows().stream()
                .map(row -> normalize(row.employeeNo()))
                .distinct()
                .count();
        var token = UUID.randomUUID().toString();
        var preview = new ImportPreview(
                token,
                parsed.totalRows(),
                parsed.rows().size(),
                parsed.ignoredDuplicateRows(),
                participantCount,
                parsed.rows().size(),
                newFields,
                existingFields,
                rows,
                List.copyOf(previewErrors)
        );
        previewStore.put(token, new ImportPreviewStore.StoredPreview(
                meetingId,
                parsed,
                preview,
                OffsetDateTime.now(ZoneOffset.UTC)
        ));
        return preview;
    }

    /**
     * 在单一事务中提交预览对应的人员、字段和动态记录。
     *
     * @param meetingId 会议ID
     * @param token 预览令牌
     * @return 新增、合并、追加和跳过统计
     */
    @Transactional
    public CommitResult commit(String meetingId, String token) {
        meetingAccessService.requireOwnedMeeting(meetingId);
        var stored = previewStore.remove(token, meetingId);
        if (stored == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "导入预览已过期，请重新上传");
        }
        if (stored.workbook().employeeNameConflict()) {
            throw new ApiException(HttpStatus.CONFLICT, "同一工号存在不同姓名，无法提交");
        }
        if (!stored.workbook().errors().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "导入预览包含错误，无法提交");
        }
        var canonicalFieldNames = fieldRegistrationService.registerFields(
                meetingId,
                stored.workbook().fieldNames()
        );
        validateParticipantNames(meetingId, stored.workbook().rows());

        var participantByEmployeeNo = new LinkedHashMap<String, ParticipantEntity>();
        var newParticipants = 0;
        for (var row : stored.workbook().rows()) {
            var employeeKey = normalize(row.employeeNo());
            if (participantByEmployeeNo.containsKey(employeeKey)) {
                continue;
            }
            var existing = participantRepository
                    .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, row.employeeNo())
                    .orElse(null);
            if (existing != null && !existing.getName().equals(row.name())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "工号" + row.employeeNo() + "已对应人员" + existing.getName()
                );
            }
            var participant = existing;
            if (participant == null) {
                participant = new ParticipantEntity();
                participant.setMeetingId(meetingId);
                participant.setEmployeeNo(row.employeeNo());
                participant.setName(row.name());
                participantRepository.save(participant);
                newParticipants++;
            }
            participantByEmployeeNo.put(employeeKey, participant);
        }

        var recordsByParticipant = loadRecords(participantByEmployeeNo.values().stream()
                .map(ParticipantEntity::getId)
                .toList());
        var mergedRecords = 0;
        var appendedRecords = 0;
        var skippedRecords = 0;
        for (var row : stored.workbook().rows()) {
            var participant = participantByEmployeeNo.get(normalize(row.employeeNo()));
            var incomingAttributes = canonicalAttributes(
                    row.attributes(),
                    canonicalFieldNames
            );
            var records = recordsByParticipant.computeIfAbsent(
                    participant.getId(),
                    ignored -> new ArrayList<>()
            );
            var decision = recordMerger.decide(incomingAttributes, mergerValues(records));
            switch (decision.action()) {
                case SKIP -> skippedRecords++;
                case MERGE -> {
                    var target = records.stream()
                            .filter(record -> record.getId().equals(decision.targetRecordId()))
                            .findFirst()
                            .orElseThrow(() -> new ApiException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    "待合并人员记录不存在"
                            ));
                    target.setAttributesJson(writeAttributes(decision.mergedAttributes()));
                    recordRepository.save(target);
                    mergedRecords++;
                }
                case APPEND -> {
                    var record = new ParticipantRecordEntity();
                    record.setParticipantId(participant.getId());
                    record.setRecordOrder(records.stream()
                            .mapToInt(ParticipantRecordEntity::getRecordOrder)
                            .max()
                            .orElse(0) + 1);
                    record.setAttributesJson(writeAttributes(decision.mergedAttributes()));
                    recordRepository.save(record);
                    records.add(record);
                    appendedRecords++;
                }
            }
        }
        return new CommitResult(
                newParticipants,
                mergedRecords,
                appendedRecords,
                skippedRecords
        );
    }

    private List<ParticipantRow> previewRows(
            String meetingId,
            List<ParsedParticipantRow> parsedRows,
            Map<String, String> canonicalFieldNames,
            List<String> previewErrors
    ) {
        var workingParticipants = new LinkedHashMap<String, PreviewParticipant>();
        var previewRows = new ArrayList<ParticipantRow>();
        for (var row : parsedRows) {
            var employeeKey = normalize(row.employeeNo());
            var state = workingParticipants.get(employeeKey);
            var firstIncomingRow = state == null;
            if (state == null) {
                var existing = participantRepository
                        .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(
                                meetingId,
                                row.employeeNo()
                        )
                        .orElse(null);
                if (existing == null) {
                    state = new PreviewParticipant(
                            row.name(),
                            true,
                            new ArrayList<>()
                    );
                } else {
                    var records = recordRepository
                            .findAllByParticipantIdAndDeletedFalseOrderByRecordOrderAsc(
                                    existing.getId()
                            );
                    state = new PreviewParticipant(
                            existing.getName(),
                            false,
                            new ArrayList<>(mergerValues(records))
                    );
                }
                workingParticipants.put(employeeKey, state);
            }

            var attributes = canonicalAttributes(row.attributes(), canonicalFieldNames);
            String expectedAction;
            if (!state.name().equals(row.name())) {
                expectedAction = "姓名冲突，提交将被阻止";
                var error = participantNameConflict(row.employeeNo(), state.name());
                if (!previewErrors.contains(error)) {
                    previewErrors.add(error);
                }
            } else {
                var decision = recordMerger.decide(attributes, state.records());
                expectedAction = expectedAction(
                        decision.action(),
                        state.newParticipant() && firstIncomingRow
                );
                applyPreviewDecision(state.records(), decision, row.sourceRow());
            }
            previewRows.add(new ParticipantRow(
                    row.sourceRow(),
                    row.employeeNo(),
                    row.name(),
                    attributes,
                    expectedAction
            ));
        }
        return List.copyOf(previewRows);
    }

    private void validateParticipantNames(
            String meetingId,
            List<ParsedParticipantRow> rows
    ) {
        var checkedEmployeeNumbers = new java.util.LinkedHashSet<String>();
        for (var row : rows) {
            if (!checkedEmployeeNumbers.add(normalize(row.employeeNo()))) {
                continue;
            }
            participantRepository
                    .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(
                            meetingId,
                            row.employeeNo()
                    )
                    .filter(existing -> !existing.getName().equals(row.name()))
                    .ifPresent(existing -> {
                        throw new ApiException(
                                HttpStatus.CONFLICT,
                                participantNameConflict(row.employeeNo(), existing.getName())
                        );
                    });
        }
    }

    private String participantNameConflict(String employeeNo, String existingName) {
        return "工号" + employeeNo + "已对应人员" + existingName;
    }

    private String expectedAction(
            ParticipantRecordMerger.Action action,
            boolean firstRowForNewParticipant
    ) {
        if (firstRowForNewParticipant) {
            return "新增人员并追加记录";
        }
        return switch (action) {
            case SKIP -> "跳过相同记录";
            case MERGE -> "合并至已有记录";
            case APPEND -> "追加冲突记录";
        };
    }

    private void applyPreviewDecision(
            List<ParticipantRecordMerger.RecordValue> records,
            ParticipantRecordMerger.MergeDecision decision,
            int sourceRow
    ) {
        switch (decision.action()) {
            case SKIP -> {
                return;
            }
            case MERGE -> {
                for (var index = 0; index < records.size(); index++) {
                    var record = records.get(index);
                    if (record.recordId().equals(decision.targetRecordId())) {
                        records.set(index, new ParticipantRecordMerger.RecordValue(
                                record.recordId(),
                                record.recordOrder(),
                                decision.mergedAttributes()
                        ));
                        return;
                    }
                }
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "预览待合并人员记录不存在"
                );
            }
            case APPEND -> {
                var nextOrder = records.stream()
                        .mapToInt(ParticipantRecordMerger.RecordValue::recordOrder)
                        .max()
                        .orElse(0) + 1;
                records.add(new ParticipantRecordMerger.RecordValue(
                        "preview-" + sourceRow + "-" + nextOrder,
                        nextOrder,
                        decision.mergedAttributes()
                ));
            }
        }
    }

    private Map<String, String> canonicalAttributes(
            Map<String, String> attributes,
            Map<String, String> canonicalFieldNames
    ) {
        var canonical = new LinkedHashMap<String, String>();
        attributes.forEach((fieldName, value) -> canonical.put(
                canonicalFieldNames.getOrDefault(normalize(fieldName), fieldName),
                value
        ));
        return Map.copyOf(canonical);
    }

    private Map<String, List<ParticipantRecordEntity>> loadRecords(List<String> participantIds) {
        var recordsByParticipant = new LinkedHashMap<String, List<ParticipantRecordEntity>>();
        participantIds.forEach(id -> recordsByParticipant.put(id, new ArrayList<>()));
        if (participantIds.isEmpty()) {
            return recordsByParticipant;
        }
        recordRepository
                .findAllByParticipantIdInAndDeletedFalseOrderByParticipantIdAscRecordOrderAsc(
                        participantIds
                )
                .forEach(record -> recordsByParticipant
                        .computeIfAbsent(record.getParticipantId(), ignored -> new ArrayList<>())
                        .add(record));
        return recordsByParticipant;
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

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record PreviewParticipant(
            String name,
            boolean newParticipant,
            List<ParticipantRecordMerger.RecordValue> records
    ) {
    }
}
