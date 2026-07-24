package com.company.meetinghelper.venue.api;

import com.company.meetinghelper.venue.api.dto.request.CreateVenueRequest;
import com.company.meetinghelper.venue.api.dto.response.VenueDetail;
import com.company.meetinghelper.venue.api.dto.response.VenueSummary;
import com.company.meetinghelper.venue.service.VenueService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/venues")
public class VenueController {
    private final VenueService venueService;

    /**
     * 创建场馆模板接口控制器。
     *
     * @param venueService 场馆模板服务
     */
    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    /**
     * 查询全部可用场馆模板。
     *
     * @return 场馆模板列表
     */
    @GetMapping
    public List<VenueSummary> list() {
        return venueService.list();
    }

    /**
     * 查询场馆模板详情。
     *
     * @param id 场馆模板ID
     * @return 场馆模板详情
     */
    @GetMapping("/{id}")
    public VenueDetail get(@PathVariable String id) {
        return venueService.get(id);
    }

    /**
     * 创建自定义场馆模板。
     *
     * @param request 场馆模板请求
     * @return 新建场馆模板详情
     */
    @PostMapping
    public VenueDetail create(@Valid @RequestBody CreateVenueRequest request) {
        return venueService.create(request);
    }

    /**
     * 更新自定义场馆模板。
     *
     * @param id 场馆模板ID
     * @param request 场馆模板请求
     * @return 更新后的场馆模板详情
     */
    @PutMapping("/{id}")
    public VenueDetail update(
            @PathVariable String id,
            @Valid @RequestBody CreateVenueRequest request
    ) {
        return venueService.update(id, request);
    }

    /**
     * 删除自定义场馆模板。
     *
     * @param id 场馆模板ID
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        venueService.delete(id);
    }
}
