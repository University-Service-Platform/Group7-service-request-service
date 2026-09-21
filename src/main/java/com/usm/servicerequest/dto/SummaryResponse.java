package com.usm.servicerequest.dto;

import java.util.Map;

/** US-13, FR-12 (guide §4.1) - GET /api/service-requests/summary?groupBy=. */
public record SummaryResponse(String groupBy, Map<String, Long> counts) {
}
