package com.ppgpt.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateMcpServerRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Endpoint URL is required")
    private String endpointUrl;

    private String authType = "STATIC_KEY";

    private String apiKey;

    private String oauthAuthorizeUrl;

    private String oauthTokenUrl;

    private String oauthClientId;

    private String oauthClientSecret;

    private String description;

    private Boolean isActive = true;
}
