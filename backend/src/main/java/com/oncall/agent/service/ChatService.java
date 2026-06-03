package com.oncall.agent.service;

import com.oncall.agent.dto.ChatResponse;
import com.oncall.agent.dto.Citation;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private static final String SYSTEM_PROMPT = """
            You are an AI on-call agent for software engineers.
            Use the provided alert, log, code, runbook, and design-document context to answer.
            Be specific: explain likely cause, evidence, next checks, and safe remediation steps.
            If the context is insufficient, say what is missing instead of inventing details.
            Include file, service, endpoint, metric, or runbook names when the context provides them.
            """;

    private final RetrievalService retrievalService;
    private final ChatClient chatClient;

    public ChatService(RetrievalService retrievalService, ChatClient chatClient) {
        this.retrievalService = retrievalService;
        this.chatClient = chatClient;
    }

    public ChatResponse answer(String question, Integer topK) {
        List<RetrievedChunk> chunks = retrievalService.retrieve(question, topK);
        String context = buildContext(chunks);
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("""
                        Question:
                        %s

                        Retrieved context:
                        %s
                        """.formatted(question, context))
                .call()
                .content();
        return new ChatResponse(answer, chunks.stream().map(this::toCitation).toList());
    }

    private String buildContext(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return "No matching uploaded context was found.";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk item = chunks.get(i);
            builder.append("Source ").append(i + 1)
                    .append(" | file=").append(item.document() == null ? "unknown" : item.document().getFilename())
                    .append(" | title=").append(item.chunk().getTitle())
                    .append(" | chapter=").append(item.chunk().getChapterPath())
                    .append(" | score=").append(String.format("%.3f", item.rerankScore()))
                    .append('\n')
                    .append(item.chunk().getContent())
                    .append("\n\n");
        }
        return builder.toString();
    }

    private Citation toCitation(RetrievedChunk item) {
        String content = item.chunk().getContent();
        String preview = content.length() > 260 ? content.substring(0, 260) + "..." : content;
        return new Citation(
                item.chunk().getDocumentId(),
                item.document() == null ? "unknown" : item.document().getFilename(),
                item.chunk().getTitle(),
                item.chunk().getChapterPath(),
                item.chunk().getChunkIndex(),
                item.rerankScore(),
                preview);
    }
}
