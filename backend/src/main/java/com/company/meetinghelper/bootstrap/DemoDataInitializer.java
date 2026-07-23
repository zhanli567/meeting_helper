package com.company.meetinghelper.bootstrap;

import com.company.meetinghelper.award.AwardRecordEntity;
import com.company.meetinghelper.award.AwardRecordRepository;
import com.company.meetinghelper.meeting.MeetingElementEntity;
import com.company.meetinghelper.meeting.MeetingElementRepository;
import com.company.meetinghelper.meeting.MeetingEntity;
import com.company.meetinghelper.meeting.MeetingRepository;
import com.company.meetinghelper.participant.ParticipantEntity;
import com.company.meetinghelper.participant.ParticipantRepository;
import com.company.meetinghelper.seating.PlanItemEntity;
import com.company.meetinghelper.seating.PlanItemRepository;
import com.company.meetinghelper.seating.PlanItemTargetEntity;
import com.company.meetinghelper.seating.PlanItemTargetRepository;
import com.company.meetinghelper.seating.PlanItemType;
import com.company.meetinghelper.seating.SeatingPlanEntity;
import com.company.meetinghelper.seating.SeatingPlanRepository;
import com.company.meetinghelper.venue.ElementType;
import com.company.meetinghelper.venue.FrontDirection;
import com.company.meetinghelper.venue.VenueElementEntity;
import com.company.meetinghelper.venue.VenueElementRepository;
import com.company.meetinghelper.venue.VenueTemplateEntity;
import com.company.meetinghelper.venue.VenueTemplateRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DemoDataInitializer implements ApplicationRunner {
    private final VenueTemplateRepository venueRepository;
    private final VenueElementRepository venueElementRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingElementRepository meetingElementRepository;
    private final ParticipantRepository participantRepository;
    private final AwardRecordRepository awardRepository;
    private final SeatingPlanRepository planRepository;
    private final PlanItemRepository itemRepository;
    private final PlanItemTargetRepository targetRepository;

    public DemoDataInitializer(
            VenueTemplateRepository venueRepository,
            VenueElementRepository venueElementRepository,
            MeetingRepository meetingRepository,
            MeetingElementRepository meetingElementRepository,
            ParticipantRepository participantRepository,
            AwardRecordRepository awardRepository,
            SeatingPlanRepository planRepository,
            PlanItemRepository itemRepository,
            PlanItemTargetRepository targetRepository
    ) {
        this.venueRepository = venueRepository;
        this.venueElementRepository = venueElementRepository;
        this.meetingRepository = meetingRepository;
        this.meetingElementRepository = meetingElementRepository;
        this.participantRepository = participantRepository;
        this.awardRepository = awardRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (meetingRepository.count() > 0) {
            return;
        }
        var venue = createVenue();
        var venueElements = createVenueElements(venue.getId());
        venueElementRepository.saveAll(venueElements);
        var meeting = createMeeting(venue);
        var meetingElements = venueElements.stream().map(source -> copyToMeeting(meeting.getId(), source)).toList();
        meetingElementRepository.saveAll(meetingElements);
        var participants = createParticipants(meeting.getId());
        participantRepository.saveAll(participants);
        createAwards(participants);
        var plan = createPlan(meeting.getId());
        createInitialPlacements(plan, participants, meetingElements);
    }

    private VenueTemplateEntity createVenue() {
        var venue = new VenueTemplateEntity();
        venue.setName("颁奖典礼礼堂");
        venue.setDescription("舞台位于上方，包含左右门、四条纵向通道和九排座位的预置演示场馆。");
        venue.setGridRows(18);
        venue.setGridColumns(43);
        venue.setCellSize(34);
        venue.setVersionNo(1);
        venue.setPreset(true);
        venue.setFrontDirection(FrontDirection.TOP);
        return venueRepository.save(venue);
    }

    private List<VenueElementEntity> createVenueElements(String venueId) {
        var elements = new ArrayList<VenueElementEntity>();
        elements.add(element(venueId, ElementType.STAGE, "STAGE", "舞台", 1, 5, 2, 35,
                false, true, 0, "#DBEAFE", "#93C5FD"));
        elements.add(element(venueId, ElementType.WALL, null, null, 1, 1, 4, 1,
                false, false, 0, "#64748B", "#475569"));
        elements.add(element(venueId, ElementType.WALL, null, null, 6, 1, 13, 1,
                false, false, 0, "#64748B", "#475569"));
        elements.add(element(venueId, ElementType.WALL, null, null, 1, 43, 4, 1,
                false, false, 0, "#64748B", "#475569"));
        elements.add(element(venueId, ElementType.WALL, null, null, 6, 43, 13, 1,
                false, false, 0, "#64748B", "#475569"));
        elements.add(element(venueId, ElementType.DOOR, "LEFT_DOOR", "左前门", 5, 1, 1, 2,
                false, true, 0, "#FED7AA", "#EA580C"));
        elements.add(element(venueId, ElementType.DOOR, "RIGHT_DOOR", "右前门", 5, 42, 1, 2,
                false, true, 0, "#FED7AA", "#EA580C"));
        elements.add(element(venueId, ElementType.AISLE, "FRONT_AISLE", "舞台前通行区", 3, 3, 3, 39,
                false, true, 0, "#F8FAFC", "#E2E8F0"));
        elements.add(element(venueId, ElementType.AISLE, "LEFT_OUTER_AISLE", "左侧走廊", 6, 2, 9, 2,
                false, true, 0, "#F8FAFC", "#E2E8F0"));
        elements.add(element(venueId, ElementType.AISLE, "LEFT_INNER_AISLE", "左中走廊", 6, 11, 9, 2,
                false, true, 0, "#F8FAFC", "#E2E8F0"));
        elements.add(element(venueId, ElementType.AISLE, "RIGHT_INNER_AISLE", "中右走廊", 6, 32, 9, 2,
                false, true, 0, "#F8FAFC", "#E2E8F0"));
        elements.add(element(venueId, ElementType.AISLE, "RIGHT_OUTER_AISLE", "右侧走廊", 6, 41, 9, 2,
                false, true, 0, "#F8FAFC", "#E2E8F0"));

        for (int row = 1; row <= 9; row++) {
            var gridRow = row + 5;
            for (int index = 1; index <= 7; index++) {
                elements.add(seat(venueId, row, index, gridRow, 3 + index, 1));
            }
            int centerColumn = 13;
            for (int index = 8; index <= 25; index++) {
                int span = row == 5 && index == 15 ? 2 : 1;
                elements.add(seat(venueId, row, index, gridRow, centerColumn, span));
                centerColumn += span;
            }
            for (int index = 26; index <= 32; index++) {
                elements.add(seat(venueId, row, index, gridRow, 34 + index - 26, 1));
            }
        }
        elements.add(element(venueId, ElementType.LABEL, "EXIT", "后方出口", 16, 18, 1, 9,
                false, true, 0, "#F1F5F9", "#CBD5E1"));
        return elements;
    }

    private VenueElementEntity seat(
            String venueId,
            int row,
            int index,
            int gridRow,
            int gridColumn,
            int columnSpan
    ) {
        var seat = element(
                venueId, ElementType.SEAT, row + "排" + String.format("%02d", index),
                null, gridRow, gridColumn, 1, columnSpan, true, false, 1,
                "#FFFFFF", "#CBD5E1");
        seat.setGroupCode("ROW_" + row);
        seat.setGroupLabel(row + "排");
        seat.setSequenceNo(index);
        return seat;
    }

    private VenueElementEntity element(
            String venueId,
            ElementType type,
            String code,
            String label,
            int row,
            int column,
            int rowSpan,
            int columnSpan,
            boolean assignable,
            boolean walkable,
            int capacity,
            String background,
            String border
    ) {
        var element = new VenueElementEntity();
        element.setVenueTemplateId(venueId);
        element.setElementType(type);
        element.setCode(code);
        element.setLabel(label);
        element.setGridRow(row);
        element.setGridColumn(column);
        element.setRowSpan(rowSpan);
        element.setColumnSpan(columnSpan);
        element.setRotation(0);
        element.setAssignable(assignable);
        element.setWalkable(walkable);
        element.setCapacity(capacity);
        element.setBackgroundColor(background);
        element.setBorderColor(border);
        return element;
    }

    private MeetingEntity createMeeting(VenueTemplateEntity venue) {
        var meeting = new MeetingEntity();
        meeting.setName("2026年度荣誉表彰大会");
        meeting.setStatus("DRAFT");
        meeting.setVenueTemplateId(venue.getId());
        meeting.setLayoutName(venue.getName());
        meeting.setGridRows(venue.getGridRows());
        meeting.setGridColumns(venue.getGridColumns());
        meeting.setCellSize(venue.getCellSize());
        meeting.setLayoutVersion(1);
        return meetingRepository.save(meeting);
    }

    private MeetingElementEntity copyToMeeting(String meetingId, VenueElementEntity source) {
        var target = new MeetingElementEntity();
        target.setMeetingId(meetingId);
        target.setSourceElementId(source.getId());
        target.setElementType(source.getElementType());
        target.setCode(source.getCode());
        target.setLabel(source.getLabel());
        target.setGridRow(source.getGridRow());
        target.setGridColumn(source.getGridColumn());
        target.setRowSpan(source.getRowSpan());
        target.setColumnSpan(source.getColumnSpan());
        target.setRotation(source.getRotation());
        target.setCapacity(source.getCapacity());
        target.setAssignable(source.isAssignable());
        target.setWalkable(source.isWalkable());
        target.setGroupCode(source.getGroupCode());
        target.setGroupLabel(source.getGroupLabel());
        target.setSequenceNo(source.getSequenceNo());
        target.setBackgroundColor(source.getBackgroundColor());
        target.setBorderColor(source.getBorderColor());
        return target;
    }

    private List<ParticipantEntity> createParticipants(String meetingId) {
        var names = List.of(
                "周明远", "苏婉清", "陈景行", "林若川", "沈知意", "陆嘉禾", "许砚秋",
                "顾言深", "唐以宁", "宋怀瑾", "叶书昀", "方云舟", "程星野", "江晚晴",
                "秦牧川", "温如许", "贺知章", "黎清和", "夏予安", "白修远", "孟知微",
                "韩墨", "罗清越", "梁知夏", "邵云帆", "谢景初", "余念安", "杜若衡"
        );
        var departments = List.of("平台研发部", "产品设计部", "数据智能部", "企业服务部", "质量工程部");
        var participants = new ArrayList<ParticipantEntity>();
        for (int index = 0; index < names.size(); index++) {
            var participant = new ParticipantEntity();
            participant.setMeetingId(meetingId);
            participant.setEmployeeNo("A" + String.format("%08d", index + 1));
            participant.setName(names.get(index));
            participant.setLevelValue(20 - index % 7);
            participant.setDepartment(departments.get(index % departments.size()));
            participant.setParticipantType(index < 3 ? "特邀嘉宾" : index < 6 ? "嘉宾" : "获奖人员");
            participant.setTags(index % 4 == 0 ? "高级专家" : index % 5 == 0 ? "团队代表" : "");
            participant.setCustomAttributesJson("{}");
            participants.add(participant);
        }
        return participants;
    }

    private void createAwards(List<ParticipantEntity> participants) {
        var awardNames = List.of("卓越创新奖", "客户价值奖", "技术突破奖", "优秀项目奖");
        for (int index = 0; index < participants.size(); index++) {
            var participant = participants.get(index);
            int batch = index % 8 + 1;
            awardRepository.save(award(
                    participant.getId(), batch, "第" + chineseNumber(batch) + "批",
                    awardNames.get(index % awardNames.size()), "项目" + (index + 1)));
            if (index % 6 == 0) {
                int repeated = Math.min(10, batch + 3);
                awardRepository.save(award(
                        participant.getId(), repeated, "第" + chineseNumber(repeated) + "批",
                        "特别贡献奖", "联合项目" + (index + 1)));
            }
        }
    }

    private AwardRecordEntity award(
            String participantId,
            int batch,
            String batchName,
            String awardName,
            String project
    ) {
        var record = new AwardRecordEntity();
        record.setParticipantId(participantId);
        record.setBatchOrder(batch);
        record.setBatchName(batchName);
        record.setAwardName(awardName);
        record.setAwardLevel("年度奖");
        record.setProjectName(project);
        record.setTeamSize(1);
        return record;
    }

    private SeatingPlanEntity createPlan(String meetingId) {
        var plan = new SeatingPlanEntity();
        plan.setMeetingId(meetingId);
        plan.setName("秘书工作方案");
        plan.setStatus("DRAFT");
        plan.setCurrentVersionNo(0);
        return planRepository.save(plan);
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
