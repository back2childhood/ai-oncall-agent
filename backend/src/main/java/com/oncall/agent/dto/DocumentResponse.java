package com.oncall.agent.dto;

import com.oncall.agent.domain.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String filename,
        String contentType,
        String sourceType,
        DocumentStatus status,
        int chunkCount,
        String errorMessage,
        Instant createdAt
) {
}
