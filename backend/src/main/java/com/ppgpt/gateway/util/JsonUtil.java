package com.ppgpt.gateway.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Thread-safe Jackson JSON Utility providing type-safe JSON serialization,
 * deserialization, and safe node extraction across Gateway services.
 */
@Slf4j
public final class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private JsonUtil() {
        // Private constructor for utility class
    }

    /**
     * Gets the shared, thread-safe ObjectMapper instance.
     *
     * @return ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    /**
     * Parses a JSON string into a type-safe Map of String to Object.
     *
     * @param json JSON input string
     * @return Map representation, or empty map if parsing fails
     */
    public static Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("[JsonUtil] Failed to parse JSON to Map: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Parses a JSON string into a type-safe List of Maps.
     *
     * @param json JSON input string
     * @return List of Maps, or empty list if parsing fails
     */
    public static List<Map<String, Object>> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.debug("[JsonUtil] Failed to parse JSON to List: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Converts an object to a JSON formatted string safely.
     *
     * @param obj Target object
     * @return JSON string or empty object string "{}" if serialization fails
     */
    public static String toJsonString(Object obj) {
        if (obj == null) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("[JsonUtil] Failed to serialize object to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Safely reads a JsonNode tree from a raw JSON string.
     *
     * @param json Raw JSON string
     * @return JsonNode tree or null if parsing fails
     */
    public static JsonNode parseTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            log.debug("[JsonUtil] Failed to parse JSON tree: {}", e.getMessage());
            return null;
        }
    }
}
