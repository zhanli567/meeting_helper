package com.company.meetinghelper.importing.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.award.entity.AwardRecordEntity;
import com.company.meetinghelper.award.repository.AwardRecordRepository;
import com.company.meetinghelper.importing.api.dto.request.CommitRequest;
import com.company.meetinghelper.importing.api.dto.response.CommitResult;
import com.company.meetinghelper.importing.api.dto.response.DuplicateGroup;
import com.company.meetinghelper.importing.api.dto.response.ImportPreview;
import com.company.meetinghelper.importing.api.dto.response.ParticipantRow;
import com.company.meetinghelper.importing.api.dto.response.TemplateDescriptor;
import com.company.meetinghelper.importing.repository.ImportPreviewStore;
import com.company.meetinghelper.importing.service.model.ParsedWorkbook;
import com.company.meetinghelper.importing.service.strategy.WorkbookImportStrategy;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ImportService {
    private final List<WorkbookImportStrategy> strategies;
    private final MeetingRepository meetingRepository;
    private final ParticipantRepository participantRepository;
    private final AwardRecordRepository awardRepository;
    private final ImportPreviewStore previewStore;
    private final ObjectMapper objectMapper;

    /**
     * 创建人员导入服务。
     *
     * @param strategies 工作簿解析策略
     * @param meetingRepository 会议仓储
     * @param participantRepository 参会人员仓储
     * @param awardRepository 获奖记录仓储
     * @param previewStore 导入预览存储
     * @param objectMapper JSON序列化器
     */
    public ImportService(
            List<WorkbookImportStrategy> strategies,
            MeetingRepository meetingRepository,
            ParticipantRepository participantRepository,
            AwardRecordRepository awardRepository,
            ImportPreviewStore previewStore,
            ObjectMapper objectMapper
    ) {
        this.strategies = strategies;
        this.meetingRepository = meetingRepository;
        this.participantRepository = participantRepository;
        this.awardRepository = awardRepository;
        this.previewStore = previewStore;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询系统支持的导入模板。
     *
     * @return 导入模板列表
     */
    public List<TemplateDescriptor> templates() {
        return strategies.stream().map(WorkbookImportStrategy::descriptor).toList();
    }

    /**
     * 生成指定导入模板的Excel文件。
     *
     * @param templateCode 模板编码
     * @return Excel模板字节
     */
    public byte[] templateFile(String templateCode) {
        var strategy = requireStrategy(templateCode);
        try (var workbook = strategy.createTemplate(); var output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "生成导入模板失败");
        }
    }

    /**
     * 解析上传的Excel并生成重复工号处理预览。
     *
     * @param meetingId 会议ID
     * @param templateCode 模板编码
     * @param file 上传文件
     * @return 导入预览
     */
    public ImportPreview preview(String meetingId, String templateCode, MultipartFile file) {
        meetingRepository.findById(meetingId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会议不存在"));
        var strategy = requireStrategy(templateCode);
        ParsedWorkbook parsed;
        try (var workbook = new XSSFWorkbook(file.getInputStream())) {
            parsed = strategy.parse(workbook);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Excel文件无法读取，请使用系统下载的模板");
        }
        var grouped = parsed.participants().stream()
                .collect(Collectors.groupingBy(
                        ParticipantRow::employeeNo,
                        LinkedHashMap::new,
                        Collectors.toList()));
        var unique = new ArrayList<ParticipantRow>();
        var duplicates = new ArrayList<DuplicateGroup>();
        grouped.forEach((employeeNo, rows) -> {
            if (rows.size() == 1) {
                unique.add(rows.getFirst());
            } else {
                duplicates.add(new DuplicateGroup(employeeNo, rows));
            }
        });
        var participantNumbers = grouped.keySet();
        var errors = new ArrayList<>(parsed.errors());
        parsed.awards().stream()
                .filter(award -> !participantNumbers.contains(award.employeeNo()))
                .forEach(award -> errors.add(
                        "获奖记录第" + award.sourceRow() + "行找不到工号为" + award.employeeNo() + "的参会人员"));

        var token = UUID.randomUUID().toString();
        var preview = new ImportPreview(
                token, templateCode, parsed.participants().size(), parsed.awards().size(),
                unique, duplicates, errors);
        previewStore.put(token, new ImportPreviewStore.StoredPreview(
                meetingId, templateCode, parsed, preview, OffsetDateTime.now(ZoneOffset.UTC)));
        return preview;
    }

    /**
     * 提交预览中确认后的人员和获奖数据。
     *
     * @param meetingId 会议ID
     * @param token 预览令牌
     * @param request 导入确认请求
     * @return 导入统计结果
     */
    @Transactional
    public CommitResult commit(
            String meetingId,
            String token,
            CommitRequest request
    ) {
        var stored = previewStore.remove(token);
        if (stored == null || !stored.meetingId().equals(meetingId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "导入预览已过期，请重新上传");
        }
        var selectedRows = request.selectedSourceRows() == null ? Map.<String, Integer>of() : request.selectedSourceRows();
        var chosen = new ArrayList<>(stored.preview().uniqueParticipants());
        for (var group : stored.preview().duplicateGroups()) {
            var selectedRow = selectedRows.get(group.employeeNo());
            if (selectedRow == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "请选择工号" + group.employeeNo() + "的有效记录");
            }
            var selected = group.candidates().stream()
                    .filter(candidate -> candidate.sourceRow() == selectedRow)
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "重复工号处理结果不正确"));
            chosen.add(selected);
        }

        var inserted = 0;
        var updated = 0;
        var savedByEmployee = new LinkedHashMap<String, ParticipantEntity>();
        for (var row : chosen) {
            var existing = participantRepository
                    .findByMeetingIdAndEmployeeNoIgnoreCaseAndDeletedFalse(meetingId, row.employeeNo())
                    .orElse(null);
            var participant = existing == null ? new ParticipantEntity() : existing;
            if (existing == null) {
                participant.setMeetingId(meetingId);
                participant.setEmployeeNo(row.employeeNo());
                inserted++;
            } else {
                updated++;
            }
            participant.setName(row.name());
            participant.setLevelValue(row.level());
            participant.setDepartment(row.department());
            participant.setParticipantType(row.participantType());
            participant.setTags(row.tags());
            try {
                participant.setCustomAttributesJson(objectMapper.writeValueAsString(row.attributes()));
            } catch (Exception exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "人员扩展字段无法保存");
            }
            participantRepository.save(participant);
            savedByEmployee.put(row.employeeNo(), participant);
        }

        var awardCount = 0;
        if (stored.templateCode().startsWith("AWARD_CEREMONY")) {
            savedByEmployee.values().forEach(participant -> awardRepository.deleteAllByParticipantId(participant.getId()));
            var validAwards = stored.workbook().awards().stream()
                    .filter(award -> savedByEmployee.containsKey(award.employeeNo()))
                    .toList();
            for (var row : validAwards) {
                var record = new AwardRecordEntity();
                record.setParticipantId(savedByEmployee.get(row.employeeNo()).getId());
                record.setBatchOrder(row.batchOrder());
                record.setBatchName(row.batchName());
                record.setAwardName(row.awardName());
                record.setAwardLevel(row.awardLevel());
                record.setProjectName(row.projectName());
                record.setTeamSize(row.teamSize());
                awardRepository.save(record);
                awardCount++;
            }
        }
        return new CommitResult(inserted, updated, awardCount, inserted);
    }

    private WorkbookImportStrategy requireStrategy(String code) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(code))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "导入模板不存在"));
    }
}
