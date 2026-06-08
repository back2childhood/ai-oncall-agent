package com.oncall.agent.service;

import com.oncall.agent.dto.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private final ConversationAgent conversationAgent;

    public ChatService(ConversationAgent conversationAgent) {
        this.conversationAgent = conversationAgent;
    }

    public ChatResponse answer(String question, Integer topK) {
        return conversationAgent.answer(question, topK);
    }
}
