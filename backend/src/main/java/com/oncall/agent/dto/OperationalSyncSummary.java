package com.oncall.agent.dto;

import java.util.List;

public record OperationalSyncSummary(
        boolean syncEnabled,
        boolean prometheusEnabled,
        boolean mcpLogsEnabled,
        long fixedDelayMs,
        List<OperationalSyncResponse> recentRuns
) {
}
