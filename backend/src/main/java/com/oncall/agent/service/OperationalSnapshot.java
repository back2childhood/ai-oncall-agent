package com.oncall.agent.service;

public record OperationalSnapshot(
        String source,
        String filename,
        String sourceType,
        String content,
        int itemCount
) {
    public boolean isEmpty() {
        return itemCount <= 0 || content == null || content.isBlank();
    }
}
