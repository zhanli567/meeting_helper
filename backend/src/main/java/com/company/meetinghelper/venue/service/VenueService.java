package com.company.meetinghelper.venue.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.meeting.repository.MeetingRepository;
import com.company.meetinghelper.venue.api.dto.ElementInput;
import com.company.meetinghelper.venue.api.dto.request.CreateVenueRequest;
import com.company.meetinghelper.venue.api.dto.request.UpdateVenueInfoRequest;
import com.company.meetinghelper.venue.api.dto.request.UpdateVenueLayoutRequest;
import com.company.meetinghelper.venue.api.dto.response.VenueDetail;
import com.company.meetinghelper.venue.api.dto.response.VenueLayout;
import com.company.meetinghelper.venue.api.dto.response.VenuePage;
import com.company.meetinghelper.venue.api.dto.response.VenueSummary;
import com.company.meetinghelper.venue.entity.ElementKind;
import com.company.meetinghelper.venue.entity.VenueElementEntity;
import com.company.meetinghelper.venue.entity.VenueTemplateEntity;
import com.company.meetinghelper.venue.repository.VenueElementRepository;
import com.company.meetinghelper.venue.repository.VenueTemplateRepository;
import com.company.meetinghelper.venue.validation.VenueLayoutValidator;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueService {
    private final VenueTemplateRepository templateRepository;
    private final VenueElementRepository elementRepository;
    private final MeetingRepository meetingRepository;
    private final VenueLayoutValidator layoutValidator;

    /**
     * 创建公共场馆聚合服务。
     *
     * @param templateRepository 场馆模板仓储
     * @param elementRepository 场馆元素仓储
     * @param meetingRepository 会议仓储
     * @param layoutValidator 布局校验器
     */
    public VenueService(
            VenueTemplateRepository templateRepository,
            VenueElementRepository elementRepository,
            MeetingRepository meetingRepository,
            VenueLayoutValidator layoutValidator
    ) {
        this.templateRepository = templateRepository;
        this.elementRepository = elementRepository;
        this.meetingRepository = meetingRepository;
        this.layoutValidator = layoutValidator;
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
    @Transactional(readOnly = true)
    public VenuePage list(String keyword, String campus, int pageNum, int pageSize) {
        Page<VenueTemplateEntity> page =
                templateRepository.findPage(keyword, campus, pageNum, pageSize);
        List<VenueSummary> records = page.getRecords().stream()
                .map(this::toSummary)
                .toList();
        return new VenuePage(records, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 按规范化地点精确检查场馆模板可用性。
     *
     * @param location 待检查地点
     * @param excludeId 编辑时排除的场馆模板ID
     * @return 地点未被其他模板占用时返回true
     */
    @Transactional(readOnly = true)
    public boolean isLocationAvailable(String location, String excludeId) {
        String locationKey = location.trim().toLowerCase(Locale.ROOT);
        if (excludeId == null || excludeId.isBlank()) {
            return !templateRepository.existsByLocationKey(locationKey);
        }
        return !templateRepository.existsByLocationKeyAndIdNot(locationKey, excludeId.trim());
    }

    /**
     * 查询场馆固定信息和布局摘要。
     *
     * @param id 场馆模板ID
     * @return 场馆详情
     */
    @Transactional(readOnly = true)
    public VenueDetail get(String id) {
        return toDetail(requireTemplate(id));
    }

    /**
     * 查询场馆完整布局。
     *
     * @param id 场馆模板ID
     * @return 场馆布局
     */
    @Transactional
    public VenueLayout getLayout(String id) {
        VenueTemplateEntity template = requireTemplateForUpdate(id);
        List<ElementInput> elements = elementRepository
                .findAllByVenueTemplateIdOrderByStartRowAscStartColumnAsc(id)
                .stream()
                .map(this::toElement)
                .toList();
        return new VenueLayout(
                template.getId(), template.getLocation(), template.getManualCapacity(),
                template.getGridRows(), template.getGridColumns(), template.getSeatCount(),
                template.getRowVersion(), elements
        );
    }

    /**
     * 创建公共场馆模板。
     *
     * @param request 场馆创建请求
     * @return 新建场馆详情
     */
    @Transactional
    public VenueDetail create(CreateVenueRequest request) {
        String displayLocation = request.location().trim();
        String locationKey = displayLocation.toLowerCase(Locale.ROOT);
        if (templateRepository.existsByLocationKey(locationKey)) {
            throw duplicateLocation();
        }
        VenueLayoutValidator.ValidationResult layout =
                layoutValidator.validate(request.gridRows(), request.gridColumns(), request.elements());
        VenueTemplateEntity template = new VenueTemplateEntity();
        template.setLocation(displayLocation);
        template.setLocationKey(locationKey);
        applyInfo(request, template);
        template.setGridRows(request.gridRows());
        template.setGridColumns(request.gridColumns());
        template.setSeatCount(layout.seatCount());
        try {
            templateRepository.save(template);
        } catch (DuplicateKeyException exception) {
            throw duplicateLocation();
        }
        saveElements(template.getId(), layout.elements());
        return toDetail(template);
    }

    /**
     * 更新公共场馆固定信息。
     *
     * @param id 场馆模板ID
     * @param request 固定信息更新请求
     * @return 更新后的场馆详情
     */
    @Transactional
    public VenueDetail updateInfo(String id, UpdateVenueInfoRequest request) {
        VenueTemplateEntity template = requireTemplateForUpdate(id);
        String displayLocation = request.location().trim();
        String locationKey = displayLocation.toLowerCase(Locale.ROOT);
        if (templateRepository.existsByLocationKeyAndIdNot(locationKey, id)) {
            throw duplicateLocation();
        }
        template.setLocation(displayLocation);
        template.setLocationKey(locationKey);
        applyInfo(request, template);
        try {
            templateRepository.updateInfoWithVersion(template, request.rowVersion());
        } catch (DuplicateKeyException exception) {
            throw duplicateLocation();
        }
        return toDetail(template);
    }

    /**
     * 更新公共场馆布局。
     *
     * @param id 场馆模板ID
     * @param request 布局更新请求
     * @return 更新后的场馆布局
     */
    @Transactional
    public VenueLayout updateLayout(String id, UpdateVenueLayoutRequest request) {
        VenueLayoutValidator.ValidationResult layout =
                layoutValidator.validate(request.gridRows(), request.gridColumns(), request.elements());
        VenueTemplateEntity template = requireTemplateForUpdate(id);
        template.setGridRows(request.gridRows());
        template.setGridColumns(request.gridColumns());
        template.setSeatCount(layout.seatCount());
        templateRepository.updateLayoutWithVersion(template, request.rowVersion());
        elementRepository.deleteAllByVenueTemplateId(id);
        saveElements(id, layout.elements());
        return new VenueLayout(
                template.getId(), template.getLocation(), template.getManualCapacity(),
                template.getGridRows(), template.getGridColumns(), template.getSeatCount(),
                template.getRowVersion(), layout.elements()
        );
    }

    /**
     * 物理删除公共场馆模板并解除会议逻辑关联。
     *
     * @param id 场馆模板ID
     */
    @Transactional
    public void delete(String id) {
        VenueTemplateEntity template = requireTemplateForUpdate(id);
        meetingRepository.clearVenueTemplateId(id);
        elementRepository.deleteAllByVenueTemplateId(id);
        templateRepository.delete(template);
    }

    private VenueTemplateEntity requireTemplate(String id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "场馆模板不存在"));
    }

    private VenueTemplateEntity requireTemplateForUpdate(String id) {
        return templateRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "场馆模板不存在"));
    }

    private void saveElements(String templateId, List<ElementInput> elements) {
        List<VenueElementEntity> entities = elements.stream().map(source -> {
            VenueElementEntity target = new VenueElementEntity();
            target.setVenueTemplateId(templateId);
            target.setElementKind(ElementKind.valueOf(source.kind()));
            target.setElementName(source.name());
            target.setStartRow(source.row());
            target.setStartColumn(source.column());
            target.setRowSpan(source.rowSpan());
            target.setColumnSpan(source.columnSpan());
            target.setFillColor(source.fillColor());
            target.setBorderColor(source.borderColor());
            return target;
        }).toList();
        elementRepository.saveBatch(entities);
    }

    private void applyInfo(CreateVenueRequest source, VenueTemplateEntity target) {
        target.setCampus(blankToNull(source.campus()));
        target.setMainScreenResolution(blankToNull(source.mainScreenResolution()));
        target.setStageDimensions(blankToNull(source.stageDimensions()));
        target.setManualCapacity(source.manualCapacity());
        target.setContactInfo(blankToNull(source.contactInfo()));
        target.setBookingUrl(normalizeBookingUrl(source.bookingUrl()));
        target.setMeetingRoomFunctions(blankToNull(source.meetingRoomFunctions()));
        target.setServicesProvided(blankToNull(source.servicesProvided()));
        target.setDescription(blankToNull(source.description()));
        target.setRemarks(blankToNull(source.remarks()));
    }

    private void applyInfo(UpdateVenueInfoRequest source, VenueTemplateEntity target) {
        target.setCampus(blankToNull(source.campus()));
        target.setMainScreenResolution(blankToNull(source.mainScreenResolution()));
        target.setStageDimensions(blankToNull(source.stageDimensions()));
        target.setManualCapacity(source.manualCapacity());
        target.setContactInfo(blankToNull(source.contactInfo()));
        target.setBookingUrl(normalizeBookingUrl(source.bookingUrl()));
        target.setMeetingRoomFunctions(blankToNull(source.meetingRoomFunctions()));
        target.setServicesProvided(blankToNull(source.servicesProvided()));
        target.setDescription(blankToNull(source.description()));
        target.setRemarks(blankToNull(source.remarks()));
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeBookingUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw invalidBookingUrl();
        }
        String normalized = value.trim();
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute()
                    || scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()) {
                throw invalidBookingUrl();
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw invalidBookingUrl();
        }
    }

    private VenueSummary toSummary(VenueTemplateEntity source) {
        return new VenueSummary(
                source.getId(), source.getLocation(), source.getCampus(),
                source.getManualCapacity(), source.getSeatCount(), source.getSeatCount() > 0,
                source.getUpdatedByName(), source.getUpdatedAt(), source.getRowVersion()
        );
    }

    private VenueDetail toDetail(VenueTemplateEntity source) {
        return new VenueDetail(
                source.getId(), source.getLocation(), source.getCampus(),
                source.getMainScreenResolution(), source.getStageDimensions(),
                source.getManualCapacity(), source.getSeatCount(), source.getContactInfo(),
                source.getBookingUrl(), source.getMeetingRoomFunctions(),
                source.getServicesProvided(), source.getDescription(), source.getRemarks(),
                source.getGridRows(), source.getGridColumns(), source.getCreatedByName(),
                source.getCreatedAt(), source.getUpdatedByName(), source.getUpdatedAt(),
                source.getRowVersion()
        );
    }

    private ElementInput toElement(VenueElementEntity source) {
        return new ElementInput(
                source.getElementKind().name(), source.getElementName(), source.getStartRow(),
                source.getStartColumn(), source.getRowSpan(), source.getColumnSpan(),
                source.getFillColor(), source.getBorderColor()
        );
    }

    private ApiException duplicateLocation() {
        return new ApiException(HttpStatus.CONFLICT, "场馆地点已存在");
    }

    private ApiException invalidBookingUrl() {
        return new ApiException(HttpStatus.BAD_REQUEST, "预定链接必须是绝对 http/https URL");
    }
}
