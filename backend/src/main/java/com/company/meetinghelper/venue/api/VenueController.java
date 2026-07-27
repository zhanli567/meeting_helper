package com.company.meetinghelper.venue.api;

import com.company.meetinghelper.venue.api.dto.request.CreateVenueRequest;
import com.company.meetinghelper.venue.api.dto.request.UpdateVenueInfoRequest;
import com.company.meetinghelper.venue.api.dto.request.UpdateVenueLayoutRequest;
import com.company.meetinghelper.venue.api.dto.response.LocationAvailability;
import com.company.meetinghelper.venue.api.dto.response.VenueDetail;
import com.company.meetinghelper.venue.api.dto.response.VenueLayout;
import com.company.meetinghelper.venue.api.dto.response.VenuePage;
import com.company.meetinghelper.venue.service.VenueService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/venues")
public class VenueController {
    private final VenueService venueService;

    /**
     * 创建公共场馆接口控制器。
     *
     * @param venueService 公共场馆服务
     */
    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    /**
     * 分页查询公共场馆模板。
     *
     * @param keyword 全字段搜索关键字
     * @param campus 校区筛选值
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 场馆模板分页
     */
    @GetMapping
    public VenuePage list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String campus,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "必须大于等于1") int pageNum,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "必须大于等于1") int pageSize
    ) {
        return venueService.list(keyword, campus, pageNum, pageSize);
    }

    /**
     * 按地点精确检查场馆模板是否可用。
     *
     * @param location 待检查地点
     * @param excludeId 编辑时排除的场馆模板ID
     * @return 地点可用性
     */
    @GetMapping("/location-availability")
    public LocationAvailability locationAvailability(
            @RequestParam
            @NotBlank(message = "请输入地点")
            @Size(max = 200, message = "地点不能超过 200 个字符")
            String location,
            @RequestParam(defaultValue = "") String excludeId
    ) {
        return new LocationAvailability(
                venueService.isLocationAvailable(location, excludeId)
        );
    }

    /**
     * 查询场馆固定信息。
     *
     * @param id 场馆模板ID
     * @return 场馆详情
     */
    @GetMapping("/{id}")
    public VenueDetail get(@PathVariable String id) {
        return venueService.get(id);
    }

    /**
     * 查询场馆布局。
     *
     * @param id 场馆模板ID
     * @return 场馆布局
     */
    @GetMapping("/{id}/layout")
    public VenueLayout getLayout(@PathVariable String id) {
        return venueService.getLayout(id);
    }

    /**
     * 创建公共场馆模板。
     *
     * @param request 场馆创建请求
     * @return 新建场馆详情
     */
    @PostMapping("/create")
    public VenueDetail create(@Valid @RequestBody CreateVenueRequest request) {
        return venueService.create(request);
    }

    /**
     * 更新场馆固定信息。
     *
     * @param id 场馆模板ID
     * @param request 固定信息更新请求
     * @return 更新后的场馆详情
     */
    @PostMapping("/{id}/info/update")
    public VenueDetail updateInfo(
            @PathVariable String id,
            @Valid @RequestBody UpdateVenueInfoRequest request
    ) {
        return venueService.updateInfo(id, request);
    }

    /**
     * 更新场馆布局。
     *
     * @param id 场馆模板ID
     * @param request 布局更新请求
     * @return 更新后的场馆布局
     */
    @PostMapping("/{id}/layout/update")
    public VenueLayout updateLayout(
            @PathVariable String id,
            @Valid @RequestBody UpdateVenueLayoutRequest request
    ) {
        return venueService.updateLayout(id, request);
    }

    /**
     * 物理删除公共场馆模板。
     *
     * @param id 场馆模板ID
     */
    @PostMapping("/{id}/delete")
    public void delete(@PathVariable String id) {
        venueService.delete(id);
    }
}
