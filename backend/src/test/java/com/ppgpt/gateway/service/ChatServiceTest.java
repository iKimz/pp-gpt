package com.ppgpt.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppgpt.gateway.adapter.AiProviderAdapterFactory;
import com.ppgpt.gateway.domain.ChatLog;
import com.ppgpt.gateway.domain.GroupModelAccess;
import com.ppgpt.gateway.domain.Model;
import com.ppgpt.gateway.domain.User;
import com.ppgpt.gateway.domain.UserGroup;
import com.ppgpt.gateway.dto.ChatRequest;
import com.ppgpt.gateway.repository.ChatLogRepository;
import com.ppgpt.gateway.repository.CreditRateRepository;
import com.ppgpt.gateway.repository.GroupModelAccessRepository;
import com.ppgpt.gateway.repository.ModelRepository;
import com.ppgpt.gateway.repository.UserGroupRepository;
import com.ppgpt.gateway.repository.UserRepository;
import com.ppgpt.gateway.util.TokenizerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatServiceTest {

    @Mock
    private AiProviderAdapterFactory adapterFactory;
    @Mock
    private ModelRepository modelRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserGroupRepository userGroupRepository;
    @Mock
    private GroupModelAccessRepository groupModelAccessRepository;
    @Mock
    private CreditRateRepository creditRateRepository;
    @Mock
    private ChatLogRepository chatLogRepository;
    @Mock
    private CryptoService cryptoService;
    @Mock
    private QuotaService quotaService;
    @Mock
    private TokenizerUtil tokenizerUtil;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private R2dbcEntityTemplate entityTemplate;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MeterRegistry meterRegistry;
    @Mock
    private McpServerService mcpServerService;

    @InjectMocks
    private ChatService chatService;

    @Test
    @DisplayName("getAvailableModels: Returns active GENERATION models accessible by user's group")
    public void testGetAvailableModelsSuccess() {
        User user = User.builder().id("u-1").groupId("g-1").build();
        GroupModelAccess access1 = GroupModelAccess.builder().groupId("g-1").modelId("m-1").build();
        GroupModelAccess access2 = GroupModelAccess.builder().groupId("g-1").modelId("m-2").build();

        Model genModel = Model.builder().id("m-1").name("GPT-4o").modelType("GENERATION").isActive(true).build();
        Model safetyModel = Model.builder().id("m-2").name("Safety Filter").modelType("SAFETY").isActive(true).build();

        when(userRepository.findById("u-1")).thenReturn(Mono.just(user));
        when(groupModelAccessRepository.findByGroupId("g-1")).thenReturn(Flux.just(access1, access2));
        when(modelRepository.findById("m-1")).thenReturn(Mono.just(genModel));
        when(modelRepository.findById("m-2")).thenReturn(Mono.just(safetyModel));

        StepVerifier.create(chatService.getAvailableModels("u-1"))
                .assertNext(dto -> {
                    assertEquals("m-1", dto.getId());
                    assertEquals("GPT-4o", dto.getName());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getAvailableModels: Throws UNAUTHORIZED when user is not found")
    public void testGetAvailableModelsUserNotFound() {
        when(userRepository.findById("u-invalid")).thenReturn(Mono.empty());

        StepVerifier.create(chatService.getAvailableModels("u-invalid"))
                .expectErrorMatches(t -> t instanceof ResponseStatusException rse && rse.getStatusCode().value() == 401)
                .verify();
    }

    @Test
    @DisplayName("streamChat: Throws BAD_REQUEST when modelId is empty")
    public void testStreamChatMissingModelId() {
        ChatRequest req = new ChatRequest();
        req.setMessage("Hello");

        StepVerifier.create(chatService.streamChat("u-1", req))
                .expectErrorMatches(t -> t instanceof ResponseStatusException rse && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    @DisplayName("streamChat: Throws BAD_REQUEST when message is empty")
    public void testStreamChatMissingMessage() {
        ChatRequest req = new ChatRequest();
        req.setModelId("m-1");

        StepVerifier.create(chatService.streamChat("u-1", req))
                .expectErrorMatches(t -> t instanceof ResponseStatusException rse && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    @DisplayName("streamChat: Throws FORBIDDEN when user group has no access to target model")
    public void testStreamChatForbiddenModelAccess() {
        ChatRequest req = new ChatRequest();
        req.setModelId("m-1");
        req.setMessage("Hello");

        User user = User.builder().id("u-1").groupId("g-1").build();

        when(userRepository.findById("u-1")).thenReturn(Mono.just(user));
        when(groupModelAccessRepository.existsByGroupIdAndModelId("g-1", "m-1")).thenReturn(Mono.just(false));

        StepVerifier.create(chatService.streamChat("u-1", req))
                .expectErrorMatches(t -> t instanceof ResponseStatusException rse && rse.getStatusCode().value() == 403)
                .verify();
    }

    @Test
    @DisplayName("getChatHistory: Returns paginated chat history for user")
    public void testGetChatHistory() {
        ChatLog log1 = ChatLog.builder().id("c-1").userId("u-1").prompt("Hi").response("Hello").createdAt(LocalDateTime.now(ZoneOffset.UTC)).build();
        when(chatLogRepository.findByUserIdOrderByCreatedAtDesc("u-1", PageRequest.of(0, 10)))
                .thenReturn(Flux.just(log1));

        StepVerifier.create(chatService.getChatHistory("u-1", 0, 10))
                .assertNext(l -> {
                    assertEquals("c-1", l.getId());
                    assertEquals("Hi", l.getPrompt());
                    assertEquals("Hello", l.getResponse());
                })
                .verifyComplete();
    }
}
