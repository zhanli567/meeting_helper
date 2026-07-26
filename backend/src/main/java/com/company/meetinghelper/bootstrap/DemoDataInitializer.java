package com.company.meetinghelper.bootstrap;

import com.company.meetinghelper.meeting.entity.MeetingElementEntity;
import com.company.meetinghelper.meeting.entity.MeetingEntity;
import com.company.meetinghelper.meeting.repository.MeetingElementRepository;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
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
import com.company.meetinghelper.seating.repository.SeatingPlanRepository;
import com.company.meetinghelper.venue.entity.ElementType;
import com.company.meetinghelper.venue.preset.PresetVenueDefinition;
import com.company.meetinghelper.venue.preset.PresetVenueStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DemoDataInitializer implements ApplicationRunner {
    private static final String DEMO_USER_ID = "demo-secretary";
    private static final String DEMO_MEETING_NAME = "2026年度荣誉表彰大会";
    private static final List<String> PARTICIPANT_FIELD_NAMES =
            List.of("部门", "人员类型", "职级", "批次", "奖项名称");

    private final MeetingRepository meetingRepository;
    private final MeetingElementRepository meetingElementRepository;
    private final ParticipantRepository participantRepository;
    private final MeetingParticipantFieldRepository fieldRepository;
    private final ParticipantRecordRepository recordRepository;
    private final SeatingPlanRepository planRepository;
    private final PlanItemRepository itemRepository;
    private final PlanItemTargetRepository targetRepository;
    private final PresetVenueStore presetVenueStore;
    private final ObjectMapper objectMapper;

    public DemoDataInitializer(
            MeetingRepository meetingRepository,
            MeetingElementRepository meetingElementRepository,
            ParticipantRepository participantRepository,
            MeetingParticipantFieldRepository fieldRepository,
            ParticipantRecordRepository recordRepository,
            SeatingPlanRepository planRepository,
            PlanItemRepository itemRepository,
            PlanItemTargetRepository targetRepository,
            PresetVenueStore presetVenueStore,
            ObjectMapper objectMapper
    ) {
        this.meetingRepository = meetingRepository;
        this.meetingElementRepository = meetingElementRepository;
        this.participantRepository = participantRepository;
        this.fieldRepository = fieldRepository;
        this.recordRepository = recordRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
        this.presetVenueStore = presetVenueStore;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var demoMeetingExists = meetingRepository
                .findAllByCreatedByIdAndDeletedFalseOrderByUpdatedAtDesc(DEMO_USER_ID)
                .stream()
                .anyMatch(value -> DEMO_MEETING_NAME.equals(value.getName()));
        if (demoMeetingExists) {
            return;
        }
        var presetVenue = presetVenueStore.findAll().getFirst();
        var meeting = createDemoMeeting(presetVenue);
        var meetingElements = meetingElementRepository
                .findAllByMeetingIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(meeting.getId());
        createParticipantFields(meeting.getId());
        var participants = createParticipants(meeting.getId());
        participantRepository.saveAll(participants);
        createParticipantRecords(participants);
        var plan = planRepository.findFirstByMeetingIdAndDeletedFalseOrderByCreatedAtAsc(meeting.getId())
                .orElseThrow();
        createInitialPlacements(plan, participants, meetingElements);
    }

    private MeetingEntity createDemoMeeting(PresetVenueDefinition venue) {
        var meeting = new MeetingEntity();
        meeting.setName(DEMO_MEETING_NAME);
        meeting.setStatus("DRAFT");
        meeting.setLayoutName(venue.name());
        meeting.setGridRows(venue.gridRows());
        meeting.setGridColumns(venue.gridColumns());
        meeting.setCellSize(venue.cellSize());
        meeting.setLayoutVersion(1);
        meeting.setCreatedById(DEMO_USER_ID);
        meeting.setCreatedByName("演示秘书");
        meeting.setUpdatedById(DEMO_USER_ID);
        meeting.setUpdatedByName("演示秘书");
        meetingRepository.save(meeting);

        var copiedElements = venue.elements().stream().map(source -> {
            var target = new MeetingElementEntity();
            target.setMeetingId(meeting.getId());
            target.setElementType(ElementType.valueOf(source.type()));
            target.setCode(source.code());
            target.setLabel(source.label());
            target.setGridRow(source.row());
            target.setGridColumn(source.column());
            target.setRowSpan(source.rowSpan());
            target.setColumnSpan(source.columnSpan());
            target.setRotation(source.rotation());
            target.setCapacity(source.capacity());
            target.setAssignable(source.assignable());
            target.setWalkable(source.walkable());
            target.setGroupCode(source.groupCode());
            target.setGroupLabel(source.groupLabel());
            target.setSequenceNo(source.sequenceNo());
            target.setBackgroundColor(source.backgroundColor());
            target.setBorderColor(source.borderColor());
            return target;
        }).toList();
        meetingElementRepository.saveAll(copiedElements);

        var plan = new SeatingPlanEntity();
        plan.setMeetingId(meeting.getId());
        plan.setName("默认排座方案");
        plan.setStatus("DRAFT");
        plan.setCurrentVersionNo(0);
        planRepository.save(plan);
        return meeting;
    }

    private void createParticipantFields(String meetingId) {
        var fields = new ArrayList<MeetingParticipantFieldEntity>();
        for (int index = 0; index < PARTICIPANT_FIELD_NAMES.size(); index++) {
            var field = new MeetingParticipantFieldEntity();
            field.setMeetingId(meetingId);
            field.setFieldName(PARTICIPANT_FIELD_NAMES.get(index));
            field.setSortOrder(index + 1);
            fields.add(field);
        }
        fieldRepository.saveAll(fields);
    }

    private List<ParticipantEntity> createParticipants(String meetingId) {
        var names = List.of(
                "周明远", "苏婉清", "陈景行", "林若川", "沈知意", "陆嘉禾", "许砚秋",
                "顾言深", "唐以宁", "宋怀瑾", "叶书昀", "方云舟", "程星野", "江晚晴",
                "秦牧川", "温如许", "贺知章", "黎清和", "夏予安", "白修远", "孟知微",
                "韩墨", "罗清越", "梁知夏", "邵云帆", "谢景初", "余念安", "杜若衡"
        );
        var participants = new ArrayList<ParticipantEntity>();
        for (int index = 0; index < names.size(); index++) {
            var participant = new ParticipantEntity();
            participant.setMeetingId(meetingId);
            participant.setEmployeeNo("a" + String.format("%08d", index + 1));
            participant.setName(names.get(index));
            participants.add(participant);
        }
        return participants;
    }

    private void createParticipantRecords(List<ParticipantEntity> participants) {
        var departments = List.of("平台研发部", "产品设计部", "数据智能部", "企业服务部", "质量工程部");
        var awardNames = List.of("卓越创新奖", "客户价值奖", "技术突破奖", "优秀项目奖");
        var records = new ArrayList<ParticipantRecordEntity>();
        for (int index = 0; index < participants.size(); index++) {
            var participant = participants.get(index);
            var commonAttributes = new LinkedHashMap<String, String>();
            commonAttributes.put("部门", departments.get(index % departments.size()));
            commonAttributes.put("人员类型", index < 3 ? "特邀嘉宾" : index < 6 ? "嘉宾" : "参会人员");
            commonAttributes.put("职级", String.valueOf(20 - index % 7));
            int batch = index == 0 ? 2 : index % 8 + 1;
            records.add(participantRecord(
                    participant.getId(),
                    1,
                    commonAttributes,
                    "第" + chineseNumber(batch) + "批",
                    awardNames.get(index % awardNames.size())
            ));
            if (index == 0) {
                records.add(participantRecord(
                        participant.getId(),
                        2,
                        commonAttributes,
                        "第三批",
                        "特别贡献奖"
                ));
            } else if (index % 6 == 0) {
                int repeated = Math.min(10, batch + 3);
                records.add(participantRecord(
                        participant.getId(),
                        2,
                        commonAttributes,
                        "第" + chineseNumber(repeated) + "批",
                        "特别贡献奖"
                ));
            }
        }
        recordRepository.saveAll(records);
    }

    private ParticipantRecordEntity participantRecord(
            String participantId,
            int recordOrder,
            Map<String, String> commonAttributes,
            String batchName,
            String awardName
    ) {
        var attributes = new LinkedHashMap<>(commonAttributes);
        attributes.put("批次", batchName);
        attributes.put("奖项名称", awardName);
        var record = new ParticipantRecordEntity();
        record.setParticipantId(participantId);
        record.setRecordOrder(recordOrder);
        try {
            record.setAttributesJson(objectMapper.writeValueAsString(attributes));
        } catch (Exception exception) {
            throw new IllegalStateException("序列化演示人员动态记录失败", exception);
        }
        return record;
    }

    private void createInitialPlacements(
            SeatingPlanEntity plan,
            List<ParticipantEntity> participants,
            List<MeetingElementEntity> elements
    ) {
        var seatsByCode = elements.stream()
                .filter(element -> element.getElementType() == ElementType.SEAT)
                .collect(Collectors.toMap(MeetingElementEntity::getCode, value -> value));
        var initialSeats = List.of(
                "1排16", "1排17", "1排15", "2排16", "2排17", "2排15",
                "1排07", "1排06", "1排26", "1排27", "2排07", "2排26"
        );
        for (int index = 0; index < initialSeats.size(); index++) {
            addItem(plan.getId(), PlanItemType.PERSON, participants.get(index).getId(),
                    participants.get(index).getName(), null, List.of(seatsByCode.get(initialSeats.get(index)).getId()));
        }
        addItem(plan.getId(), PlanItemType.EQUIPMENT, null, "摄像机", "#FDE68A",
                List.of(seatsByCode.get("5排15").getId(), seatsByCode.get("5排16").getId()));
        var lastRowTargets = elements.stream()
                .filter(element -> element.getElementType() == ElementType.SEAT
                        && "ROW_9".equals(element.getGroupCode()))
                .map(MeetingElementEntity::getId)
                .toList();
        addItem(plan.getId(), PlanItemType.DISABLED, null, "本次不启用", "#E2E8F0", lastRowTargets);
    }

    private void addItem(
            String planId,
            PlanItemType type,
            String participantId,
            String label,
            String background,
            List<String> targets
    ) {
        var item = new PlanItemEntity();
        item.setPlanId(planId);
        item.setItemType(type);
        item.setParticipantId(participantId);
        item.setLabel(label);
        item.setBackgroundColor(background);
        item.setTextColor("#172033");
        item.setBold(type != PlanItemType.PERSON);
        itemRepository.save(item);
        var itemTargets = targets.stream().map(elementId -> {
            var target = new PlanItemTargetEntity();
            target.setPlanItemId(item.getId());
            target.setMeetingElementId(elementId);
            return target;
        }).toList();
        targetRepository.saveAll(itemTargets);
    }

    private String chineseNumber(int value) {
        return switch (value) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            case 7 -> "七";
            case 8 -> "八";
            case 9 -> "九";
            case 10 -> "十";
            default -> String.valueOf(value);
        };
    }
}
