package com.company.meetinghelper.participant.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Decides whether an incoming dynamic record should be skipped, merged into one existing record,
 * or appended as a new record.
 */
@Component
public class ParticipantRecordMerger {

    /**
     * Chooses the persistence action for one incoming dynamic record.
     *
     * @param incoming incoming attributes
     * @param existing existing records in stable record order
     * @return skip, merge, or append decision
     */
    public MergeDecision decide(Map<String, String> incoming, List<RecordValue> existing) {
        Map<String, String> normalizedIncoming = normalize(incoming);
        List<NormalizedRecord> normalizedExisting = existing.stream()
                .map(record -> new NormalizedRecord(record, normalize(record.attributes())))
                .toList();

        for (NormalizedRecord record : normalizedExisting) {
            if (containsAll(record.attributes(), normalizedIncoming)) {
                return new MergeDecision(Action.SKIP, record.value().recordId(), record.attributes());
            }
        }

        List<NormalizedRecord> compatibleRecords = normalizedExisting.stream()
                .filter(record -> compatible(record.attributes(), normalizedIncoming))
                .toList();
        if (compatibleRecords.size() == 1) {
            NormalizedRecord target = compatibleRecords.getFirst();
            Map<String, String> merged = new LinkedHashMap<>(target.attributes());
            merged.putAll(normalizedIncoming);
            return new MergeDecision(Action.MERGE, target.value().recordId(), Map.copyOf(merged));
        }

        return new MergeDecision(Action.APPEND, null, normalizedIncoming);
    }

    private boolean containsAll(Map<String, String> existing, Map<String, String> incoming) {
        return incoming.entrySet().stream()
                .allMatch(entry -> Objects.equals(existing.get(entry.getKey()), entry.getValue()));
    }

    private boolean compatible(Map<String, String> existing, Map<String, String> incoming) {
        return incoming.entrySet().stream()
                .filter(entry -> existing.containsKey(entry.getKey()))
                .allMatch(entry -> Objects.equals(existing.get(entry.getKey()), entry.getValue()));
    }

    private Map<String, String> normalize(Map<String, String> attributes) {
        Map<String, String> normalized = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            String normalizedKey = key == null ? "" : key.trim();
            String normalizedValue = value == null ? "" : value.trim();
            if (!normalizedKey.isEmpty() && !normalizedValue.isEmpty()) {
                normalized.put(normalizedKey, normalizedValue);
            }
        });
        return Map.copyOf(normalized);
    }

    public enum Action {
        SKIP,
        MERGE,
        APPEND
    }

    public record RecordValue(String recordId, int recordOrder, Map<String, String> attributes) {
    }

    public record MergeDecision(
            Action action,
            String targetRecordId,
            Map<String, String> mergedAttributes
    ) {
    }

    private record NormalizedRecord(RecordValue value, Map<String, String> attributes) {
    }
}
