package com.oncall.agent.service;

public record ParsedSection(
        String title,
        String chapterPath,
        String content
) {
}
