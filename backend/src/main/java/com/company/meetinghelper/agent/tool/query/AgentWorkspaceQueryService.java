package com.company.meetinghelper.agent.tool.query;

import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse.ElementView;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse.ParticipantView;
import com.company.meetinghelper.workspace.service.WorkspaceService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 为智能体提供工作区的只读查询能力。 */
@Service
public class AgentWorkspaceQueryService {
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;
    private final Function<String, WorkspaceResponse> workspaceLoader;

    /** 创建生产环境查询服务。
     *
     * @param workspaceService 工作区聚合服务
     */
    @Autowired
    public AgentWorkspaceQueryService(WorkspaceService workspaceService) {
        this(workspaceService::getWorkspace);
    }

    /** 创建测试用查询服务。
     *
     * @param workspaceLoader 工作区加载函数
     */
    public AgentWorkspaceQueryService(Function<String, WorkspaceResponse> workspaceLoader) {
        this.workspaceLoader = Objects.requireNonNull(workspaceLoader);
    }

    /** 汇总会议工作区的可查询状态。
     *
     * @param meetingId 会议ID
     * @return 工作区汇总
     */
    public WorkspaceSummaryResult summarize(String meetingId) {
        WorkspaceResponse workspace = workspaceLoader.apply(meetingId);
        List<ParticipantView> participants = workspace.participants();
        int assignedCount = (int) participants.stream().filter(this::isAssigned).count();
        int seatCount = seatElements(workspace).size();
        int lockedCount = (int) participants.stream().filter(ParticipantView::locked).count();
        int attendingCount = (int) participants.stream().filter(this::isAttending).count();
        return new WorkspaceSummaryResult(
                workspace.meeting().id(), workspace.meeting().name(), participants.size(), attendingCount,
                seatCount, assignedCount, participants.size() - assignedCount, lockedCount,
                Math.max(0, seatCount - assignedCount)
        );
    }

    /** 查询尚未分配座位的人员。
     *
     * @param meetingId 会议ID
     * @param keyword 关键词
     * @param limit 返回条数上限
     * @return 人员摘要分页结果
     */
    public AgentPageResult<ParticipantBrief> listUnassigned(String meetingId, String keyword, int limit) {
        return page(meetingId, keyword, limit, participant -> !isAssigned(participant));
    }

    /** 按关键词查询人员。
     *
     * @param meetingId 会议ID
     * @param keyword 关键词
     * @param limit 返回条数上限
     * @return 人员摘要分页结果
     */
    public AgentPageResult<ParticipantBrief> searchParticipants(String meetingId, String keyword, int limit) {
        return page(meetingId, keyword, limit, participant -> true);
    }

    /** 按关键词查询座位及其占用情况。
     *
     * @param meetingId 会议ID
     * @param keyword 关键词
     * @param limit 返回条数上限
     * @return 座位摘要分页结果
     */
    public AgentPageResult<SeatBrief> searchSeats(String meetingId, String keyword, int limit) {
        WorkspaceResponse workspace = workspaceLoader.apply(meetingId);
        Map<String, ParticipantView> participants = workspace.participants().stream()
                .filter(this::isAssigned)
                .collect(Collectors.toMap(ParticipantView::assignedElementId, participant -> participant));
        List<SeatBrief> matches = seatElements(workspace).stream()
                .map(seat -> toSeatBrief(seat, participants.get(seat.id())))
                .filter(seat -> matches(seat, keyword))
                .toList();
        return pageResult(matches, limit, "共找到" + matches.size() + "个座位");
    }

    private AgentPageResult<ParticipantBrief> page(
            String meetingId,
            String keyword,
            int limit,
            Function<ParticipantView, Boolean> scope
    ) {
        WorkspaceResponse workspace = workspaceLoader.apply(meetingId);
        List<ParticipantBrief> matches = workspace.participants().stream()
                .filter(scope::apply)
                .filter(participant -> matches(participant, keyword))
                .map(this::toParticipantBrief)
                .toList();
        return pageResult(matches, limit, "共找到" + matches.size() + "个人员");
    }

    private <T> AgentPageResult<T> pageResult(List<T> matches, int limit, String summary) {
        int boundedLimit = Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, limit));
        List<T> items = matches.subList(0, Math.min(boundedLimit, matches.size()));
        return new AgentPageResult<>(matches.size(), items.size(), summary, List.copyOf(items));
    }

    private List<ElementView> seatElements(WorkspaceResponse workspace) {
        return workspace.layout().elements().stream().filter(element -> "SEAT".equals(element.kind())).toList();
    }

    private ParticipantBrief toParticipantBrief(ParticipantView participant) {
        return new ParticipantBrief(
                participant.id(), participant.employeeNo(), participant.name(), participant.attendanceStatus(),
                participant.locked(), participant.assignedElementId(), Map.copyOf(participant.primaryAttributes())
        );
    }

    private SeatBrief toSeatBrief(ElementView seat, ParticipantView participant) {
        if (participant == null) {
            return new SeatBrief(seat.id(), seat.name(), null, null);
        }
        return new SeatBrief(seat.id(), seat.name(), participant.name(), participant.employeeNo());
    }

    private boolean matches(ParticipantView participant, String keyword) {
        if (blank(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(participant.name(), normalized) || contains(participant.employeeNo(), normalized)
                || contains(participant.attendanceStatus(), normalized)
                || participant.primaryAttributes().entrySet().stream()
                .anyMatch(entry -> contains(entry.getKey(), normalized) || contains(entry.getValue(), normalized));
    }

    private boolean matches(SeatBrief seat, String keyword) {
        if (blank(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(seat.id(), normalized) || contains(seat.name(), normalized)
                || contains(seat.occupiedParticipantName(), normalized)
                || contains(seat.occupiedEmployeeNo(), normalized);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isAssigned(ParticipantView participant) {
        return participant.assignedElementId() != null;
    }

    private boolean isAttending(ParticipantView participant) {
        return !"ABSENT".equalsIgnoreCase(participant.attendanceStatus());
    }
}
