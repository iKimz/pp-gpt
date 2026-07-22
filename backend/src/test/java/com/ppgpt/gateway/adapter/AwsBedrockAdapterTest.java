package com.ppgpt.gateway.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppgpt.gateway.dto.ChatRequest;
import com.ppgpt.gateway.dto.ToolDto;
import com.ppgpt.gateway.domain.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AwsBedrockAdapterTest {

    private AwsBedrockAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        adapter = new AwsBedrockAdapter(objectMapper);
    }

    @Test
    void testProviderKey() {
        assertEquals("AWS_BEDROCK", adapter.providerKey());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testBuildRequestBody_AnthropicClaude3() throws Exception {
        Model model = new Model();
        model.setModelName("anthropic.claude-3-5-sonnet-20240620-v1:0");
        model.setSystemPrompt("You are a helpful AI assistant.");
        model.setTemperature(0.7);

        ChatRequest request = new ChatRequest();
        request.setMessage("Hello Claude!");
        request.setImages(List.of("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="));

        ToolDto tool = new ToolDto("function", new ToolDto.FunctionDef("get_weather", "Get weather information", Map.of("type", "object")));
        request.setTools(List.of(tool));

        Method buildReqMethod = AwsBedrockAdapter.class.getDeclaredMethod("buildRequestBody", ChatRequest.class, Model.class);
        buildReqMethod.setAccessible(true);
        Map<String, Object> body = (Map<String, Object>) buildReqMethod.invoke(adapter, request, model);

        assertEquals("bedrock-2023-05-31", body.get("anthropic_version"));
        assertTrue(body.get("system").toString().contains("You are a helpful AI assistant."));

        List<Map<String, Object>> tools = (List<Map<String, Object>>) body.get("tools");
        assertNotNull(tools);
        assertEquals(1, tools.size());
        assertEquals("get_weather", tools.get(0).get("name"));
        assertTrue(tools.get(0).containsKey("input_schema"));

        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        assertNotNull(messages);
        assertEquals(1, messages.size());

        List<Map<String, Object>> contentBlocks = (List<Map<String, Object>>) messages.get(0).get("content");
        assertEquals(2, contentBlocks.size());
        assertEquals("text", contentBlocks.get(0).get("type"));
        assertEquals("image", contentBlocks.get(1).get("type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testBuildRequestBody_NonAnthropicLlamaAndNova() throws Exception {
        Model model = new Model();
        model.setModelName("meta.llama3-1-70b-instruct-v1:0");
        model.setSystemPrompt("You are Llama.");
        model.setTemperature(0.5);

        ChatRequest request = new ChatRequest();
        request.setMessage("Calculate 10 * 5");
        ToolDto tool = new ToolDto("function", new ToolDto.FunctionDef("calculate", "Performs mathematical calculations. " + "A".repeat(400), Map.of("type", "object")));
        request.setTools(List.of(tool));

        Method buildReqMethod = AwsBedrockAdapter.class.getDeclaredMethod("buildRequestBody", ChatRequest.class, Model.class);
        buildReqMethod.setAccessible(true);
        Map<String, Object> body = (Map<String, Object>) buildReqMethod.invoke(adapter, request, model);

        assertEquals("meta.llama3-1-70b-instruct-v1:0", body.get("model"));
        assertEquals(true, body.get("stream"));

        List<Map<String, Object>> tools = (List<Map<String, Object>>) body.get("tools");
        assertNotNull(tools);
        assertEquals(1, tools.size());
        Map<String, Object> toolFn = (Map<String, Object>) tools.get(0).get("function");
        assertEquals("calculate", toolFn.get("name"));
        assertTrue(toolFn.get("description").toString().length() <= 305);

        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        assertTrue(messages.size() >= 2); // System message + User message
        assertEquals("system", messages.get(0).get("role"));
    }

    @Test
    void testExtractBedrockContent_Formats() throws Exception {
        Method extractMethod = AwsBedrockAdapter.class.getDeclaredMethod("extractBedrockContent", String.class);
        extractMethod.setAccessible(true);

        // 1. OpenAI Delta format (OSS Bedrock / Proxy)
        String openAiJson = "{\"choices\":[{\"delta\":{\"content\":\"Hello from Bedrock OSS!\"}}]}";
        String content1 = (String) extractMethod.invoke(adapter, openAiJson);
        assertEquals("Hello from Bedrock OSS!", content1);

        // 2. Claude 3 Tool Use Event
        String claudeToolStartJson = "{\"type\":\"content_block_start\",\"content_block\":{\"type\":\"tool_use\",\"id\":\"call_abc\",\"name\":\"get_current_time\",\"input\":{}}}";
        String content2 = (String) extractMethod.invoke(adapter, claudeToolStartJson);
        assertTrue(content2.contains("get_current_time"));
        assertTrue(content2.contains("call_abc"));

        // 3. Reasoning Tag Stripping
        String reasoningJson = "{\"choices\":[{\"delta\":{\"content\":\"<reasoning>Thinking step 1</reasoning>The answer is 42.\"}}]}";
        String content3 = (String) extractMethod.invoke(adapter, reasoningJson);
        assertEquals("The answer is 42.", content3);
    }
}
