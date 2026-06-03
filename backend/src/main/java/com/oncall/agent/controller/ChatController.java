package com.oncall.agent.controller;

import com.oncall.agent.dto.ChatRequest;
import com.oncall.agent.dto.ChatResponse;
import com.oncall.agent.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
        return chatService.answer(request.question(), request.topK());
    }
}
