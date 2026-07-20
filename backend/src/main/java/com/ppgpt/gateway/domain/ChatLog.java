package com.ppgpt.gateway.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("chat_logs")
public class ChatLog {

    @Id
    private String id;

    @Column("user_id")
    private String userId;

    @Column("model_id")
    private String modelId;

    @Column("model_display_name")
    private String modelDisplayName;

    @Column("session_id")
    private String sessionId;

    @Column("prompt")
    private String prompt;

    @Column("response")
    private String response;

    @Column("created_at")
    private LocalDateTime createdAt;
}
