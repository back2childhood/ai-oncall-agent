package com.oncall.agent.dto;

import com.oncall.agent.domain.SyncStatus;
import java.time.Instant;
import java.util.UUID;

public record OperationalSyncResponse(
        UUID id,
        String source,
        SyncStatus status,
        int itemCount,
        UUID documentId,
        String message,
        Instant startedAt,
        Instant finishedAt
) {
}
