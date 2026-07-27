package com.ppgpt.gateway.util;

/**
 * Common constants for Model Context Protocol (MCP) and Legacy REST Integration.
 */
public final class McpConstants {

    private McpConstants() {
        // Utility class constructor
    }

    // Server capability status constants
    public static final String CAPABILITY_DISCOVERED = "DISCOVERED";
    public static final String CAPABILITY_NON_MCP_REST = "NON_MCP_REST";

    // Authentication types
    public static final String AUTH_STATIC_KEY = "STATIC_KEY";
    public static final String AUTH_OAUTH2 = "OAUTH2";

    // JSON-RPC 2.0 protocol methods
    public static final String METHOD_INITIALIZE = "initialize";
    public static final String METHOD_NOTIFICATIONS_INITIALIZED = "notifications/initialized";
    public static final String METHOD_TOOLS_LIST = "tools/list";
    public static final String METHOD_TOOLS_CALL = "tools/call";
    public static final String METHOD_RESOURCES_LIST = "resources/list";
    public static final String METHOD_PROMPTS_LIST = "prompts/list";

    // Standard HTTP header names
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_BEARER_PREFIX = "Bearer ";
}
