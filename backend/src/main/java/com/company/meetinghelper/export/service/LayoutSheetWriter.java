package com.company.meetinghelper.export.service;

import com.company.meetinghelper.export.api.dto.request.ExportExcelRequest;
import com.company.meetinghelper.seating.service.SeatLabelService;
import com.company.meetinghelper.venue.entity.ElementKind;
import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * LayoutSheetWriter 类。
 */
@Component
public class LayoutSheetWriter {
    private static final int TOP_OFFSET = 2;
    private static final int LEFT_OFFSET = 2;
    private static final int ROW_GAP = 0;
    private static final int ROW_HEIGHT_POINTS = 24;

    private final SeatLabelService seatLabelService;

    public LayoutSheetWriter(SeatLabelService seatLabelService) {
        this.seatLabelService = seatLabelService;
    }

/**
 * write 方法。
 * @param workbook workbook 参数。
 * @param workspace workspace 参数。
 * @param options options 参数。
 */
public void write(
            XSSFWorkbook workbook,
            WorkspaceResponse workspace,
            ExportExcelRequest.LayoutSheet options
    ) {
        XSSFSheet sheet = workbook.createSheet("排座图");
        sheet.setDisplayGridlines(false);
        List<WorkspaceResponse.ElementView> elements = layoutElements(workspace);
        if (elements.isEmpty()) {
            sheet.createRow(TOP_OFFSET).createCell(LEFT_OFFSET).setCellValue("暂无排座图");
            return;
        }

        LayoutRenderContext context = layoutRenderContext(workbook, workspace, options, elements);
        configureDimensions(sheet, context.measured());
        renderRowAndFieldLabels(
                sheet,
                context.measured(),
                elements,
                context.selectedFields(),
                context.blockByElement(),
                context.displayRowByCanvasRow(),
                context.styles()
        );
        renderSeats(
                sheet,
                context.measured(),
                elements,
                context.selectedFields(),
                context.blockByElement(),
                context.itemByElement(),
                context.participantById(),
                context.seatLabels(),
                options,
                context.colorsByFieldValue(),
                context.styles()
        );
        renderNonSeatElements(sheet, context.measured(), elements, context.styles());
    }

    private List<WorkspaceResponse.ElementView> layoutElements(WorkspaceResponse workspace) {
        return workspace.layout().elements() == null ? List.of() : workspace.layout().elements();
    }

    private LayoutRenderContext layoutRenderContext(
            XSSFWorkbook workbook,
            WorkspaceResponse workspace,
            ExportExcelRequest.LayoutSheet options,
            List<WorkspaceResponse.ElementView> elements
    ) {
        List<WorkspaceResponse.FieldDefinitionView> selectedFields = selectedFields(workspace, options);
        Map<String, WorkspaceResponse.ParticipantView> participantById = workspace.participants().stream()
                .collect(Collectors.toMap(WorkspaceResponse.ParticipantView::id, Function.identity()));
        Map<String, WorkspaceResponse.PlanItemView> itemByElement = itemsByElement(workspace);
        Map<String, SeatBlock> blockByElement = seatBlocks(
                elements,
                selectedFields,
                itemByElement,
                participantById
        );
        MeasuredLayout measured = measure(elements, selectedFields.size(), blockByElement);
        StyleCache styles = new StyleCache(workbook);
        Map<String, Map<String, String>> colorsByFieldValue = colorsByFieldValue(
                elements,
                selectedFields,
                options,
                workspace.participants(),
                workspace.items()
        );
        Map<String, String> seatLabels = paddedSeatLabels(elements);
        Map<Integer, Integer> displayRowByCanvasRow = seatLabelService.rowLabels(elements).stream()
                .collect(Collectors.toMap(
                        SeatLabelService.RowLabel::sourceRow,
                        SeatLabelService.RowLabel::displayRow
                ));
        return new LayoutRenderContext(
                selectedFields,
                participantById,
                itemByElement,
                blockByElement,
                measured,
                styles,
                colorsByFieldValue,
                seatLabels,
                displayRowByCanvasRow
        );
    }

    private record LayoutRenderContext(
            List<WorkspaceResponse.FieldDefinitionView> selectedFields,
            Map<String, WorkspaceResponse.ParticipantView> participantById,
            Map<String, WorkspaceResponse.PlanItemView> itemByElement,
            Map<String, SeatBlock> blockByElement,
            MeasuredLayout measured,
            StyleCache styles,
            Map<String, Map<String, String>> colorsByFieldValue,
            Map<String, String> seatLabels,
            Map<Integer, Integer> displayRowByCanvasRow
    ) {
    }

    private List<WorkspaceResponse.FieldDefinitionView> selectedFields(
            WorkspaceResponse workspace,
            ExportExcelRequest.LayoutSheet options
    ) {
        if (workspace.fieldDefinitions() == null || options.fieldCodes().isEmpty()) {
            return List.of();
        }
        Set<String> selectedCodes = new LinkedHashSet<>(options.fieldCodes());
        return workspace.fieldDefinitions().stream()
                .filter(field -> !"name".equals(field.code()) && !"employeeNo".equals(field.code()))
                .filter(field -> selectedCodes.contains(field.code()))
                .toList();
    }

    private Map<String, WorkspaceResponse.PlanItemView> itemsByElement(WorkspaceResponse workspace) {
        LinkedHashMap<String, WorkspaceResponse.PlanItemView> result =
                new LinkedHashMap<String, WorkspaceResponse.PlanItemView>();
        if (workspace.items() != null) {
            workspace.items().forEach(item -> {
                if (item.targetElementIds() != null) {
                    item.targetElementIds().forEach(elementId -> result.put(elementId, item));
                }
            });
        }
        return result;
    }

