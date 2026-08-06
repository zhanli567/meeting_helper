package com.company.meetinghelper.meeting.service;

import com.company.meetinghelper.common.context.CurrentUserHolder;
import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.common.security.CurrentUser;
import com.company.meetinghelper.meeting.entity.MeetingEntity;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.seating.entity.SeatingPlanEntity;
import com.company.meetinghelper.seating.repository.SeatingPlanRepository;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MeetingAccessService 类。
 */
@Service
public class MeetingAccessService {
    private final MeetingRepository meetingRepository;
    private final SeatingPlanRepository planRepository;

    /**
     * 创建会议归属校验服务。
     *
     * @param meetingRepository 会议仓储
     * @param planRepository 排座方案仓储
     */
    public MeetingAccessService(
            MeetingRepository meetingRepository,
            SeatingPlanRepository planRepository
    ) {
        this.meetingRepository = meetingRepository;
        this.planRepository = planRepository;
    }

    /**
     * 查询并校验当前用户拥有的会议。
     *
     * @param meetingId 会议ID
     * @return 当前用户拥有的有效会议
     */
    @Transactional(readOnly = true)
    public MeetingEntity requireOwnedMeeting(String meetingId) {
        CurrentUser currentUser = CurrentUserHolder.get();
        String userId = currentUser == null ? "" : Objects.toString(currentUser.userId(), "");
        return meetingRepository.findByIdAndCreatedById(meetingId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会议不存在"));
    }

    /**
     * 查询排座方案并校验其所属会议归当前用户所有。
     *
     * @param planId 排座方案ID
     * @return 当前用户会议中的有效排座方案
     */
    @Transactional(readOnly = true)
    public SeatingPlanEntity requireOwnedPlan(String planId) {
        CurrentUser currentUser = CurrentUserHolder.get();
        String userId = currentUser == null ? "" : Objects.toString(currentUser.userId(), "");
        return planRepository.findOwnedById(planId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "排座方案不存在"));
    }
}
