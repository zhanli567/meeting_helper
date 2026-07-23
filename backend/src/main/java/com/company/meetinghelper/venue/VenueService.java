package com.company.meetinghelper.venue;

import com.company.meetinghelper.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VenueService {
    private final VenueTemplateRepository templateRepository;
    private final VenueElementRepository elementRepository;

    public VenueService(VenueTemplateRepository templateRepository, VenueElementRepository elementRepository) {
        this.templateRepository = templateRepository;
        this.elementRepository = elementRepository;
    }

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

    @Transactional
    public VenueDetail create(CreateVenueRequest request) {
        var template = new VenueTemplateEntity();
        template.setName(request.name());
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

    public record VenueSummary(
            String id,
            String name,
            String description,
            int gridRows,
            int gridColumns,
            int versionNo,
            boolean preset,
            long seatCount
    ) {
    }

    public record VenueDetail(
            String id,
            String name,
            String description,
            int gridRows,
            int gridColumns,
            int cellSize,
            int versionNo,
            boolean preset,
            String frontDirection,
            List<ElementInput> elements
    ) {
    }

    public record CreateVenueRequest(
            @NotBlank String name,
            String description,
            @Min(1) int gridRows,
            @Min(1) int gridColumns,
            @Min(20) int cellSize,
            @NotBlank String frontDirection,
            @NotEmpty List<@Valid ElementInput> elements
    ) {
    }

    public record ElementInput(
            @NotBlank String type,
            String code,
            String label,
            @Min(1) int row,
            @Min(1) int column,
            @Min(1) int rowSpan,
            @Min(1) int columnSpan,
            int rotation,
            int capacity,
            boolean assignable,
            boolean walkable,
            String groupCode,
            String groupLabel,
            Integer sequenceNo,
            String backgroundColor,
            String borderColor
    ) {
    }
}

