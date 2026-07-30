-- ============================================================
-- Enterprise AI Gateway — MariaDB Schema
-- UUID stored as CHAR(36). DEFAULT (UUID()) requires MariaDB 10.7+
-- ============================================================

-- AI Models
CREATE TABLE IF NOT EXISTS models (
    id                   CHAR(36)     NOT NULL,
    name                 VARCHAR(100) NULL,
    provider             VARCHAR(50)  NOT NULL,
    model_name           VARCHAR(100) NOT NULL,
    endpoint_url         VARCHAR(500) NOT NULL,
    credentials_encrypted TEXT        NOT NULL COMMENT 'AES-256-GCM encrypted JSON. Format varies by provider.',
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    timeout_ms           INT          NOT NULL DEFAULT 30000,
    temperature          DECIMAL(3,2) NOT NULL DEFAULT 0.70,
    system_prompt        TEXT         NULL,
    max_history_messages INT          NOT NULL DEFAULT 10,
    model_type           VARCHAR(20)  NOT NULL DEFAULT 'GENERATION',
    supports_vision      BOOLEAN      NOT NULL DEFAULT FALSE,
    supports_tools       BOOLEAN      NOT NULL DEFAULT TRUE,
    supports_temperature BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_models PRIMARY KEY (id),
    INDEX idx_models_active (is_active)
);

-- Schema Migration for Existing Databases
ALTER TABLE models ADD COLUMN IF NOT EXISTS supports_tools BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE models ADD COLUMN IF NOT EXISTS supports_temperature BOOLEAN NOT NULL DEFAULT TRUE;

-- User Groups
CREATE TABLE IF NOT EXISTS user_groups (
    id                 CHAR(36)       NOT NULL,
    group_name         VARCHAR(100)   NOT NULL,
    max_daily_credits   DECIMAL(15,4) NOT NULL DEFAULT 10000.0000,
    guardrail_model_id CHAR(36)       NULL,
    created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_groups PRIMARY KEY (id),
    CONSTRAINT uq_user_groups_name UNIQUE (group_name),
    CONSTRAINT fk_user_groups_guardrail FOREIGN KEY (guardrail_model_id) REFERENCES models(id) ON DELETE SET NULL
);

-- Users
CREATE TABLE IF NOT EXISTS users (
    id            CHAR(36)     NOT NULL,
    username      VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    auth_source   ENUM('AZURE_AD','LOCAL') NOT NULL DEFAULT 'LOCAL',
    group_id      CHAR(36),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT fk_users_group FOREIGN KEY (group_id) REFERENCES user_groups(id) ON DELETE SET NULL,
    INDEX idx_users_group_id (group_id)
);

-- Group ↔ Model Access (surrogate PK for R2DBC compatibility)
CREATE TABLE IF NOT EXISTS group_model_access (
    id       CHAR(36) NOT NULL,
    group_id CHAR(36) NOT NULL,
    model_id CHAR(36) NOT NULL,
    CONSTRAINT pk_group_model_access PRIMARY KEY (id),
    CONSTRAINT uq_group_model UNIQUE (group_id, model_id),
    CONSTRAINT fk_gma_group FOREIGN KEY (group_id) REFERENCES user_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_gma_model FOREIGN KEY (model_id) REFERENCES models(id) ON DELETE CASCADE,
    INDEX idx_gma_group_id (group_id),
    INDEX idx_gma_model_id (model_id)
);

-- Credit Rates per Model
CREATE TABLE IF NOT EXISTS credit_rates (
    id                CHAR(36)      NOT NULL,
    model_id          CHAR(36)      NOT NULL,
    input_multiplier  DECIMAL(10,6) NOT NULL DEFAULT 1.000000,
    output_multiplier DECIMAL(10,6) NOT NULL DEFAULT 2.000000,
    CONSTRAINT pk_credit_rates PRIMARY KEY (id),
    CONSTRAINT uq_credit_rates_model UNIQUE (model_id),
    CONSTRAINT fk_credit_rates_model FOREIGN KEY (model_id) REFERENCES models(id) ON DELETE CASCADE
);

-- Daily Token Usage
CREATE TABLE IF NOT EXISTS token_usage (
    id           CHAR(36)      NOT NULL,
    user_id      CHAR(36)      NOT NULL,
    usage_date   DATE          NOT NULL,
    credits_used DECIMAL(15,4) NOT NULL DEFAULT 0.0000,
    CONSTRAINT pk_token_usage PRIMARY KEY (id),
    CONSTRAINT uq_token_usage_user_date UNIQUE (user_id, usage_date),
    CONSTRAINT fk_token_usage_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_token_usage_user_date (user_id, usage_date)
);

