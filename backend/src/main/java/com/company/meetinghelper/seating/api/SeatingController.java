package com.company.meetinghelper.seating.api;

import com.company.meetinghelper.common.api.ApiResponse;
import com.company.meetinghelper.seating.api.dto.request.AssignmentRequest;
import com.company.meetinghelper.seating.api.dto.request.CreateVersionRequest;
import com.company.meetinghelper.seating.api.dto.request.SaveAssignmentsRequest;
import com.company.meetinghelper.seating.api.dto.request.SaveReservedAreasRequest;
import com.company.meetinghelper.seating.api.dto.response.RestoreVersionResult;
import com.company.meetinghelper.seating.api.dto.response.VersionResult;
import com.company.meetinghelper.seating.service.PlanVersionService;
import com.company.meetinghelper.seating.service.SeatingService;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plans")
public class SeatingController {
    private final SeatingService seatingService;
    private final PlanVersionService versionService;

    /**
     * 创建排座接口控制器。
     *
     * @param seatingService 排座服务
     * @param versionService 版本服务
     */
    public SeatingController(SeatingService seatingService, PlanVersionService versionService) {
        this.seatingService = seatingService;
        this.versionService = versionService;
    }

    /**
     * 安排或交换一名人员的座位。
     *
     * @param planId 排座方案ID
     * @param request 单次排座请求
     * @return 空响应
     */
    @PostMapping("/{planId}/assignments")
    public ApiResponse<Void> assign(
            @PathVariable String planId,
            @Valid @RequestBody AssignmentRequest request
    ) {
        seatingService.assign(planId, request);
        return ApiResponse.success(null);
    }

    /**
     * 保存草稿中的完整人员座位关系。
     *
     * @param planId 排座方案ID
     * @param request 完整人员座位关系
     * @return 空响应
     */
    @PostMapping("/{planId}/assignments/save")
    public ApiResponse<Void> replaceAssignments(
            @PathVariable String planId,
            @Valid @RequestBody SaveAssignmentsRequest request
    ) {
        seatingService.replaceAssignments(planId, request);
        return ApiResponse.success(null);
    }

    /**
     * 保存草稿中的区域。
     *
     * @param planId 排座方案ID
     * @param request 完整区域集合
     * @return 空响应
     */
    @PostMapping("/{planId}/reserved-areas/save")
    public ApiResponse<Void> replaceReservedAreas(
            @PathVariable String planId,
            @Valid @RequestBody SaveReservedAreasRequest request
    ) {
        seatingService.replaceReservedAreas(planId, request);
        return ApiResponse.success(null);
    }

    /**
     * 将人员移回待排列表。
     *
     * @param planId 排座方案ID
     * @param participantId 参会人员ID
     * @return 空响应
     */
    @PostMapping("/{planId}/participants/{participantId}/assignment/remove")
    public ApiResponse<Void> unassign(@PathVariable String planId, @PathVariable String participantId) {
        seatingService.unassign(planId, participantId);
        return ApiResponse.success(null);
    }

    /**
     * 修改人员座位的锁定状态。
     *
     * @param planId 排座方案ID
     * @param participantId 参会人员ID
     * @param locked 是否锁定
     * @return 空响应
     */
    @PostMapping("/{planId}/participants/{participantId}/lock")
    public ApiResponse<Void> lock(
            @PathVariable String planId,
            @PathVariable String participantId,
            @RequestParam boolean locked
    ) {
        seatingService.setLocked(planId, participantId, locked);
        return ApiResponse.success(null);
    }

    /**
     * 发布排座方案版本。
     *
     * @param planId 排座方案ID
     * @param request 版本创建请求
     * @return 新版本信息
     */
    @PostMapping("/{planId}/versions")
    public VersionResult version(
            @PathVariable String planId,
            @Valid @RequestBody CreateVersionRequest request
    ) {
        return versionService.create(planId, request);
    }

    /**
     * 查询指定发布版本的快照。
     *
     * @param planId 排座方案ID
     * @param versionId 版本ID
     * @return 版本工作区快照
     */
    @GetMapping("/{planId}/versions/{versionId}")
    public WorkspaceResponse versionSnapshot(
            @PathVariable String planId,
            @PathVariable String versionId
    ) {
        return versionService.getSnapshot(planId, versionId);
    }

    /**
     * 将指定发布版本恢复为当前草稿。
     *
     * @param planId 排座方案ID
     * @param versionId 版本ID
     * @return 恢复结果
     */
    @PostMapping("/{planId}/versions/{versionId}/restore")
    public RestoreVersionResult restoreVersion(
            @PathVariable String planId,
            @PathVariable String versionId
    ) {
        return versionService.restore(planId, versionId);
    }
}
