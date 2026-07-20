package com.ppgpt.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    @Pattern(regexp = "LOCAL|AZURE_AD", message = "auth_source must be LOCAL or AZURE_AD")
    private String authSource = "LOCAL";
}