-- Chat Logs (audit trail)
CREATE TABLE IF NOT EXISTS chat_logs (
    id                 CHAR(36)   NOT NULL,
    user_id            CHAR(36)   NOT NULL,
    model_id           CHAR(36)   NULL,
    model_display_name VARCHAR(100) NULL,
    session_id         VARCHAR(36)NOT NULL,
    prompt             LONGTEXT   NOT NULL,
    response           LONGTEXT,
    created_at         TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_chat_logs PRIMARY KEY (id),
    CONSTRAINT fk_chat_logs_user  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE CASCADE,
    CONSTRAINT fk_chat_logs_model FOREIGN KEY (model_id) REFERENCES models(id) ON DELETE SET NULL,
    INDEX idx_chat_logs_user_id   (user_id),
    INDEX idx_chat_logs_session   (session_id),
    INDEX idx_chat_logs_created   (created_at)
);

-- Executive Dashboard Metrics (Aggregated token usage by group, model, and date)
CREATE TABLE IF NOT EXISTS dashboard_metrics (
    id                  CHAR(36) NOT NULL,
    group_id            CHAR(36) NOT NULL,
    model_id            CHAR(36) NOT NULL,
    usage_date          DATE     NOT NULL,
    total_input_tokens  BIGINT   NOT NULL DEFAULT 0,
    total_output_tokens BIGINT   NOT NULL DEFAULT 0,
    CONSTRAINT pk_dashboard_metrics PRIMARY KEY (id),
    CONSTRAINT uq_dashboard_metrics UNIQUE (group_id, model_id, usage_date),
    CONSTRAINT fk_dm_group FOREIGN KEY (group_id) REFERENCES user_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_dm_model FOREIGN KEY (model_id) REFERENCES models(id) ON DELETE CASCADE,
    INDEX idx_dm_group_date (group_id, usage_date)
);

-- MCP Servers (Internal & External Tool Endpoints)
CREATE TABLE IF NOT EXISTS mcp_servers (
    id                            CHAR(36)     NOT NULL,
    name                          VARCHAR(100) NOT NULL,
    endpoint_url                  VARCHAR(500) NOT NULL,
    auth_type                     VARCHAR(20)  NOT NULL DEFAULT 'STATIC_KEY' COMMENT 'STATIC_KEY or OAUTH2',
    api_key_encrypted             TEXT         NULL COMMENT 'AES-256-GCM encrypted API key / Bearer token',
    oauth_authorize_url           VARCHAR(500) NULL,
    oauth_token_url               VARCHAR(500) NULL,
    oauth_client_id               VARCHAR(255) NULL,
    oauth_client_secret_encrypted TEXT         NULL,
    oauth_refresh_token_encrypted TEXT         NULL,
    oauth_access_token_encrypted  TEXT         NULL,
    oauth_expires_at              TIMESTAMP    NULL,
    description                   TEXT         NULL,
    is_active                     BOOLEAN      NOT NULL DEFAULT TRUE,
    supports_tools                BOOLEAN      NOT NULL DEFAULT TRUE,
    supports_resources            BOOLEAN      NOT NULL DEFAULT FALSE,
    supports_prompts              BOOLEAN      NOT NULL DEFAULT FALSE,
    capability_status             VARCHAR(30)  NOT NULL DEFAULT 'DISCOVERED' COMMENT 'DISCOVERED or NON_MCP_REST',
    created_at                    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_mcp_servers PRIMARY KEY (id),
    INDEX idx_mcp_servers_active (is_active)
);

-- Schema migration additions for existing databases
ALTER TABLE mcp_servers ADD COLUMN IF NOT EXISTS supports_tools BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE mcp_servers ADD COLUMN IF NOT EXISTS supports_resources BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE mcp_servers ADD COLUMN IF NOT EXISTS supports_prompts BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE mcp_servers ADD COLUMN IF NOT EXISTS capability_status VARCHAR(30) NOT NULL DEFAULT 'DISCOVERED';

