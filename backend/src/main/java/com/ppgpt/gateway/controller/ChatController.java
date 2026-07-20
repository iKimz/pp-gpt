package com.ppgpt.gateway.controller;

import com.ppgpt.gateway.domain.ChatLog;
import com.ppgpt.gateway.dto.ChatRequest;
import com.ppgpt.gateway.dto.ModelDto;
import com.ppgpt.gateway.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * GET /api/v1/chat/models
     * Returns a list of models available to the authenticated user based on their group.
     */
    @GetMapping("/models")
    public Flux<ModelDto> getModels(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return chatService.getAvailableModels(userId);
    }

    /**
     * POST /api/v1/chat/stream
     * Streams AI response as Server-Sent Events.
     * Client-side AbortController.abort() triggers backend doFinally() / doOnCancel().
     *
     * Produces: text/event-stream
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @Valid @RequestBody ChatRequest request,
            Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return chatService.streamChat(userId, request);
    }

    /**
     * GET /api/v1/chat/history?page=0&size=20
     * Returns paginated chat history for the authenticated user.
     */
    @GetMapping("/history")
    public Flux<ChatLog> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return chatService.getChatHistory(userId, page, size);
    }
}
