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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * REST Controller for AI Chat completion streaming, model discovery, and chat history retrieval.
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Retrieves models available for invocation by the authenticated user based on group access.
     *
     * @param auth Spring Security authentication object
     * @return Flux of available model DTOs
     */
    @GetMapping("/models")
    public Flux<ModelDto> getModels(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return chatService.getAvailableModels(userId);
    }

    /**
     * Streams AI response as Server-Sent Events (SSE).
     *
     * @param request Chat completion request payload
     * @param auth    Spring Security authentication object
     * @return Flux of SSE string events
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @Valid @RequestBody ChatRequest request,
            Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return chatService.streamChat(userId, request);
    }

    /**
     * Retrieves paginated chat history for the authenticated user.
     *
     * @param page Page index (0-based)
     * @param size Page size
     * @param auth Spring Security authentication object
     * @return Flux of ChatLog entities
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
