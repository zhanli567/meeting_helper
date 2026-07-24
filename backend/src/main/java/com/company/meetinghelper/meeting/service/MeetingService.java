package com.company.meetinghelper.meeting.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.meeting.api.dto.request.CreateMeetingRequest;
import com.company.meetinghelper.meeting.api.dto.response.MeetingSummary;
import com.company.meetinghelper.meeting.entity.MeetingElementEntity;
import com.company.meetinghelper.meeting.entity.MeetingEntity;
import com.company.meetinghelper.meeting.repository.MeetingElementRepository;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import com.company.meetinghelper.seating.repository.SeatingPlanRepository;
import com.company.meetinghelper.venue.repository.VenueElementRepository;
import com.company.meetinghelper.venue.repository.VenueTemplateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MeetingService {
    private final MeetingRepository meetingRepository;
    private final MeetingElementRepository meetingElementRepository;
    private final VenueTemplateRepository venueRepository;
    private final VenueElementRepository venueElementRepository;
    private final SeatingPlanRepository planRepository;

    public MeetingService(
            MeetingRepository meetingRepository,
            MeetingElementRepository meetingElementRepository,
            VenueTemplateRepository venueRepository,
            VenueElementRepository venueElementRepository,
            SeatingPlanRepository planRepository
    ) {
        this.meetingRepository = meetingRepository;
        this.meetingElementRepository = meetingElementRepository;
        this.venueRepository = venueRepository;
        this.venueElementRepository = venueElementRepository;
        this.planRepository = planRepository;
    }

    @Transactional(readOnly = true)
    public List<MeetingSummary> list() {
        return meetingRepository.findAllByDeletedFalseOrderByUpdatedAtDesc().stream()
                .map(meeting -> new MeetingSummary(
                        meeting.getId(), meeting.getName(), meeting.getStatus(), meeting.getLayoutName(),
                        meeting.getUpdatedAt(), meeting.getUpdatedByName()))
                .toList();
    }

    @Transactional
    public MeetingSummary create(CreateMeetingRequest request) {
        var normalizedName = request.name().trim();
        if (meetingRepository.existsByNameIgnoreCaseAndDeletedFalse(normalizedName)) {
            throw new ApiException(HttpStatus.CONFLICT, "会议名称已存在");
        }
        var venue = venueRepository.findById(request.venueTemplateId())
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "场馆模板不存在"));
        var meeting = new MeetingEntity();
        meeting.setName(normalizedName);
        meeting.setStatus("DRAFT");
        meeting.setVenueTemplateId(venue.getId());
        meeting.setLayoutName(venue.getName());
        meeting.setGridRows(venue.getGridRows());
        meeting.setGridColumns(venue.getGridColumns());
        meeting.setCellSize(venue.getCellSize());
        meeting.setLayoutVersion(1);
        meetingRepository.save(meeting);

        var copiedElements = venueElementRepository
                .findAllByVenueTemplateIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(venue.getId())
                .stream()
                .map(source -> {
                    var target = new MeetingElementEntity();
                    target.setMeetingId(meeting.getId());
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
                })
                .toList();
        meetingElementRepository.saveAll(copiedElements);

        var plan = new SeatingPlanEntity();
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
