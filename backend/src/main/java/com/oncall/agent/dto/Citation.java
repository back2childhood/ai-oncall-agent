package com.oncall.agent.dto;

import java.util.UUID;

public record Citation(
        UUID documentId,
        String filename,
        String title,
        String chapterPath,
        int chunkIndex,
        double score,
        String preview
) {
}
