package com.company.meetinghelper.importing.api.dto.request;

import java.util.Map;

public record CommitRequest(Map<String, Integer> selectedSourceRows) {
}
