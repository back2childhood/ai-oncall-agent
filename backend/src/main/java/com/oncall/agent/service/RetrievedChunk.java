package com.oncall.agent.service;

import com.oncall.agent.domain.DocumentChunkEntity;
import com.oncall.agent.domain.UploadedDocument;

public record RetrievedChunk(
        DocumentChunkEntity chunk,
        UploadedDocument document,
        double vectorScore,
        double rerankScore
) {
}