    private Set<String> assignedParticipantIds(List<WorkspaceResponse.PlanItemView> items) {
        if (items == null) {
            return Set.of();
        }
        return items.stream()
                .filter(item -> "PERSON".equals(item.type()))
                .filter(item -> item.participantId() != null)
                .filter(item -> item.targetElementIds() != null && !item.targetElementIds().isEmpty())
                .map(WorkspaceResponse.PlanItemView::participantId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<String, SeatBlock> seatBlocks(
            List<WorkspaceResponse.ElementView> elements,
            List<WorkspaceResponse.FieldDefinitionView> selectedFields,
            Map<String, WorkspaceResponse.PlanItemView> itemByElement,
            Map<String, WorkspaceResponse.ParticipantView> participantById
    ) {
        LinkedHashMap<String, SeatBlock> result = new LinkedHashMap<String, SeatBlock>();
        for (WorkspaceResponse.ElementView element : elements) {
            if (!isSeat(element)) {
                continue;
            }
            WorkspaceResponse.PlanItemView item = itemByElement.get(element.id());
            WorkspaceResponse.ParticipantView participant = item == null || item.participantId() == null
                    ? null
                    : participantById.get(item.participantId());
            result.put(element.id(), seatBlock(participant, selectedFields));
        }
        return result;
    }

    private SeatBlock seatBlock(
            WorkspaceResponse.ParticipantView participant,
            List<WorkspaceResponse.FieldDefinitionView> selectedFields
    ) {
        if (selectedFields.isEmpty()) {
            return new SeatBlock(List.of(), 2);
        }
        if (participant == null) {
            return new SeatBlock(emptyFieldRuns(selectedFields), 2 + selectedFields.size());
        }
        List<Map<String, String>> recordAttributes =
                participant.records() == null || participant.records().isEmpty()
                        ? List.of(Map.of())
                        : participant.records().stream()
                                .sorted(Comparator.comparingInt(
                                        WorkspaceResponse.ParticipantRecordView::recordOrder
                                ))
                                .map(record -> record.attributes() == null
                                        ? Map.<String, String>of()
                                        : record.attributes())
                                .toList();
        ArrayList<FieldRun> runs = new ArrayList<FieldRun>();
        for (WorkspaceResponse.FieldDefinitionView field : selectedFields) {
            LinkedHashSet<String> values = new LinkedHashSet<String>();
            for (Map<String, String> record : recordAttributes) {
                String value = normalizeFieldValue(record.get(field.code()));
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
            if (values.isEmpty()) {
                values.add("");
            }
            values.forEach(value -> runs.add(new FieldRun(field.code(), field.label(), value, 1)));
        }
        int dynamicHeight = runs.stream().mapToInt(FieldRun::height).sum();
        return new SeatBlock(List.copyOf(runs), 2 + dynamicHeight);
    }

    private List<FieldRun> emptyFieldRuns(
            List<WorkspaceResponse.FieldDefinitionView> selectedFields
    ) {
        return selectedFields.stream()
                .map(field -> new FieldRun(field.code(), field.label(), "", 1))
                .toList();
    }

    private MeasuredLayout measure(
            List<WorkspaceResponse.ElementView> elements,
            int selectedFieldCount,
            Map<String, SeatBlock> blockByElement
    ) {
        CanvasBounds bounds = bounds(elements);
        int baseHeight = baseSeatBlockHeight(selectedFieldCount);
        LinkedHashMap<Integer, Integer> rowHeights = new LinkedHashMap<Integer, Integer>();
        for (int canvasRow = bounds.top(); canvasRow <= bounds.bottom(); canvasRow++) {
            int currentCanvasRow = canvasRow;
            int measuredHeight = elements.stream()
                    .filter(this::isSeat)
                    .filter(element -> element.row() == currentCanvasRow)
                    .filter(element -> element.rowSpan() == 1)
                    .map(element -> blockByElement.get(element.id()))
                    .filter(java.util.Objects::nonNull)
                    .mapToInt(SeatBlock::height)
                    .max()
                    .orElse(baseHeight);
            rowHeights.put(canvasRow, Math.max(baseHeight, measuredHeight));
        }
        elements.stream()
                .filter(this::isSeat)
                .filter(element -> element.rowSpan() > 1)
                .sorted(Comparator.comparingInt(WorkspaceResponse.ElementView::row)
                        .thenComparingInt(WorkspaceResponse.ElementView::column))
                .forEach(element -> {
                    SeatBlock block = blockByElement.get(element.id());
                    if (block == null) {
                        return;
                    }
                    int coveredHeight = ROW_GAP * (element.rowSpan() - 1);
                    for (int canvasRow = element.row();
                            canvasRow < element.row() + element.rowSpan();
                            canvasRow++) {
                        coveredHeight += rowHeights.get(canvasRow);
                    }
                    int overflow = block.height() - coveredHeight;
                    if (overflow > 0) {
                        rowHeights.compute(element.row(), (ignored, height) -> height + overflow);
                    }
                });

        LinkedHashMap<Integer, Integer> excelRowByCanvasRow = new LinkedHashMap<Integer, Integer>();
        int excelRow = TOP_OFFSET;
        for (int canvasRow = bounds.top(); canvasRow <= bounds.bottom(); canvasRow++) {
            excelRowByCanvasRow.put(canvasRow, excelRow);
            excelRow += rowHeights.get(canvasRow) + ROW_GAP;
        }

        LinkedHashMap<Integer, Integer> excelColumnByCanvasColumn =
                new LinkedHashMap<Integer, Integer>();
        for (int canvasColumn = bounds.left(); canvasColumn <= bounds.right(); canvasColumn++) {
            excelColumnByCanvasColumn.put(
                    canvasColumn,
                    LEFT_OFFSET + 2 + (canvasColumn - bounds.left())
            );
        }
        int canvasWidth = bounds.right() - bounds.left() + 1;
        return new MeasuredLayout(
                bounds,
                excelRowByCanvasRow,
                rowHeights,
                excelColumnByCanvasColumn,
                TOP_OFFSET,
                LEFT_OFFSET,
                LEFT_OFFSET,
                LEFT_OFFSET + 1,
                LEFT_OFFSET + 2 + canvasWidth
        );
    }

    private CanvasBounds bounds(List<WorkspaceResponse.ElementView> elements) {
        int top = elements.stream().mapToInt(WorkspaceResponse.ElementView::row).min().orElse(1);
        int bottom = elements.stream()
                .mapToInt(element -> element.row() + element.rowSpan() - 1)
                .max()
                .orElse(top);
        int left = elements.stream().mapToInt(WorkspaceResponse.ElementView::column).min().orElse(1);
        int right = elements.stream()
                .mapToInt(element -> element.column() + element.columnSpan() - 1)
                .max()
                .orElse(left);
        return new CanvasBounds(top, bottom, left, right);
    }

    private int baseSeatBlockHeight(int selectedFieldCount) {
        return 2 + selectedFieldCount;
    }

    private Map<String, Map<String, String>> colorsByFieldValue(
            List<WorkspaceResponse.ElementView> elements,
            List<WorkspaceResponse.FieldDefinitionView> selectedFields,
            ExportExcelRequest.LayoutSheet options,
            List<WorkspaceResponse.ParticipantView> participants,
            List<WorkspaceResponse.PlanItemView> items
    ) {
        LinkedHashSet<String> reservedColors = elements.stream()
                .map(WorkspaceResponse.ElementView::fillColor)
                .map(this::normalizeColor)
                .filter(color -> !color.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (items != null) {
            items.stream()
                    .filter(item -> "RESERVED".equals(item.type()))
                    .map(WorkspaceResponse.PlanItemView::backgroundColor)
                    .map(this::normalizeColor)
                    .filter(color -> !color.isBlank())
                    .forEach(reservedColors::add);
        }
        reservedColors.add(ExportPalette.SYSTEM_LAYOUT_COLOR);
        Set<String> assignedParticipantIds = assignedParticipantIds(items);
        Set<String> colorFieldCodes = new LinkedHashSet<String>(options.colorFieldCodes());
        Map<String, Map<String, String>> providedStyleRules = styleRulesByFieldValue(options);
        LinkedHashMap<String, Map<String, String>> result =
                new LinkedHashMap<String, Map<String, String>>();
        for (WorkspaceResponse.FieldDefinitionView field : selectedFields) {
            if (!colorFieldCodes.contains(field.code())) {
                continue;
            }
            LinkedHashSet<String> values = new LinkedHashSet<String>();
            for (WorkspaceResponse.ParticipantView participant : participants) {
                if (!assignedParticipantIds.contains(participant.id())) {
                    continue;
                }
                if (participant.records() == null || participant.records().isEmpty()) {
                    String value = participant.primaryAttributes() == null
                            ? ""
                            : normalizeFieldValue(participant.primaryAttributes().get(field.code()));
                    if (!value.isBlank()) {
                        values.add(value);
                    }
                    continue;
                }
                participant.records().stream()
                        .sorted(Comparator.comparingInt(
                                WorkspaceResponse.ParticipantRecordView::recordOrder
                        ))
                        .map(record -> record.attributes() == null
                                ? ""
                                : normalizeFieldValue(record.attributes().get(field.code())))
                        .filter(value -> !value.isBlank())
                        .forEach(values::add);
            }
            LinkedHashMap<String, String> colors = new LinkedHashMap<String, String>();
            Map<String, String> providedColors = providedStyleRules.getOrDefault(field.code(), Map.of());
            LinkedHashSet<String> missingValues = new LinkedHashSet<String>();
            for (String value : values) {
                String providedColor = normalizeColor(providedColors.get(value));
                if (providedColor.isBlank() || reservedColors.contains(providedColor)) {
                    missingValues.add(value);
                    continue;
                }
                colors.put(value, providedColor);
                reservedColors.add(providedColor);
            }
            colors.putAll(ExportPalette.colorsByValue(missingValues, reservedColors));
            result.put(field.code(), colors);
            reservedColors.addAll(colors.values());
        }
        return result;
    }

    private Map<String, Map<String, String>> styleRulesByFieldValue(ExportExcelRequest.LayoutSheet options) {
        LinkedHashMap<String, Map<String, String>> result =
                new LinkedHashMap<String, Map<String, String>>();
        for (ExportExcelRequest.StyleRule rule : options.styleRules()) {
            String fieldCode = nullToEmpty(rule.fieldCode()).trim();
            String value = nullToEmpty(rule.value()).trim();
            String color = normalizeColor(rule.backgroundColor());
            if (fieldCode.isBlank() || value.isBlank() || color.isBlank()) {
                continue;
            }
            result.computeIfAbsent(fieldCode, ignored -> new LinkedHashMap<String, String>())
                    .putIfAbsent(value, color);
        }
        return result;
    }

    private Map<String, String> paddedSeatLabels(List<WorkspaceResponse.ElementView> elements) {
        return seatLabelService.labelsByElementId(elements).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> padSeatNumber(entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private String padSeatNumber(String label) {
        int separator = label.indexOf('排');
        if (separator < 0 || separator == label.length() - 1) {
            return label;
        }
        try {
            int seatNumber = Integer.parseInt(label.substring(separator + 1));
            return label.substring(0, separator + 1) + "%02d".formatted(seatNumber);
        } catch (NumberFormatException exception) {
            return label;
        }
    }

    private void configureDimensions(XSSFSheet sheet, MeasuredLayout measured) {
        sheet.setColumnWidth(measured.leftRowLabelColumn(), 7 * 256);
        sheet.setColumnWidth(measured.fieldLabelColumn(), 14 * 256);
        for (int column : measured.excelColumnByCanvasColumn().values()) {
            sheet.setColumnWidth(column, 14 * 256);
        }
        sheet.setColumnWidth(measured.rightRowLabelColumn(), 7 * 256);
        for (Map.Entry<Integer, Integer> rowEntry : measured.excelRowByCanvasRow().entrySet()) {
            int startRow = rowEntry.getValue();
            int rowHeight = measured.rowHeights().get(rowEntry.getKey());
            for (int rowIndex = startRow; rowIndex < startRow + rowHeight; rowIndex++) {
                row(sheet, rowIndex).setHeightInPoints(ROW_HEIGHT_POINTS);
            }
        }
    }

    private void renderRowAndFieldLabels(
            XSSFSheet sheet,
            MeasuredLayout measured,
            List<WorkspaceResponse.ElementView> elements,
            List<WorkspaceResponse.FieldDefinitionView> selectedFields,
            Map<String, SeatBlock> blockByElement,
            Map<Integer, Integer> displayRowByCanvasRow,
            StyleCache styles
    ) {
        XSSFCellStyle systemStyle = styles.style(ExportPalette.SYSTEM_LAYOUT_COLOR, true, true);
        XSSFCellStyle labelStyle = styles.style("#ffffff", true, true);
        List<Integer> displayedCanvasRows = displayRowByCanvasRow.keySet().stream()
                .sorted()
                .toList();
        for (int rowIndex = 0; rowIndex < displayedCanvasRows.size(); rowIndex++) {
            int canvasRow = displayedCanvasRows.get(rowIndex);
            int startRow = measured.excelRowByCanvasRow().get(canvasRow);
            int rowHeight = elements.stream()
                    .filter(this::isSeat)
                    .filter(element -> element.row() == canvasRow)
                    .mapToInt(element -> seatSpanHeight(measured, element))
                    .max()
                    .orElse(measured.rowHeights().get(canvasRow));
            int fullEndRow = startRow + rowHeight - 1;
            int endRow = rowIndex + 1 < displayedCanvasRows.size()
                    ? Math.min(
                            fullEndRow,
                            measured.excelRowByCanvasRow().get(displayedCanvasRows.get(rowIndex + 1)) - 1
                    )
                    : fullEndRow;
            int labelBlockHeight = endRow - startRow + 1;
            String label = "第" + chineseNumber(displayRowByCanvasRow.get(canvasRow)) + "排";
            mergeAndWrite(
                    sheet,
                    startRow,
                    endRow,
                    measured.leftRowLabelColumn(),
                    measured.leftRowLabelColumn(),
                    label,
                    systemStyle
            );
            mergeAndWrite(
                    sheet,
                    startRow,
                    endRow,
                    measured.rightRowLabelColumn(),
                    measured.rightRowLabelColumn(),
                    label,
                    systemStyle
            );

            writeCell(sheet, startRow, measured.fieldLabelColumn(), "座位编号", systemStyle);
            Map<String, Integer> fieldHeights = fieldLabelHeights(
                    elements,
                    canvasRow,
                    selectedFields,
                    blockByElement
            );
            int currentRow = startRow + 1;
            int remainingHeight = labelBlockHeight - 2;
            for (int index = 0; index < selectedFields.size(); index++) {
                WorkspaceResponse.FieldDefinitionView field = selectedFields.get(index);
                int remainingFields = selectedFields.size() - index;
                int fieldHeight = index == selectedFields.size() - 1
                        ? remainingHeight
                        : Math.min(
                                fieldHeights.getOrDefault(field.code(), 1),
                                remainingHeight - remainingFields + 1
                        );
                fieldHeight = Math.max(1, fieldHeight);
                mergeAndWrite(
                        sheet,
                        currentRow,
                        currentRow + fieldHeight - 1,
                        measured.fieldLabelColumn(),
                        measured.fieldLabelColumn(),
                        field.label(),
                        labelStyle
                );
                currentRow += fieldHeight;
                remainingHeight -= fieldHeight;
            }
            writeCell(
                    sheet,
                    endRow,
                    measured.fieldLabelColumn(),
                    "姓名",
                    labelStyle
            );
        }
    }

    private Map<String, Integer> fieldLabelHeights(
            List<WorkspaceResponse.ElementView> elements,
            int canvasRow,
            List<WorkspaceResponse.FieldDefinitionView> selectedFields,
            Map<String, SeatBlock> blockByElement
    ) {
        LinkedHashMap<String, Integer> heights = selectedFields.stream()
                .collect(Collectors.toMap(
                        WorkspaceResponse.FieldDefinitionView::code,
                        ignored -> 1,
                        Math::max,
                        LinkedHashMap::new
                ));
        elements.stream()
                .filter(this::isSeat)
                .filter(element -> element.row() == canvasRow)
                .map(element -> blockByElement.get(element.id()))
                .filter(java.util.Objects::nonNull)
                .forEach(block -> selectedFields.forEach(field -> {
                    int height = block.fieldRuns().stream()
                            .filter(run -> field.code().equals(run.fieldCode()))
                            .mapToInt(FieldRun::height)
                            .sum();
                    heights.compute(field.code(), (ignored, current) -> Math.max(current, height));
                }));
        return heights;
    }

    private void renderSeats(
            XSSFSheet sheet,
            MeasuredLayout measured,
            List<WorkspaceResponse.ElementView> elements,
            List<WorkspaceResponse.FieldDefinitionView> selectedFields,
            Map<String, SeatBlock> blockByElement,
            Map<String, WorkspaceResponse.PlanItemView> itemByElement,
            Map<String, WorkspaceResponse.ParticipantView> participantById,
            Map<String, String> seatLabels,
            ExportExcelRequest.LayoutSheet options,
            Map<String, Map<String, String>> colorsByFieldValue,
            StyleCache styles
    ) {
        XSSFCellStyle systemStyle = styles.style(ExportPalette.SYSTEM_LAYOUT_COLOR, true, true);
        Set<String> colorFieldCodes = new LinkedHashSet<String>(options.colorFieldCodes());
        Set<String> renderedReservedSeatIds = new LinkedHashSet<String>();
        Set<Integer> renderedFieldRows = new LinkedHashSet<Integer>();
        for (WorkspaceResponse.ElementView element : elements) {
            if (!isSeat(element)) {
                continue;
            }
            WorkspaceResponse.PlanItemView item = itemByElement.get(element.id());
            if (isReserved(item)) {
                if (!renderedReservedSeatIds.contains(element.id())) {
                    renderedReservedSeatIds.addAll(
                            renderReservedSeats(sheet, measured, elements, item, styles)
                    );
                }
                continue;
            }
            int startRow = measured.excelRowByCanvasRow().get(element.row());
            int seatHeight = seatSpanHeight(measured, element);
            int endRow = startRow + seatHeight - 1;
            int startColumn = measured.excelColumnByCanvasColumn().get(element.column());
            int endColumn = measured.excelColumnByCanvasColumn().get(
                    element.column() + element.columnSpan() - 1
            );
            mergeAndWrite(
                    sheet,
                    startRow,
                    startRow,
                    startColumn,
                    endColumn,
                    seatLabels.getOrDefault(element.id(), nullToEmpty(element.name())),
                    systemStyle
            );

            if (renderedFieldRows.add(element.row())) {
                renderSeatFieldCells(
                        sheet,
                        measured,
                        elements,
                        element.row(),
                        selectedFields,
                        blockByElement,
                        itemByElement,
                        colorFieldCodes,
                        colorsByFieldValue,
                        styles
                );
            }
            Map<String, Integer> fieldHeights = fieldLabelHeights(
                    elements,
                    element.row(),
                    selectedFields,
                    blockByElement
            );
            int currentRow = startRow + 1 + fieldHeights.values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            while (currentRow < endRow) {
                mergeAndWrite(
                        sheet,
                        currentRow,
                        currentRow,
                        startColumn,
                        endColumn,
                        "",
                        styles.style("#ffffff", true, false)
                );
                currentRow++;
            }

            WorkspaceResponse.ParticipantView participant = item == null || item.participantId() == null
                    ? null
                    : participantById.get(item.participantId());
            String name = participant != null
                    ? participant.name()
                    : item == null ? "" : nullToEmpty(item.label());
            String nameFill = participant == null && item != null && "RESERVED".equals(item.type())
                    ? normalizeColor(item.backgroundColor())
                    : normalizeColor(element.fillColor());
            mergeAndWrite(
                    sheet,
                    endRow,
                    endRow,
                    startColumn,
                    endColumn,
                    name,
                    styles.style(nameFill, true, participant != null)
            );
        }
    }

    private void renderSeatFieldCells(
            XSSFSheet sheet,
            MeasuredLayout measured,
            List<WorkspaceResponse.ElementView> elements,
            int canvasRow,
            List<WorkspaceResponse.FieldDefinitionView> selectedFields,
            Map<String, SeatBlock> blockByElement,
            Map<String, WorkspaceResponse.PlanItemView> itemByElement,
            Set<String> colorFieldCodes,
            Map<String, Map<String, String>> colorsByFieldValue,
            StyleCache styles
    ) {
        List<WorkspaceResponse.ElementView> rowSeats = elements.stream()
                .filter(this::isSeat)
                .filter(element -> element.row() == canvasRow)
                .filter(element -> !isReserved(itemByElement.get(element.id())))
                .sorted(Comparator.comparingInt(WorkspaceResponse.ElementView::column))
                .toList();
        if (rowSeats.isEmpty() || selectedFields.isEmpty()) {
            return;
        }
        Map<String, Integer> fieldHeights = fieldLabelHeights(
                elements,
                canvasRow,
                selectedFields,
                blockByElement
        );
        int currentRow = measured.excelRowByCanvasRow().get(canvasRow) + 1;
        for (WorkspaceResponse.FieldDefinitionView field : selectedFields) {
            int fieldHeight = Math.max(1, fieldHeights.getOrDefault(field.code(), 1));
            Map<String, List<FieldCellSpan>> spansByElement = planFieldSpans(
                    rowSeats,
                    field,
                    fieldHeight,
                    blockByElement
            );
            renderFieldSpans(
                    sheet,
                    measured,
                    rowSeats,
                    field,
                    currentRow,
                    spansByElement,
                    colorFieldCodes,
                    colorsByFieldValue,
                    styles
            );
            currentRow += fieldHeight;
        }
    }

    private Map<String, List<FieldCellSpan>> planFieldSpans(
            List<WorkspaceResponse.ElementView> rowSeats,
            WorkspaceResponse.FieldDefinitionView field,
            int fieldHeight,
            Map<String, SeatBlock> blockByElement
    ) {
        LinkedHashMap<String, List<String>> valuesByElement = new LinkedHashMap<String, List<String>>();
        for (WorkspaceResponse.ElementView element : rowSeats) {
            SeatBlock block = blockByElement.getOrDefault(
                    element.id(),
                    new SeatBlock(emptyFieldRuns(List.of(field)), 1)
            );
            List<String> values = fieldRows(block, field).stream()
                    .map(FieldRun::value)
                    .toList();
            valuesByElement.put(element.id(), values);
        }
        List<String> sharedValues = sharedAdjacentValues(rowSeats, valuesByElement);
        LinkedHashMap<String, Integer> sharedOffsets = new LinkedHashMap<String, Integer>();
        for (String value : sharedValues) {
            if (sharedOffsets.size() >= fieldHeight) {
                break;
            }
            sharedOffsets.put(value, sharedOffsets.size());
        }

        LinkedHashMap<String, List<FieldCellSpan>> result =
                new LinkedHashMap<String, List<FieldCellSpan>>();
        for (WorkspaceResponse.ElementView element : rowSeats) {
            List<String> values = valuesByElement.getOrDefault(element.id(), List.of(""));
            LinkedHashSet<String> consumed = new LinkedHashSet<String>();
            boolean[] occupied = new boolean[fieldHeight];
            ArrayList<FieldCellSpan> spans = new ArrayList<FieldCellSpan>();
            for (Map.Entry<String, Integer> entry : sharedOffsets.entrySet()) {
                String value = entry.getKey();
                int offset = entry.getValue();
                if (values.contains(value)) {
                    int height = sharedValueHeight(rowSeats, valuesByElement, element, value, offset, fieldHeight);
                    spans.add(new FieldCellSpan(field.code(), field.label(), value, offset, height));
                    consumed.add(value);
                    markOccupied(occupied, offset, height);
                }
            }

            List<String> remainingValues = values.stream()
                    .filter(value -> !value.isBlank())
                    .filter(value -> !consumed.contains(value))
                    .toList();
            List<Integer> availableOffsets = new ArrayList<Integer>();
            for (int index = 0; index < fieldHeight; index++) {
                if (!occupied[index]) {
                    availableOffsets.add(index);
                }
            }
            if (remainingValues.isEmpty()) {
                if (!availableOffsets.isEmpty()) {
                    spans.add(new FieldCellSpan(
                            field.code(),
                            field.label(),
                            "",
                            availableOffsets.get(0),
                            availableOffsets.size()
                    ));
                }
            } else {
                for (int index = 0; index < remainingValues.size() && index < availableOffsets.size(); index++) {
                    int offset = availableOffsets.get(index);
                    int height = index == remainingValues.size() - 1
                            ? availableOffsets.size() - index
                            : 1;
                    spans.add(new FieldCellSpan(
                            field.code(),
                            field.label(),
                            remainingValues.get(index),
                            offset,
                            Math.max(1, height)
                    ));
                }
            }
            result.put(element.id(), spans.stream()
                    .sorted(Comparator.comparingInt(FieldCellSpan::offset))
                    .toList());
        }
        return result;
    }

    private int sharedValueHeight(
            List<WorkspaceResponse.ElementView> rowSeats,
            Map<String, List<String>> valuesByElement,
            WorkspaceResponse.ElementView element,
            String value,
            int offset,
            int fieldHeight
    ) {
        if (fieldHeight <= offset + 1) {
            return 1;
        }
        if (!canSharedValueFillTail(rowSeats, valuesByElement, element, value)) {
            return 1;
        }
        return fieldHeight - offset;
    }

    private boolean canSharedValueFillTail(
            List<WorkspaceResponse.ElementView> rowSeats,
            Map<String, List<String>> valuesByElement,
            WorkspaceResponse.ElementView element,
            String value
    ) {
        if (nonBlankValues(valuesByElement.getOrDefault(element.id(), List.of())).size() != 1) {
            return false;
        }
        return adjacentValueGroup(rowSeats, valuesByElement, element, value).stream()
                .map(candidate -> valuesByElement.getOrDefault(candidate.id(), List.of()))
                .map(this::nonBlankValues)
                .allMatch(values -> values.size() == 1);
    }

    private List<WorkspaceResponse.ElementView> adjacentValueGroup(
            List<WorkspaceResponse.ElementView> rowSeats,
            Map<String, List<String>> valuesByElement,
            WorkspaceResponse.ElementView element,
            String value
    ) {
        int index = rowSeats.indexOf(element);
        int start = index;
        int end = index;
        while (start > 0 && hasAdjacentValue(rowSeats, valuesByElement, start - 1, start, value)) {
            start--;
        }
        while (end + 1 < rowSeats.size() && hasAdjacentValue(rowSeats, valuesByElement, end, end + 1, value)) {
            end++;
        }
        return rowSeats.subList(start, end + 1);
    }

    private boolean hasAdjacentValue(
            List<WorkspaceResponse.ElementView> rowSeats,
            Map<String, List<String>> valuesByElement,
            int leftIndex,
            int rightIndex,
            String value
    ) {
        WorkspaceResponse.ElementView left = rowSeats.get(leftIndex);
        WorkspaceResponse.ElementView right = rowSeats.get(rightIndex);
        return left.column() + left.columnSpan() == right.column()
                && valuesByElement.getOrDefault(left.id(), List.of()).contains(value)
                && valuesByElement.getOrDefault(right.id(), List.of()).contains(value);
    }

    private List<String> nonBlankValues(List<String> values) {
        return values.stream()
                .filter(value -> !value.isBlank())
                .toList();
    }

    private void markOccupied(boolean[] occupied, int offset, int height) {
        for (int index = offset; index < offset + height && index < occupied.length; index++) {
            occupied[index] = true;
        }
    }

    private List<String> sharedAdjacentValues(
            List<WorkspaceResponse.ElementView> rowSeats,
            Map<String, List<String>> valuesByElement
    ) {
        LinkedHashSet<String> shared = new LinkedHashSet<String>();
        for (int index = 0; index + 1 < rowSeats.size(); index++) {
            WorkspaceResponse.ElementView left = rowSeats.get(index);
            WorkspaceResponse.ElementView right = rowSeats.get(index + 1);
            if (left.column() + left.columnSpan() != right.column()) {
                continue;
            }
            Set<String> rightValues = valuesByElement.getOrDefault(right.id(), List.of()).stream()
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            valuesByElement.getOrDefault(left.id(), List.of()).stream()
                    .filter(value -> !value.isBlank())
                    .filter(rightValues::contains)
                    .forEach(shared::add);
        }
        return List.copyOf(shared);
    }

    private void renderFieldSpans(
            XSSFSheet sheet,
            MeasuredLayout measured,
            List<WorkspaceResponse.ElementView> rowSeats,
            WorkspaceResponse.FieldDefinitionView field,
            int fieldStartRow,
            Map<String, List<FieldCellSpan>> spansByElement,
            Set<String> colorFieldCodes,
            Map<String, Map<String, String>> colorsByFieldValue,
            StyleCache styles
    ) {
        Set<String> rendered = new LinkedHashSet<String>();
        for (WorkspaceResponse.ElementView element : rowSeats) {
            List<FieldCellSpan> spans = spansByElement.getOrDefault(element.id(), List.of());
            for (FieldCellSpan span : spans) {
                String key = spanKey(element.id(), span);
                if (rendered.contains(key)) {
                    continue;
                }
                int firstSeatIndex = rowSeats.indexOf(element);
                int lastSeatIndex = firstSeatIndex;
                String fillColor = fieldValueColor(
                        new FieldRun(field.code(), field.label(), span.value(), 1),
                        colorFieldCodes,
                        colorsByFieldValue
                );
                if (!span.value().isBlank()) {
                    for (int candidateIndex = firstSeatIndex + 1;
                            candidateIndex < rowSeats.size();
                            candidateIndex++) {
                        WorkspaceResponse.ElementView previous = rowSeats.get(candidateIndex - 1);
                        WorkspaceResponse.ElementView candidate = rowSeats.get(candidateIndex);
                        if (previous.column() + previous.columnSpan() != candidate.column()) {
                            break;
                        }
                        FieldCellSpan candidateSpan = matchingSpan(
                                spansByElement.getOrDefault(candidate.id(), List.of()),
                                span
                        );
                        if (candidateSpan == null) {
                            break;
                        }
                        rendered.add(spanKey(candidate.id(), candidateSpan));
                        lastSeatIndex = candidateIndex;
                    }
                }
                WorkspaceResponse.ElementView lastSeat = rowSeats.get(lastSeatIndex);
                int firstColumn = measured.excelColumnByCanvasColumn().get(element.column());
                int lastColumn = measured.excelColumnByCanvasColumn().get(
                        lastSeat.column() + lastSeat.columnSpan() - 1
                );
                mergeAndWrite(
                        sheet,
                        fieldStartRow + span.offset(),
                        fieldStartRow + span.offset() + span.height() - 1,
                        firstColumn,
                        lastColumn,
                        span.value(),
                        styles.style(fillColor, true, false)
                );
                rendered.add(key);
            }
        }
    }

    private FieldCellSpan matchingSpan(List<FieldCellSpan> spans, FieldCellSpan expected) {
        return spans.stream()
                .filter(span -> span.offset() == expected.offset())
                .filter(span -> span.height() == expected.height())
                .filter(span -> span.value().equals(expected.value()))
                .findFirst()
                .orElse(null);
    }

    private String spanKey(String elementId, FieldCellSpan span) {
        return elementId + ":" + span.fieldCode() + ":" + span.offset();
    }

    private List<FieldRun> fieldRows(
            SeatBlock block,
            WorkspaceResponse.FieldDefinitionView field
    ) {
        ArrayList<FieldRun> rows = new ArrayList<FieldRun>();
        block.fieldRuns().stream()
                .filter(run -> field.code().equals(run.fieldCode()))
                .forEach(run -> {
                    int height = Math.max(1, run.height());
                    for (int index = 0; index < height; index++) {
                        rows.add(new FieldRun(run.fieldCode(), run.label(), run.value(), 1));
                    }
                });
        if (rows.isEmpty()) {
            rows.add(new FieldRun(field.code(), field.label(), "", 1));
        }
        return rows;
    }

    private Set<String> renderReservedSeats(
            XSSFSheet sheet,
            MeasuredLayout measured,
            List<WorkspaceResponse.ElementView> elements,
            WorkspaceResponse.PlanItemView item,
            StyleCache styles
    ) {
        Set<String> targetIds = item.targetElementIds() == null
                ? Set.of()
                : new LinkedHashSet<String>(item.targetElementIds());
        List<WorkspaceResponse.ElementView> seats = elements.stream()
                .filter(this::isSeat)
                .filter(element -> targetIds.contains(element.id()))
                .sorted(Comparator.comparingInt(WorkspaceResponse.ElementView::row)
                        .thenComparingInt(WorkspaceResponse.ElementView::column))
                .toList();
        LinkedHashSet<String> renderedIds = seats.stream()
                .map(WorkspaceResponse.ElementView::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (seats.isEmpty()) {
            return renderedIds;
        }
        XSSFCellStyle style = styles.style(item.backgroundColor(), true, item.bold());
        boolean wroteLabel = false;
        for (List<WorkspaceResponse.ElementView> component : connectedSeatComponents(seats)) {
            String label = wroteLabel ? "" : nullToEmpty(item.label());
            if (isSolidRectangle(component)) {
                renderReservedRectangle(sheet, measured, component, label, style);
                wroteLabel = true;
            } else {
                wroteLabel = renderReservedRuns(sheet, measured, component, label, style) || wroteLabel;
            }
        }
        return renderedIds;
    }

    private List<List<WorkspaceResponse.ElementView>> connectedSeatComponents(
            List<WorkspaceResponse.ElementView> seats
    ) {
        ArrayList<List<WorkspaceResponse.ElementView>> components =
                new ArrayList<List<WorkspaceResponse.ElementView>>();
        LinkedHashSet<String> pending = seats.stream()
                .map(WorkspaceResponse.ElementView::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, WorkspaceResponse.ElementView> seatById = seats.stream()
                .collect(Collectors.toMap(
                        WorkspaceResponse.ElementView::id,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        while (!pending.isEmpty()) {
            String firstId = pending.iterator().next();
            pending.remove(firstId);
            ArrayList<WorkspaceResponse.ElementView> component =
                    new ArrayList<WorkspaceResponse.ElementView>();
            ArrayList<WorkspaceResponse.ElementView> queue =
                    new ArrayList<WorkspaceResponse.ElementView>();
            queue.add(seatById.get(firstId));
            for (int index = 0; index < queue.size(); index++) {
                WorkspaceResponse.ElementView current = queue.get(index);
                component.add(current);
                ArrayList<String> neighborIds = new ArrayList<String>();
                for (String candidateId : pending) {
                    WorkspaceResponse.ElementView candidate = seatById.get(candidateId);
                    if (isTouching(current, candidate)) {
                        neighborIds.add(candidateId);
                    }
                }
                for (String neighborId : neighborIds) {
                    pending.remove(neighborId);
                    queue.add(seatById.get(neighborId));
                }
            }
            components.add(component);
        }
        return components;
    }

    private boolean isTouching(
            WorkspaceResponse.ElementView left,
            WorkspaceResponse.ElementView right
    ) {
        int leftTop = left.row();
        int leftBottom = left.row() + left.rowSpan() - 1;
        int leftStart = left.column();
        int leftEnd = left.column() + left.columnSpan() - 1;
        int rightTop = right.row();
        int rightBottom = right.row() + right.rowSpan() - 1;
        int rightStart = right.column();
        int rightEnd = right.column() + right.columnSpan() - 1;
        boolean rowsOverlap = leftTop <= rightBottom && rightTop <= leftBottom;
        boolean columnsOverlap = leftStart <= rightEnd && rightStart <= leftEnd;
        boolean horizontalTouch = rowsOverlap && (leftEnd + 1 == rightStart || rightEnd + 1 == leftStart);
        boolean verticalTouch = columnsOverlap && (leftBottom + 1 == rightTop || rightBottom + 1 == leftTop);
        return horizontalTouch || verticalTouch;
    }

    private boolean isSolidRectangle(List<WorkspaceResponse.ElementView> seats) {
        CanvasBounds bounds = coveredBounds(seats);
        LinkedHashSet<String> cells = coveredCells(seats);
        int rectangleSize = (bounds.bottom() - bounds.top() + 1) * (bounds.right() - bounds.left() + 1);
        return cells.size() == rectangleSize;
    }

    private void renderReservedRectangle(
            XSSFSheet sheet,
            MeasuredLayout measured,
            List<WorkspaceResponse.ElementView> seats,
            String label,
            XSSFCellStyle style
    ) {
        CanvasBounds bounds = coveredBounds(seats);
        mergeAndWrite(
                sheet,
                measured.excelRowByCanvasRow().get(bounds.top()),
                measured.excelRowByCanvasRow().get(bounds.bottom())
                        + measured.rowHeights().get(bounds.bottom()) - 1,
                measured.excelColumnByCanvasColumn().get(bounds.left()),
                measured.excelColumnByCanvasColumn().get(bounds.right()),
                label,
                style
        );
    }

    private boolean renderReservedRuns(
            XSSFSheet sheet,
            MeasuredLayout measured,
            List<WorkspaceResponse.ElementView> seats,
            String label,
            XSSFCellStyle style
    ) {
        LinkedHashMap<Integer, List<Integer>> columnsByRow =
                new LinkedHashMap<Integer, List<Integer>>();
        for (WorkspaceResponse.ElementView seat : seats) {
            for (int row = seat.row(); row < seat.row() + seat.rowSpan(); row++) {
                columnsByRow.computeIfAbsent(row, ignored -> new ArrayList<Integer>());
                for (int column = seat.column(); column < seat.column() + seat.columnSpan(); column++) {
                    columnsByRow.get(row).add(column);
                }
            }
        }
        boolean wroteLabel = false;
        for (Map.Entry<Integer, List<Integer>> entry : columnsByRow.entrySet()) {
            List<Integer> columns = entry.getValue().stream()
                    .distinct()
                    .sorted()
                    .toList();
            int runStart = -1;
            int previous = -1;
            for (int index = 0; index <= columns.size(); index++) {
                int column = index < columns.size() ? columns.get(index) : Integer.MIN_VALUE;
                if (runStart < 0) {
                    runStart = column;
                    previous = column;
                    continue;
                }
                if (column == previous + 1) {
                    previous = column;
                    continue;
                }
                String value = wroteLabel ? "" : label;
                mergeAndWrite(
                        sheet,
                        measured.excelRowByCanvasRow().get(entry.getKey()),
                        measured.excelRowByCanvasRow().get(entry.getKey())
                                + measured.rowHeights().get(entry.getKey()) - 1,
                        measured.excelColumnByCanvasColumn().get(runStart),
                        measured.excelColumnByCanvasColumn().get(previous),
                        value,
                        style
                );
                wroteLabel = true;
                runStart = column;
                previous = column;
            }
        }
        return wroteLabel;
    }

    private CanvasBounds coveredBounds(List<WorkspaceResponse.ElementView> seats) {
        int top = seats.stream().mapToInt(WorkspaceResponse.ElementView::row).min().orElse(1);
        int bottom = seats.stream()
                .mapToInt(element -> element.row() + element.rowSpan() - 1)
                .max()
                .orElse(top);
        int left = seats.stream().mapToInt(WorkspaceResponse.ElementView::column).min().orElse(1);
        int right = seats.stream()
                .mapToInt(element -> element.column() + element.columnSpan() - 1)
                .max()
                .orElse(left);
        return new CanvasBounds(top, bottom, left, right);
    }

    private LinkedHashSet<String> coveredCells(List<WorkspaceResponse.ElementView> seats) {
        LinkedHashSet<String> cells = new LinkedHashSet<String>();
        for (WorkspaceResponse.ElementView seat : seats) {
            for (int row = seat.row(); row < seat.row() + seat.rowSpan(); row++) {
                for (int column = seat.column(); column < seat.column() + seat.columnSpan(); column++) {
                    cells.add(row + ":" + column);
                }
            }
        }
        return cells;
    }

    private boolean isReserved(WorkspaceResponse.PlanItemView item) {
        return item != null && "RESERVED".equals(item.type());
    }

    private int seatSpanHeight(
            MeasuredLayout measured,
            WorkspaceResponse.ElementView element
    ) {
        int startRow = measured.excelRowByCanvasRow().get(element.row());
        int endCanvasRow = element.row() + element.rowSpan() - 1;
        int endRow = measured.excelRowByCanvasRow().get(endCanvasRow)
                + measured.rowHeights().get(endCanvasRow) - 1;
        return endRow - startRow + 1;
    }

    private String fieldValueColor(
            FieldRun run,
            Set<String> colorFieldCodes,
            Map<String, Map<String, String>> colorsByFieldValue
    ) {
        if (!colorFieldCodes.contains(run.fieldCode()) || run.value().isBlank()) {
            return "#ffffff";
        }
        return colorsByFieldValue.getOrDefault(run.fieldCode(), Map.of())
                .getOrDefault(run.value(), "#ffffff");
    }

    private void renderNonSeatElements(
            XSSFSheet sheet,
            MeasuredLayout measured,
            List<WorkspaceResponse.ElementView> elements,
            StyleCache styles
    ) {
        for (WorkspaceResponse.ElementView element : elements) {
            if (isSeat(element)) {
                continue;
            }
            int startRow = measured.excelRowByCanvasRow().get(element.row());
            int endCanvasRow = element.row() + element.rowSpan() - 1;
            int endRow = measured.excelRowByCanvasRow().get(endCanvasRow)
                    + measured.rowHeights().get(endCanvasRow) - 1;
            int startColumn = measured.excelColumnByCanvasColumn().get(element.column());
            int endColumn = measured.excelColumnByCanvasColumn().get(
                    element.column() + element.columnSpan() - 1
            );
            mergeAndWrite(
                    sheet,
                    startRow,
                    endRow,
                    startColumn,
                    endColumn,
                    nullToEmpty(element.name()),
                    styles.style(element.fillColor(), true, true)
            );
        }
    }

    private void mergeAndWrite(
            XSSFSheet sheet,
            int firstRow,
            int lastRow,
            int firstColumn,
            int lastColumn,
            String value,
            XSSFCellStyle style
    ) {
        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            for (int columnIndex = firstColumn; columnIndex <= lastColumn; columnIndex++) {
                writeCell(sheet, rowIndex, columnIndex, "", style);
            }
        }
        writeCell(sheet, firstRow, firstColumn, value, style);
        if (lastRow > firstRow || lastColumn > firstColumn) {
            sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstColumn, lastColumn));
        }
    }

    private void writeCell(
            XSSFSheet sheet,
            int rowIndex,
            int columnIndex,
            String value,
            XSSFCellStyle style
    ) {
        XSSFRow row = row(sheet, rowIndex);
        XSSFCell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        cell.setCellValue(nullToEmpty(value));
        cell.setCellStyle(style);
    }

    private XSSFRow row(XSSFSheet sheet, int rowIndex) {
        XSSFRow row = sheet.getRow(rowIndex);
        return row == null ? sheet.createRow(rowIndex) : row;
    }

    private boolean isSeat(WorkspaceResponse.ElementView element) {
        return ElementKind.SEAT.name().equals(element.kind());
    }

    private String chineseNumber(int value) {
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (value < 10) {
            return digits[value];
        }
        if (value < 20) {
            return "十" + (value == 10 ? "" : digits[value % 10]);
        }
        if (value < 100) {
            return digits[value / 10] + "十" + (value % 10 == 0 ? "" : digits[value % 10]);
        }
        return Integer.toString(value);
    }

    private String normalizeColor(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.startsWith("#") ? value : "#" + value;
        return normalized.matches("#[0-9a-fA-F]{6}")
                ? normalized.toLowerCase(Locale.ROOT)
                : "";
    }

    private byte[] parseRgbBytes(String value) {
        String normalized = normalizeColor(value);
        if (normalized.isBlank()) {
            return new byte[]{(byte) 255, (byte) 255, (byte) 255};
        }
        return new byte[]{
                (byte) Integer.parseInt(normalized.substring(1, 3), 16),
                (byte) Integer.parseInt(normalized.substring(3, 5), 16),
                (byte) Integer.parseInt(normalized.substring(5, 7), 16)
        };
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String normalizeFieldValue(String value) {
        return nullToEmpty(value).trim();
    }

    private record CanvasBounds(int top, int bottom, int left, int right) {
    }

    private record MeasuredLayout(
            CanvasBounds bounds,
            Map<Integer, Integer> excelRowByCanvasRow,
            Map<Integer, Integer> rowHeights,
            Map<Integer, Integer> excelColumnByCanvasColumn,
            int topOffset,
            int leftOffset,
            int leftRowLabelColumn,
            int fieldLabelColumn,
            int rightRowLabelColumn
    ) {
    }

    private record FieldRun(String fieldCode, String label, String value, int height) {
    }

    private record FieldCellSpan(String fieldCode, String label, String value, int offset, int height) {
    }

    private record SeatBlock(List<FieldRun> fieldRuns, int height) {
    }

    private record StyleKey(String fillColor, boolean bordered, boolean bold) {
    }

    private final class StyleCache {
        private final XSSFWorkbook workbook;
        private final Map<StyleKey, XSSFCellStyle> styles = new LinkedHashMap<StyleKey, XSSFCellStyle>();

        private StyleCache(XSSFWorkbook workbook) {
            this.workbook = workbook;
        }

        private XSSFCellStyle style(String fillColor, boolean bordered, boolean bold) {
            String normalized = normalizeColor(fillColor);
            StyleKey key = new StyleKey(normalized, bordered, bold);
            return styles.computeIfAbsent(key, ignored -> createStyle(normalized, bordered, bold));
        }

        private XSSFCellStyle createStyle(String fillColor, boolean bordered, boolean bold) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setWrapText(true);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            if (bordered) {
                style.setBorderBottom(BorderStyle.THIN);
                style.setBorderTop(BorderStyle.THIN);
                style.setBorderLeft(BorderStyle.THIN);
                style.setBorderRight(BorderStyle.THIN);
            }
            if (!fillColor.isBlank() && !"#ffffff".equals(fillColor)) {
                style.setFillForegroundColor(new XSSFColor(
                        parseRgbBytes(fillColor),
                        new DefaultIndexedColorMap()
                ));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            XSSFFont font = workbook.createFont();
            font.setFontHeightInPoints((short) 12);
            font.setBold(bold);
            style.setFont(font);
            return style;
        }
    }
}
