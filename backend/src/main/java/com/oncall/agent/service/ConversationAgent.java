package com.oncall.agent.service;

import com.oncall.agent.dto.ChatResponse;
import com.oncall.agent.dto.Citation;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConversationAgent {
    private static final String SYSTEM_PROMPT = """
            You are the Conversation Agent for an AI on-call assistant.
            Your job is to answer the user's incident or knowledge-base question.
            Search results from the vector database are provided as grounded context.
            Use the provided alert, log, code, runbook, and design-document context to answer.
            Be specific: explain likely cause, evidence, next checks, and safe remediation steps.
            If the context is insufficient, say what is missing instead of inventing details.
            Include file, service, endpoint, metric, or runbook names when the context provides them.
            """;

    private final RetrievalService retrievalService;
    private final ChatClient chatClient;
    private final ClassroomLlmClient classroomLlmClient;
    private final boolean demoMode;

    public ConversationAgent(
            RetrievalService retrievalService,
            ChatClient chatClient,
            ClassroomLlmClient classroomLlmClient,
            @Value("${app.demo-mode}") boolean demoMode) {
        this.retrievalService = retrievalService;
        this.chatClient = chatClient;
        this.classroomLlmClient = classroomLlmClient;
        this.demoMode = demoMode;
    }

    public ChatResponse answer(String question, Integer topK) {
        List<RetrievedChunk> chunks = retrievalService.retrieve(question, topK);
        String context = buildContext(chunks);
        String answer;
        if (classroomLlmClient.isEnabled()) {
            answer = classroomLlmClient.generate(classroomPrompt(question, context));
        } else if (demoMode) {
            answer = demoAnswer(question, chunks, context);
        } else {
            answer = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("""
                            Question:
                            %s

                            Retrieved context:
                            %s
                            """.formatted(question, context))
                    .call()
                    .content();
        }
        return new ChatResponse(answer, chunks.stream().map(this::toCitation).toList());
    }

    private String classroomPrompt(String question, String context) {
        return SYSTEM_PROMPT + "\n\n"
                + "Response rules:\n"
                + "- Write Markdown that the UI can render.\n"
                + "- If the user is only greeting you, introducing themselves, or making casual conversation, reply naturally and briefly as the Conversation Agent. Do not use the incident template.\n"
                + "- If the user asks what you can do, briefly explain that you can answer uploaded-document questions and help triage alerts/logs.\n"
                + "- Only use the incident template when the user asks about an alert, log, metric, outage, error, incident, runbook, code, design document, or other technical operational question.\n"
                + "- Do not copy the retrieved runbook verbatim. Synthesize it into an incident diagnosis.\n"
                + "- Separate confirmed evidence from likely hypotheses.\n"
                + "- If no live logs or metrics are included in the context, clearly say that the answer is based on runbook context only.\n"
                + "- Keep the answer practical for an on-call engineer.\n"
                + "- For incident-template answers, use these headings exactly: ## Diagnosis, ## Evidence, ## Next Checks, ## Safe Remediation, ## Missing Data.\n\n"
                + "Question:\n" + question + "\n\n"
                + "Retrieved context from the vector/knowledge store:\n" + context + "\n"
                + "Now answer the question using the response rules.";
    }

    private String demoAnswer(String question, List<RetrievedChunk> chunks, String context) {
        if (chunks.isEmpty()) {
            return "Demo mode answer: I could not find matching uploaded context. Upload one or more runbooks from aiops-docs, then ask about CPU, memory, disk, service unavailable, or slow response alerts.";
        }

        RetrievedChunk best = chunks.getFirst();
        return "Demo mode answer from the Conversation Agent.\n\n"
                + "Question: " + question + "\n\n"
                + "Most relevant source: "
                + (best.document() == null ? "unknown" : best.document().getFilename())
                + " / " + best.chunk().getTitle() + "\n\n"
                + "Likely reason and next steps are based on the retrieved runbook context. "
                + "Start by checking the alert time window, then query related logs, review Prometheus metrics, identify common causes, apply safe remediation, and verify recovery.\n\n"
                + "Retrieved evidence preview:\n"
                + context.substring(0, Math.min(context.length(), 1400));
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
