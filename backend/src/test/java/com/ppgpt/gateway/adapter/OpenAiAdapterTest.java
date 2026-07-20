package com.ppgpt.gateway.adapter;

import com.ppgpt.gateway.domain.Model;
import com.ppgpt.gateway.dto.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the static {@link OpenAiAdapter#buildMessages} helper.
 */
class OpenAiAdapterTest {

    private static Model modelWith(String systemPrompt) {
        return Model.builder()
                .id("m1").name("Test Model").modelName("gpt-4o")
                .systemPrompt(systemPrompt).maxHistoryMessages(10).build();
    }

    private static ChatRequest requestWith(String message, List<Map<String, String>> history) {
        ChatRequest req = new ChatRequest();
        req.setMessage(message);
        req.setSessionId("sess-1");
        req.setHistory(history);
        return req;
    }

    @Test
    void buildMessages_noSystemPrompt_noHistory_singleUserMessage() {
        List<Map<String, String>> msgs = OpenAiAdapter.buildMessages(requestWith("Hello", null), modelWith(null));
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).containsEntry("role", "user").containsEntry("content", "Hello");
    }

    @Test
    void buildMessages_withSystemPrompt_firstMessageIsSystem() {
        List<Map<String, String>> msgs = OpenAiAdapter.buildMessages(requestWith("Hi", null), modelWith("You are helpful."));
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).containsEntry("role", "system").containsEntry("content", "You are helpful.");
        assertThat(msgs.get(1)).containsEntry("role", "user");
    }

    @Test
    void buildMessages_blankSystemPrompt_noSystemMessage() {
        List<Map<String, String>> msgs = OpenAiAdapter.buildMessages(requestWith("Hello", null), modelWith("   "));
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).get("role")).isEqualTo("user");
    }

    @Test
    void buildMessages_withHistory_historyInsertedBetweenSystemAndUser() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "user",      "content", "What is 2+2?"),
                Map.of("role", "assistant", "content", "It is 4."));
        List<Map<String, String>> msgs = OpenAiAdapter.buildMessages(
                requestWith("What about 3+3?", history), modelWith("Be helpful."));
        assertThat(msgs).hasSize(4);
        assertThat(msgs.get(0).get("role")).isEqualTo("system");
        assertThat(msgs.get(1).get("content")).isEqualTo("What is 2+2?");
        assertThat(msgs.get(2).get("role")).isEqualTo("assistant");
        assertThat(msgs.get(3).get("content")).isEqualTo("What about 3+3?");
    }

    @Test
    void buildMessages_historyTurnWithNullRole_defaultsToUser() {
        List<Map<String, String>> history = List.of(Map.of("content", "previous message"));
        List<Map<String, String>> msgs = OpenAiAdapter.buildMessages(requestWith("New", history), modelWith(null));
        assertThat(msgs.get(0).get("role")).isEqualTo("user");
    }

    @Test
    void buildMessages_historyTurnWithNullContent_defaultsToEmpty() {
        List<Map<String, String>> history = List.of(Map.of("role", "assistant"));
        List<Map<String, String>> msgs = OpenAiAdapter.buildMessages(requestWith("Follow-up", history), modelWith(null));
        assertThat(msgs.get(0).get("content")).isEmpty();
    }

    @Test
    void buildMessages_lastMessageIsAlwaysNewUserMessage() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "old"),
                Map.of("role", "assistant", "content", "reply"));
        List<Map<String, String>> msgs = OpenAiAdapter.buildMessages(
                requestWith("brand new question", history), modelWith("System prompt."));
        Map<String, String> last = msgs.get(msgs.size() - 1);
        assertThat(last.get("role")).isEqualTo("user");
        assertThat(last.get("content")).isEqualTo("brand new question");
    }
}
