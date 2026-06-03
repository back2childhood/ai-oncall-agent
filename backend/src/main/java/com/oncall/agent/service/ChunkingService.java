package com.oncall.agent.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChunkingService {
    private static final int MAX_CHARS = 3200;
    private static final int OVERLAP_CHARS = 350;

    public List<ChunkCandidate> chunk(List<ParsedSection> sections) {
        List<ChunkCandidate> chunks = new ArrayList<>();
        int index = 0;
        for (ParsedSection section : sections) {
            String content = section.content();
            int start = 0;
            while (start < content.length()) {
                int end = chooseBoundary(content, Math.min(start + MAX_CHARS, content.length()));
                String part = content.substring(start, end).trim();
                if (!part.isBlank()) {
                    chunks.add(new ChunkCandidate(
                            section.title(),
                            section.chapterPath(),
                            index++,
                            part,
                            estimateTokens(part)));
                }
                if (end >= content.length()) {
                    break;
                }
                start = Math.max(0, end - OVERLAP_CHARS);
            }
        }
        return chunks;
    }

    private int chooseBoundary(String content, int proposedEnd) {
        if (proposedEnd >= content.length()) {
            return content.length();
        }
        int paragraph = content.lastIndexOf("\n\n", proposedEnd);
        if (paragraph > proposedEnd - 900) {
            return paragraph;
        }
        int sentence = content.lastIndexOf(". ", proposedEnd);
        if (sentence > proposedEnd - 500) {
            return sentence + 1;
        }
        return proposedEnd;
    }

    private int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }
}