-- MCP Server Discovered Tools
CREATE TABLE IF NOT EXISTS mcp_tools (
    id                CHAR(36)     NOT NULL,
    mcp_server_id     CHAR(36)     NOT NULL,
    tool_name         VARCHAR(150) NOT NULL,
    namespaced_name   VARCHAR(200) NOT NULL,
    description       TEXT         NULL,
    input_schema      LONGTEXT     NULL,
    is_available      BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_sync_count INT          NOT NULL DEFAULT 0,
    last_synced_at    TIMESTAMP    NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_mcp_tools PRIMARY KEY (id),
    CONSTRAINT uq_mcp_tool_server UNIQUE (mcp_server_id, tool_name),
    CONSTRAINT fk_mcp_tools_server FOREIGN KEY (mcp_server_id) REFERENCES mcp_servers(id) ON DELETE CASCADE,
    INDEX idx_mcp_tools_server (mcp_server_id),
    INDEX idx_mcp_tools_available (is_available)
);

-- Group ↔ MCP Tool Access Configuration
CREATE TABLE IF NOT EXISTS group_mcp_tool_access (
    id          CHAR(36) NOT NULL,
    group_id    CHAR(36) NOT NULL,
    mcp_tool_id CHAR(36) NOT NULL,
    is_enabled  BOOLEAN  NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_gmta PRIMARY KEY (id),
    CONSTRAINT uq_gmta_group_tool UNIQUE (group_id, mcp_tool_id),
    CONSTRAINT fk_gmta_group FOREIGN KEY (group_id) REFERENCES user_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_gmta_tool FOREIGN KEY (mcp_tool_id) REFERENCES mcp_tools(id) ON DELETE CASCADE,
    INDEX idx_gmta_group (group_id),
    INDEX idx_gmta_tool (mcp_tool_id)
);

-- MCP Server Discovered Resources (Read-only Files/Data)
CREATE TABLE IF NOT EXISTS mcp_resources (
    id            CHAR(36)     NOT NULL,
    mcp_server_id CHAR(36)     NOT NULL,
    uri           VARCHAR(500) NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   TEXT         NULL,
    mime_type     VARCHAR(100) NULL,
    is_available  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_mcp_resources PRIMARY KEY (id),
    CONSTRAINT uq_mcp_resource_uri UNIQUE (mcp_server_id, uri(250)),
    CONSTRAINT fk_mcp_resources_server FOREIGN KEY (mcp_server_id) REFERENCES mcp_servers(id) ON DELETE CASCADE,
    INDEX idx_mcp_resources_server (mcp_server_id)
);

-- MCP Server Discovered Prompts (Prompt Templates)
CREATE TABLE IF NOT EXISTS mcp_prompts (
    id             CHAR(36)     NOT NULL,
    mcp_server_id  CHAR(36)     NOT NULL,
    prompt_name    VARCHAR(150) NOT NULL,
    description    TEXT         NULL,
    arguments_json TEXT         NULL,
    is_available   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_mcp_prompts PRIMARY KEY (id),
    CONSTRAINT uq_mcp_prompt_server UNIQUE (mcp_server_id, prompt_name),
    CONSTRAINT fk_mcp_prompts_server FOREIGN KEY (mcp_server_id) REFERENCES mcp_servers(id) ON DELETE CASCADE,
    INDEX idx_mcp_prompts_server (mcp_server_id)
);

-- ─── Seed Data ──────────────────────────────────────────────────────────────

-- Default group (referenced by JIT provisioning)
INSERT IGNORE INTO user_groups (id, group_name, max_daily_credits)
VALUES ('00000000-0000-0000-0000-000000000001', 'DEFAULT_GROUP', 1000.0000);

-- Admin group
INSERT IGNORE INTO user_groups (id, group_name, max_daily_credits)
VALUES ('00000000-0000-0000-0000-000000000002', 'ADMIN_GROUP', 100000.0000);

-- Default local admin user (password = 'admin123' BCrypt hashed)
INSERT IGNORE INTO users (id, username, email, password_hash, auth_source, group_id)
VALUES (
    '00000000-0000-0000-0000-000000000010',
    'admin',
    'admin@ppgpt.local',
    '$2b$12$gTRA7S30bklI4EIFX/5k5e7Tw4VTXR/HxggXjDNBL089TpBAfhhS.',
    'LOCAL',
    '00000000-0000-0000-0000-000000000002'
);

-- Seed admin2 user (password = 'admin123' BCrypt hashed)
INSERT IGNORE INTO users (id, username, email, password_hash, auth_source, group_id)
VALUES (
    '00000000-0000-0000-0000-000000000011',
    'admin2',
    'admin2@ppgpt.local',
    '$2b$12$gTRA7S30bklI4EIFX/5k5e7Tw4VTXR/HxggXjDNBL089TpBAfhhS.',
    'LOCAL',
    '00000000-0000-0000-0000-000000000002'
);
