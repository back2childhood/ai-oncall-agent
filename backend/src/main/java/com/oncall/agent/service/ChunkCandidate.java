package com.oncall.agent.service;

public record ChunkCandidate(
        String title,
        String chapterPath,
        int chunkIndex,
        String content,
        int tokenEstimate
) {
}
