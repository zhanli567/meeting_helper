package com.company.meetinghelper.meeting.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.common.user.CurrentUserProvider;
import com.company.meetinghelper.meeting.api.dto.request.CreateMeetingRequest;
import com.company.meetinghelper.meeting.api.dto.response.MeetingSummary;
import com.company.meetinghelper.meeting.entity.MeetingElementEntity;
import com.company.meetinghelper.meeting.entity.MeetingEntity;
import com.company.meetinghelper.meeting.repository.MeetingElementRepository;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import com.company.meetinghelper.seating.repository.SeatingPlanRepository;
import com.company.meetinghelper.venue.api.dto.response.VenueDetail;
import com.company.meetinghelper.venue.entity.ElementType;
import com.company.meetinghelper.venue.service.VenueService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingService {
    private final MeetingRepository meetingRepository;
    private final MeetingElementRepository meetingElementRepository;
    private final VenueService venueService;
    private final SeatingPlanRepository planRepository;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 创建会议服务。
     *
     * @param meetingRepository 会议仓储
     * @param meetingElementRepository 会议元素仓储
     * @param venueService 场馆模板服务
     * @param planRepository 排座方案仓储
     * @param currentUserProvider 当前用户提供器
     */
    public MeetingService(
            MeetingRepository meetingRepository,
            MeetingElementRepository meetingElementRepository,
            VenueService venueService,
            SeatingPlanRepository planRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.meetingRepository = meetingRepository;
        this.meetingElementRepository = meetingElementRepository;
        this.venueService = venueService;
        this.planRepository = planRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * 查询当前用户拥有的全部有效会议。
     *
     * @return 会议摘要列表
     */
    @Transactional(readOnly = true)
    public List<MeetingSummary> list() {
        String userId = currentUserProvider.requireUserId();
        return meetingRepository.findAllByCreatedByIdOrderByUpdatedAtDesc(userId).stream()
                .map(meeting -> new MeetingSummary(
                        meeting.getId(), meeting.getName(), meeting.getStatus(), meeting.getLayoutName(),
                        meeting.getUpdatedAt(), meeting.getUpdatedByName()))
                .toList();
    }

    /**
     * 基于场馆模板创建会议、独立布局快照和草稿排座方案。
     *
     * @param request 会议创建请求
     * @return 新建会议摘要
     */
    @Transactional
    public MeetingSummary create(CreateMeetingRequest request) {
        String userId = currentUserProvider.requireUserId();
        String normalizedName = request.name().trim();
        if (meetingRepository.existsByCreatedByIdAndNameIgnoreCase(
                userId,
                normalizedName
        )) {
            throw new ApiException(HttpStatus.CONFLICT, "会议名称已存在");
        }
        VenueDetail venue = venueService.get(request.venueTemplateId());
        MeetingEntity meeting = new MeetingEntity();
        meeting.setName(normalizedName);
        meeting.setStatus("DRAFT");
        meeting.setVenueTemplateId(venue.preset() ? null : venue.id());
        meeting.setLayoutName(venue.name());
        meeting.setGridRows(venue.gridRows());
        meeting.setGridColumns(venue.gridColumns());
        meeting.setCellSize(venue.cellSize());
        meeting.setLayoutVersion(1);
        meeting.setCreatedById(userId);
        meeting.setCreatedByName(userId);
        meeting.setUpdatedById(userId);
        meeting.setUpdatedByName(userId);
        meetingRepository.save(meeting);

        List<MeetingElementEntity> copiedElements = venue.elements().stream()
                .map(source -> {
                    MeetingElementEntity target = new MeetingElementEntity();
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
                })
                .toList();
        meetingElementRepository.saveAll(copiedElements);

        SeatingPlanEntity plan = new SeatingPlanEntity();
        plan.setMeetingId(meeting.getId());
        plan.setName("默认排座方案");
        plan.setStatus("DRAFT");
        plan.setCurrentVersionNo(0);
        planRepository.save(plan);
        return new MeetingSummary(
                meeting.getId(), meeting.getName(), meeting.getStatus(), meeting.getLayoutName(),
                meeting.getUpdatedAt(), meeting.getUpdatedByName());
    }

}
