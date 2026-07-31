package com.company.meetinghelper.seating.service;

import com.company.meetinghelper.workspace.api.dto.response.WorkspaceResponse;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * SeatLabelService 类。
 */
@Service
public class SeatLabelService {
/**
 * labelsByElementId 方法。
 * @param elements elements 参数。
 * @return 返回结果。
 */
public Map<String, String> labelsByElementId(List<WorkspaceResponse.ElementView> elements) {
        LinkedHashMap<String, String> labels = new LinkedHashMap<String, String>();
        List<Integer> sourceRows = seatRows(elements);
        for (int rowIndex = 0; rowIndex < sourceRows.size(); rowIndex++) {
            int sourceRow = sourceRows.get(rowIndex);
            List<WorkspaceResponse.ElementView> rowSeats = seats(elements).stream()
                    .filter(element -> element.row() == sourceRow)
                    .sorted(Comparator.comparingInt(WorkspaceResponse.ElementView::column)
                            .thenComparing(WorkspaceResponse.ElementView::id))
                    .toList();
            for (int seatIndex = 0; seatIndex < rowSeats.size(); seatIndex++) {
                labels.put(rowSeats.get(seatIndex).id(), (rowIndex + 1) + "排" + (seatIndex + 1));
            }
        }
        return labels;
    }

/**
 * rowLabels 方法。
 * @param elements elements 参数。
 * @return 返回结果。
 */
public List<RowLabel> rowLabels(List<WorkspaceResponse.ElementView> elements) {
        List<Integer> rows = seatRows(elements);
        return java.util.stream.IntStream.range(0, rows.size())
                .mapToObj(index -> new RowLabel(rows.get(index), index + 1))
                .toList();
    }

    private List<Integer> seatRows(List<WorkspaceResponse.ElementView> elements) {
        return seats(elements).stream()
                .map(WorkspaceResponse.ElementView::row)
                .distinct()
                .sorted()
                .toList();
    }

    private List<WorkspaceResponse.ElementView> seats(List<WorkspaceResponse.ElementView> elements) {
        return (elements == null ? List.<WorkspaceResponse.ElementView>of() : elements).stream()
                .filter(element -> "SEAT".equals(element.kind()))
                .collect(Collectors.toList());
    }

/**
 * RowLabel 数据结构。
 * @param sourceRow sourceRow 参数。
 * @param displayRow displayRow 参数。
 */
public record RowLabel(int sourceRow, int displayRow) {
    }
}
