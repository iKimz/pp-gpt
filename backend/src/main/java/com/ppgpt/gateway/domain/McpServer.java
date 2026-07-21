package com.ppgpt.gateway.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("mcp_servers")
public class McpServer implements Persistable<String> {

    @Id
    private String id;

    private String name;

    @Column("endpoint_url")
    private String endpointUrl;

    @Column("auth_type")
    @Builder.Default
    private String authType = "STATIC_KEY";

    @Column("api_key_encrypted")
    private String apiKeyEncrypted;

    @Column("oauth_authorize_url")
    private String oauthAuthorizeUrl;

    @Column("oauth_token_url")
    private String oauthTokenUrl;

    @Column("oauth_client_id")
    private String oauthClientId;

    @Column("oauth_client_secret_encrypted")
    private String oauthClientSecretEncrypted;

    @Column("oauth_refresh_token_encrypted")
    private String oauthRefreshTokenEncrypted;

    @Column("oauth_access_token_encrypted")
    private String oauthAccessTokenEncrypted;

    @Column("oauth_expires_at")
    private LocalDateTime oauthExpiresAt;

    private String description;

    @Column("is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Transient
    @Builder.Default
    private boolean newEntity = false;

    @Override
    public boolean isNew() {
        return newEntity;
    }
}
