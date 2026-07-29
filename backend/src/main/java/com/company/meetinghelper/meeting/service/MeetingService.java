package com.company.meetinghelper.meeting.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.common.user.CurrentUserProvider;
import com.company.meetinghelper.meeting.api.dto.request.CreateMeetingRequest;
import com.company.meetinghelper.meeting.api.dto.request.MeetingElementInput;
import com.company.meetinghelper.meeting.api.dto.request.UpdateMeetingNameRequest;
import com.company.meetinghelper.meeting.api.dto.request.UpdateMeetingLayoutRequest;
import com.company.meetinghelper.meeting.api.dto.response.MeetingSummary;
import com.company.meetinghelper.meeting.entity.MeetingElementEntity;
import com.company.meetinghelper.meeting.entity.MeetingEntity;
import com.company.meetinghelper.meeting.repository.MeetingElementRepository;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.participant.entity.ParticipantEntity;
import com.company.meetinghelper.participant.repository.MeetingParticipantFieldRepository;
import com.company.meetinghelper.participant.repository.ParticipantRecordRepository;
import com.company.meetinghelper.participant.repository.ParticipantRepository;
import com.company.meetinghelper.seating.entity.PlanItemEntity;
import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import com.company.meetinghelper.seating.repository.PlanItemRepository;
import com.company.meetinghelper.seating.repository.PlanItemTargetRepository;
import com.company.meetinghelper.seating.repository.PlanVersionRepository;
import com.company.meetinghelper.seating.repository.SeatingPlanRepository;
import com.company.meetinghelper.seating.service.SeatingService;
import com.company.meetinghelper.venue.api.dto.ElementInput;
import com.company.meetinghelper.venue.api.dto.response.VenueLayout;
import com.company.meetinghelper.venue.entity.ElementKind;
import com.company.meetinghelper.venue.entity.VenueElementEntity;
import com.company.meetinghelper.venue.repository.VenueElementRepository;
import com.company.meetinghelper.venue.service.VenueService;
import com.company.meetinghelper.venue.validation.VenueLayoutValidator;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import com.company.meetinghelper.workspace.service.WorkspaceService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingService {
    private final MeetingRepository meetingRepository;
    private final MeetingElementRepository meetingElementRepository;
    private final VenueService venueService;
    private final VenueElementRepository venueElementRepository;
    private final SeatingPlanRepository planRepository;
    private final PlanItemRepository itemRepository;
    private final PlanItemTargetRepository targetRepository;
    private final PlanVersionRepository versionRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantRecordRepository recordRepository;
    private final MeetingParticipantFieldRepository fieldRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SeatingService seatingService;
    private final WorkspaceService workspaceService;
    private final VenueLayoutValidator layoutValidator;

    /**
     * 创建会议聚合服务。
     *
     * @param meetingRepository 会议仓储
     * @param meetingElementRepository 会议元素仓储
     * @param venueService 场馆服务
     * @param venueElementRepository 场馆元素仓储
     * @param planRepository 排座方案仓储
     * @param itemRepository 排座明细仓储
     * @param targetRepository 排座目标仓储
     * @param versionRepository 版本仓储
     * @param participantRepository 人员仓储
     * @param recordRepository 人员扩展记录仓储
     * @param fieldRepository 人员字段仓储
     * @param currentUserProvider 当前用户提供器
     * @param seatingService 排座服务
     * @param workspaceService 工作区服务
     * @param layoutValidator 布局校验器
     */
    public MeetingService(
            MeetingRepository meetingRepository,
            MeetingElementRepository meetingElementRepository,
            VenueService venueService,
            VenueElementRepository venueElementRepository,
            SeatingPlanRepository planRepository,
            PlanItemRepository itemRepository,
            PlanItemTargetRepository targetRepository,
            PlanVersionRepository versionRepository,
            ParticipantRepository participantRepository,
            ParticipantRecordRepository recordRepository,
            MeetingParticipantFieldRepository fieldRepository,
            CurrentUserProvider currentUserProvider,
            SeatingService seatingService,
            WorkspaceService workspaceService,
            VenueLayoutValidator layoutValidator
    ) {
        this.meetingRepository = meetingRepository;
        this.meetingElementRepository = meetingElementRepository;
        this.venueService = venueService;
        this.venueElementRepository = venueElementRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.targetRepository = targetRepository;
        this.versionRepository = versionRepository;
        this.participantRepository = participantRepository;
        this.recordRepository = recordRepository;
        this.fieldRepository = fieldRepository;
        this.currentUserProvider = currentUserProvider;
        this.seatingService = seatingService;
        this.workspaceService = workspaceService;
        this.layoutValidator = layoutValidator;
    }

    /**
     * 查询当前用户拥有的全部会议。
     *
     * @return 会议摘要列表
     */
    @Transactional(readOnly = true)
    public List<MeetingSummary> list() {
        String userId = currentUserProvider.requireUserId();
        return meetingRepository.findAllByCreatedByIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 基于公共场馆创建会议和独立布局快照。
     *
     * @param request 会议创建请求
     * @return 新建会议摘要
     */
    @Transactional
    public MeetingSummary create(CreateMeetingRequest request) {
        String userId = currentUserProvider.requireUserId();
        String normalizedName = request.name().trim();
        if (meetingRepository.existsByCreatedByIdAndNameIgnoreCase(userId, normalizedName)) {
            throw new ApiException(HttpStatus.CONFLICT, "会议名称已存在");
        }
        VenueLayout venue = venueService.getLayout(request.venueTemplateId());
        if (venue.seatCount() == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "场馆没有可排座座位");
        }
        MeetingEntity meeting = new MeetingEntity();
        meeting.setName(normalizedName);
        meeting.setStatus("DRAFT");
        meeting.setVenueTemplateId(venue.id());
        meeting.setLayoutName(venue.location());
        meeting.setGridRows(venue.gridRows());
        meeting.setGridColumns(venue.gridColumns());
        meeting.setLayoutVersion(1);
        meeting.setCreatedById(userId);
        meeting.setCreatedByName(userId);
        meeting.setUpdatedById(userId);
        meeting.setUpdatedByName(userId);
        meetingRepository.save(meeting);

        List<VenueElementEntity> sources = venueElementRepository
                .findAllByVenueTemplateIdOrderByStartRowAscStartColumnAsc(venue.id());
        ArrayList<MeetingElementEntity> copiedElements =
                new ArrayList<MeetingElementEntity>(venue.elements().size());
        for (int index = 0; index < venue.elements().size(); index++) {
            ElementInput source = venue.elements().get(index);
            MeetingElementEntity target = new MeetingElementEntity();
            target.setMeetingId(meeting.getId());
            target.setSourceElementId(sources.get(index).getId());
            target.setElementKind(ElementKind.valueOf(source.kind()));
            target.setElementName(source.name());
            target.setStartRow(source.row());
            target.setStartColumn(source.column());
            target.setRowSpan(source.rowSpan());
            target.setColumnSpan(source.columnSpan());
            target.setFillColor(source.fillColor());
            copiedElements.add(target);
        }
        meetingElementRepository.saveAll(copiedElements);

        SeatingPlanEntity plan = new SeatingPlanEntity();
        plan.setMeetingId(meeting.getId());
        plan.setName("默认排座方案");
        plan.setStatus("DRAFT");
        plan.setCurrentVersionNo(0);
        planRepository.save(plan);
        return toSummary(meeting);
    }

    /**
     * 更新当前用户拥有的会议名称。
     *
     * @param meetingId 会议ID
     * @param request 会议名称更新请求
     * @return 更新后的会议摘要
     */
    @Transactional
    public MeetingSummary updateName(String meetingId, UpdateMeetingNameRequest request) {
        String userId = currentUserProvider.requireUserId();
        MeetingEntity meeting = meetingRepository.findByIdAndCreatedById(meetingId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会议不存在"));
        String normalizedName = request.name().trim();
        if (!meeting.getName().equalsIgnoreCase(normalizedName)
                && meetingRepository.existsByCreatedByIdAndNameIgnoreCase(userId, normalizedName)) {
            throw new ApiException(HttpStatus.CONFLICT, "会议名称已存在");
        }
        meeting.setName(normalizedName);
        meetingRepository.save(meeting);
        return toSummary(meeting);
    }

    /**
     * 删除当前用户拥有的会议及其独立工作区数据。
     *
     * @param meetingId 会议ID
     * @return 被删除的会议信息
     */
    @Transactional
    public MeetingSummary delete(String meetingId) {
        String userId = currentUserProvider.requireUserId();
        MeetingEntity meeting = meetingRepository.findByIdAndCreatedById(meetingId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会议不存在"));
        MeetingSummary summary = toSummary(meeting);

        deletePlans(meetingId);
        deleteParticipants(meetingId);
        meetingElementRepository.deleteAllByMeetingId(meetingId);
        meetingRepository.delete(meeting);
        return summary;
    }

    /**
     * 更新会议自己的草稿布局快照，不修改来源场馆模板。
     *
     * @param meetingId 会议ID
     * @param request 草稿布局请求
     * @return 更新后的工作区
     */
    @Transactional
    public WorkspaceResponse updateLayout(String meetingId, UpdateMeetingLayoutRequest request) {
        String userId = currentUserProvider.requireUserId();
        MeetingEntity meeting = meetingRepository.findByIdAndCreatedById(meetingId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会议不存在"));
        List<ElementInput> normalizedElements = validateMeetingLayout(request);
        List<MeetingElementInput> requestedElements = request.elements();
        List<MeetingElementEntity> before = meetingElementRepository
                .findAllByMeetingIdOrderByStartRowAscStartColumnAsc(meetingId);
        Map<String,MeetingElementEntity> existingById = before.stream()
                .collect(Collectors.toMap(MeetingElementEntity::getId, value -> value));
        Set<String> retainedIds = requestedElements.stream()
                .map(MeetingElementInput::id)
                .filter(value -> value != null && existingById.containsKey(value))
                .collect(Collectors.toSet());
        Set<String> targetsToRelease = before.stream()
                .map(MeetingElementEntity::getId)
                .filter(id -> !retainedIds.contains(id))
                .collect(Collectors.toSet());
        for (int index = 0; index < requestedElements.size(); index++) {
            MeetingElementInput input = requestedElements.get(index);
            if (input.id() != null && existingById.containsKey(input.id())
                    && !ElementKind.SEAT.name().equals(normalizedElements.get(index).kind())) {
                targetsToRelease.add(input.id());
            }
        }
        seatingService.releaseTargetsForRemovedElements(meetingId, targetsToRelease);
        meetingElementRepository.deleteAll(before.stream()
                .filter(element -> !retainedIds.contains(element.getId()))
                .toList());

        for (int index = 0; index < normalizedElements.size(); index++) {
            MeetingElementInput input = requestedElements.get(index);
            ElementInput normalized = normalizedElements.get(index);
            MeetingElementEntity element = input.id() == null
                    ? null
                    : existingById.get(input.id());
            if (element == null) {
                element = new MeetingElementEntity();
                element.setMeetingId(meetingId);
            }
            applyLayoutElement(element, normalized);
            meetingElementRepository.save(element);
        }
        meeting.setGridRows(request.gridRows());
        meeting.setGridColumns(request.gridColumns());
        meeting.setLayoutVersion(meeting.getLayoutVersion() + 1);
        meetingRepository.save(meeting);
        return workspaceService.getWorkspace(meetingId);
    }

    private List<ElementInput> validateMeetingLayout(UpdateMeetingLayoutRequest request) {
        List<ElementInput> elements = request.elements() == null
                ? null
                : request.elements().stream()
                        .map(input -> input == null
                                ? null
                                : new ElementInput(
                                        input.kind(), input.name(), input.row(), input.column(),
                                        input.rowSpan(), input.columnSpan(), input.fillColor()
                                ))
                        .toList();
        return layoutValidator.validate(request.gridRows(), request.gridColumns(), elements).elements();
    }

    private void applyLayoutElement(MeetingElementEntity element, ElementInput source) {
        element.setElementKind(ElementKind.valueOf(source.kind()));
        element.setElementName(source.name());
        element.setStartRow(source.row());
        element.setStartColumn(source.column());
        element.setRowSpan(source.rowSpan());
        element.setColumnSpan(source.columnSpan());
        element.setFillColor(source.fillColor());
    }

    private void deletePlans(String meetingId) {
        List<SeatingPlanEntity> plans = planRepository.findAllByMeetingIdOrderByCreatedAtAsc(meetingId);
        for (SeatingPlanEntity plan : plans) {
            List<PlanItemEntity> items = itemRepository.findAllByPlanIdOrderByCreatedAtAsc(plan.getId());
            for (PlanItemEntity item : items) {
                targetRepository.deleteAllByPlanItemId(item.getId());
            }
            itemRepository.deleteAll(items);
            versionRepository.deleteAll(versionRepository.findAllByPlanIdOrderByVersionNoDesc(plan.getId()));
        }
        planRepository.deleteAll(plans);
    }

    private void deleteParticipants(String meetingId) {
        List<ParticipantEntity> participants = participantRepository.findAllByMeetingIdOrderByNameAsc(meetingId);
        recordRepository.deleteAll(recordRepository.findAllByParticipantIdInOrderByParticipantIdAscRecordOrderAsc(
                participants.stream().map(ParticipantEntity::getId).toList()
        ));
        participantRepository.deleteAll(participants);
        fieldRepository.deleteAll(fieldRepository.findAllByMeetingIdOrderBySortOrderAsc(meetingId));
    }

    private MeetingSummary toSummary(MeetingEntity meeting) {
        return new MeetingSummary(
                meeting.getId(), meeting.getName(), meeting.getStatus(),
                meeting.getLayoutName(), meeting.getUpdatedAt(), meeting.getUpdatedByName()
        );
    }
}
