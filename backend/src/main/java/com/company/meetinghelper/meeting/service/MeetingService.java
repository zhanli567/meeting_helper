package com.company.meetinghelper.meeting.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.common.user.CurrentUserProvider;
import com.company.meetinghelper.meeting.api.dto.request.CreateMeetingRequest;
import com.company.meetinghelper.meeting.api.dto.request.UpdateMeetingNameRequest;
import com.company.meetinghelper.meeting.api.dto.response.MeetingSummary;
import com.company.meetinghelper.meeting.entity.MeetingElementEntity;
import com.company.meetinghelper.meeting.entity.MeetingEntity;
import com.company.meetinghelper.meeting.repository.MeetingElementRepository;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import com.company.meetinghelper.seating.repository.SeatingPlanRepository;
import com.company.meetinghelper.venue.api.dto.ElementInput;
import com.company.meetinghelper.venue.api.dto.response.VenueLayout;
import com.company.meetinghelper.venue.entity.ElementKind;
import com.company.meetinghelper.venue.entity.VenueElementEntity;
import com.company.meetinghelper.venue.repository.VenueElementRepository;
import com.company.meetinghelper.venue.service.VenueService;
import java.util.ArrayList;
import java.util.List;
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
    private final CurrentUserProvider currentUserProvider;

    /**
     * 创建会议聚合服务。
     *
     * @param meetingRepository 会议仓储
     * @param meetingElementRepository 会议元素仓储
     * @param venueService 场馆服务
     * @param venueElementRepository 场馆元素仓储
     * @param planRepository 排座方案仓储
     * @param currentUserProvider 当前用户提供器
     */
    public MeetingService(
            MeetingRepository meetingRepository,
            MeetingElementRepository meetingElementRepository,
            VenueService venueService,
            VenueElementRepository venueElementRepository,
            SeatingPlanRepository planRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.meetingRepository = meetingRepository;
        this.meetingElementRepository = meetingElementRepository;
        this.venueService = venueService;
        this.venueElementRepository = venueElementRepository;
        this.planRepository = planRepository;
        this.currentUserProvider = currentUserProvider;
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
            target.setBorderColor(source.borderColor());
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

    private MeetingSummary toSummary(MeetingEntity meeting) {
        return new MeetingSummary(
                meeting.getId(), meeting.getName(), meeting.getStatus(),
                meeting.getLayoutName(), meeting.getUpdatedAt(), meeting.getUpdatedByName()
        );
    }
}
