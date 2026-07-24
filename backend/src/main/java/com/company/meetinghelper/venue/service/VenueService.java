package com.company.meetinghelper.venue.service;

import com.company.meetinghelper.common.exception.ApiException;
import com.company.meetinghelper.venue.api.dto.ElementInput;
import com.company.meetinghelper.venue.api.dto.request.CreateVenueRequest;
import com.company.meetinghelper.venue.api.dto.response.VenueDetail;
import com.company.meetinghelper.venue.api.dto.response.VenueSummary;
import com.company.meetinghelper.venue.entity.ElementType;
import com.company.meetinghelper.venue.entity.FrontDirection;
import com.company.meetinghelper.venue.entity.VenueElementEntity;
import com.company.meetinghelper.venue.entity.VenueTemplateEntity;
import com.company.meetinghelper.venue.repository.VenueElementRepository;
import com.company.meetinghelper.venue.repository.VenueTemplateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VenueService {
    private final VenueTemplateRepository templateRepository;
    private final VenueElementRepository elementRepository;

    /**
     * 创建场馆模板服务。
     *
     * @param templateRepository 场馆模板仓储
     * @param elementRepository 场馆元素仓储
     */
    public VenueService(VenueTemplateRepository templateRepository, VenueElementRepository elementRepository) {
        this.templateRepository = templateRepository;
        this.elementRepository = elementRepository;
    }

    /**
     * 查询全部有效场馆模板。
     *
     * @return 场馆模板摘要列表
     */
    @Transactional(readOnly = true)
    public List<VenueSummary> list() {
        return templateRepository.findAllByDeletedFalseOrderByPresetDescNameAsc().stream()
                .map(template -> new VenueSummary(
                        template.getId(), template.getName(), template.getDescription(), template.getGridRows(),
                        template.getGridColumns(), template.getVersionNo(), template.isPreset(),
                        elementRepository
                                .findAllByVenueTemplateIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(template.getId())
                                .stream().filter(VenueElementEntity::isAssignable).count()))
                .toList();
    }

    /**
     * 查询场馆模板及其元素详情。
     *
     * @param id 场馆模板ID
     * @return 场馆模板详情
     */
    @Transactional(readOnly = true)
    public VenueDetail get(String id) {
        var template = templateRepository.findById(id)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "场馆模板不存在"));
        return new VenueDetail(
                template.getId(), template.getName(), template.getDescription(), template.getGridRows(),
                template.getGridColumns(), template.getCellSize(), template.getVersionNo(), template.isPreset(),
                template.getFrontDirection().name(),
                elementRepository.findAllByVenueTemplateIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(id).stream()
                        .map(this::toElement).toList());
    }

    /**
     * 创建自定义场馆模板。
     *
     * @param request 场馆模板请求
     * @return 新建场馆模板详情
     */
    @Transactional
    public VenueDetail create(CreateVenueRequest request) {
        var normalizedName = request.name().trim();
        if (templateRepository.existsByNameIgnoreCaseAndDeletedFalse(normalizedName)) {
            throw new ApiException(HttpStatus.CONFLICT, "场馆名称已存在");
        }
        var template = new VenueTemplateEntity();
        template.setName(normalizedName);
        template.setDescription(request.description());
        template.setGridRows(request.gridRows());
        template.setGridColumns(request.gridColumns());
        template.setCellSize(request.cellSize());
        template.setVersionNo(1);
        template.setPreset(false);
        template.setFrontDirection(FrontDirection.valueOf(request.frontDirection()));
        templateRepository.save(template);
        var elements = request.elements().stream().map(source -> {
            var target = new VenueElementEntity();
            target.setVenueTemplateId(template.getId());
            applyElement(source, target);
            return target;
        }).toList();
        elementRepository.saveAll(elements);
        return get(template.getId());
    }

    /**
     * 更新自定义场馆模板并提升版本号。
     *
     * @param id 场馆模板ID
     * @param request 场馆模板请求
     * @return 更新后的场馆模板详情
     */
    @Transactional
    public VenueDetail update(String id, CreateVenueRequest request) {
        var template = templateRepository.findById(id)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "场馆模板不存在"));
        if (template.isPreset()) {
            throw new ApiException(HttpStatus.CONFLICT, "系统预置场馆不允许修改");
        }
        var normalizedName = request.name().trim();
        if (templateRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot(normalizedName, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "场馆名称已存在");
        }
        template.setName(normalizedName);
        template.setDescription(request.description());
        template.setGridRows(request.gridRows());
        template.setGridColumns(request.gridColumns());
        template.setCellSize(request.cellSize());
        template.setVersionNo(template.getVersionNo() + 1);
        template.setFrontDirection(FrontDirection.valueOf(request.frontDirection()));
        templateRepository.save(template);

        var oldElements = elementRepository
                .findAllByVenueTemplateIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(id);
        oldElements.forEach(element -> element.setDeleted(true));
        elementRepository.saveAll(oldElements);
        var newElements = request.elements().stream().map(source -> {
            var target = new VenueElementEntity();
            target.setVenueTemplateId(id);
            applyElement(source, target);
            return target;
        }).toList();
        elementRepository.saveAll(newElements);
        return get(id);
    }

    /**
     * 软删除自定义场馆模板及其元素。
     *
     * @param id 场馆模板ID
     */
    @Transactional
    public void delete(String id) {
        var template = templateRepository.findById(id)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "场馆模板不存在"));
        if (template.isPreset()) {
            throw new ApiException(HttpStatus.CONFLICT, "系统预置场馆不允许删除");
        }
        template.setDeleted(true);
        templateRepository.save(template);
        var elements = elementRepository
                .findAllByVenueTemplateIdAndDeletedFalseOrderByGridRowAscGridColumnAsc(id);
        elements.forEach(element -> element.setDeleted(true));
        elementRepository.saveAll(elements);
    }

    private void applyElement(ElementInput source, VenueElementEntity target) {
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
    }

    private ElementInput toElement(VenueElementEntity source) {
        return new ElementInput(
                source.getElementType().name(), source.getCode(), source.getLabel(), source.getGridRow(),
                source.getGridColumn(), source.getRowSpan(), source.getColumnSpan(), source.getRotation(),
                source.getCapacity(), source.isAssignable(), source.isWalkable(), source.getGroupCode(),
                source.getGroupLabel(), source.getSequenceNo(), source.getBackgroundColor(), source.getBorderColor());
    }

}
