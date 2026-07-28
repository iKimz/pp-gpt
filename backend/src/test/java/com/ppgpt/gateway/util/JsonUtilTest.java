package com.ppgpt.gateway.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JsonUtilTest {

    @Test
    @DisplayName("getObjectMapper: Returns initialized ObjectMapper instance")
    public void testGetObjectMapper() {
        assertNotNull(JsonUtil.getObjectMapper());
    }

    @Test
    @DisplayName("parseJsonMap: Parses valid JSON string into Map")
    public void testParseJsonMapSuccess() {
        String json = "{\"name\":\"Test\", \"amount\":100}";
        Map<String, Object> map = JsonUtil.parseJsonMap(json);

        assertNotNull(map);
        assertEquals("Test", map.get("name"));
        assertEquals(100, map.get("amount"));
    }

    @Test
    @DisplayName("parseJsonMap: Returns empty Map when JSON is null, blank, or invalid")
    public void testParseJsonMapEdgeCases() {
        assertTrue(JsonUtil.parseJsonMap(null).isEmpty());
        assertTrue(JsonUtil.parseJsonMap("   ").isEmpty());
        assertTrue(JsonUtil.parseJsonMap("{invalid-json}").isEmpty());
    }

    @Test
    @DisplayName("parseJsonList: Parses valid JSON list string into List of Maps")
    public void testParseJsonListSuccess() {
        String json = "[{\"id\":1}, {\"id\":2}]";
        List<Map<String, Object>> list = JsonUtil.parseJsonList(json);

        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals(1, list.get(0).get("id"));
        assertEquals(2, list.get(1).get("id"));
    }

    @Test
    @DisplayName("parseJsonList: Returns empty List when JSON is null, blank, or invalid")
    public void testParseJsonListEdgeCases() {
        assertTrue(JsonUtil.parseJsonList(null).isEmpty());
        assertTrue(JsonUtil.parseJsonList("   ").isEmpty());
        assertTrue(JsonUtil.parseJsonList("[invalid-list]").isEmpty());
    }

    @Test
    @DisplayName("toJsonString: Converts object to JSON string")
    public void testToJsonStringSuccess() {
        Map<String, Object> data = Map.of("key", "value");
        String json = JsonUtil.toJsonString(data);

        assertNotNull(json);
        assertTrue(json.contains("\"key\":\"value\""));
    }

    @Test
    @DisplayName("toJsonString: Returns '{}' when object is null")
    public void testToJsonStringNull() {
        assertEquals("{}", JsonUtil.toJsonString(null));
    }

    @Test
    @DisplayName("parseTree: Parses JSON string into JsonNode tree")
    public void testParseTreeSuccess() {
        String json = "{\"status\":\"OK\",\"code\":200}";
        JsonNode node = JsonUtil.parseTree(json);

        assertNotNull(node);
        assertEquals("OK", node.get("status").asText());
        assertEquals(200, node.get("code").asInt());
    }

    @Test
    @DisplayName("parseTree: Returns null when JSON is null, blank, or invalid")
    public void testParseTreeEdgeCases() {
        assertNull(JsonUtil.parseTree(null));
        assertNull(JsonUtil.parseTree("   "));
        assertNull(JsonUtil.parseTree("{broken-json}"));
    }
}
