# pp-gpt — Enterprise AI Gateway

A self-hosted, provider-agnostic AI gateway that proxies streaming LLM requests through a unified REST API. Built with Spring Boot WebFlux (fully reactive, non-blocking) and designed to be deployed with Docker Compose.

---

## Features

- **Multi-provider streaming** — OpenAI, Azure OpenAI, and AWS Bedrock via a pluggable adapter pattern
- **Comprehensive AWS Bedrock Support** — Full support for 100+ models across 18+ providers on AWS Bedrock (Anthropic Claude 3/3.5, Amazon Nova/Titan, Meta Llama 3/3.1/3.2/3.3, Mistral/Pixtral, Cohere Command R+, DeepSeek R1/V3, Qwen3, Gemma 3, AI21 Jamba, etc.)
- **Multimodal Vision support** — Native image and file upload support for vision-capable models (OpenAI GPT-4o, Azure OpenAI, Claude 3, Llama 3.2 Vision, Pixtral, Qwen3 VL) with pre-flight safety checks and UI image preview
- **Model Context Protocol (MCP) & Agentic Tool Calling** — Multi-pass tool execution loop with external MCP servers via JSON-RPC 2.0 (HTTP/SSE), featuring RFC 9207/8414 well-known OAuth discovery, RFC 7591 dynamic client registration, and PKCE S256 popup authentication
- **High-Performance Async Client Caching** — Connection pooling and thread-safe client caching for AWS Bedrock Netty Async Clients, eliminating EventLoop termination overhead
- **Auto Reasoning Tag Sanitization** — Automatic stripping of `<think>` and `<reasoning>` tags for OSS/Reasoning models (DeepSeek, Qwen, Llama) to prevent in-context refusal loops
- **Credit quota system** — Per-user daily credit limits enforced atomically via Redis Lua scripts, with MariaDB as durable source of truth
- **Guardrail safety layer** — Optional per-group safety model that evaluates prompts before forwarding to the primary model
- **Admin dashboard** — Full CRUD for models, user groups, credit rates, users, and MCP servers; paginated null-safe audit logs; executive analytics with live fallback by group and model
- **JWT authentication** — LOCAL (BCrypt password) and AZURE_AD (mock LDAP / JIT provisioning) auth sources
- **AES-256-GCM credential encryption** — Provider API keys are encrypted at rest and never returned in API responses
- **Conversation history** — Configurable sliding context window (`max_history_messages`) per model
- **Token counting** — JTokkit BPE tokenizer with character-based fallback for accurate credit calculation

---

## Architecture

```
┌─────────────┐    SSE/HTTP    ┌──────────────────────────────────────────────┐
│   Frontend  │ ◄────────────► │                 Spring WebFlux               │
│  (Nginx)    │                │                                              │
└─────────────┘                │  AuthController  ChatController  AdminController
                               │       │               │               │
                               │       └───────────────┴───────────────┘
                               │                       │
                               │              Service Layer
                               │  AuthService  ChatService  AdminService
                               │                   │
                               │         AiProviderAdapterFactory
                               │      ┌──────┬──────┴───────────┐
                               │  OpenAI  Azure          AWS Bedrock
                               └──────────────────────────────────────────────┘
                                        │                │
                               ┌────────┴───┐     ┌──────┴──────┐
                               │  MariaDB   │     │    Redis     │
                               │ (R2DBC)    │     │  (Quota)    │
                               └────────────┘     └─────────────┘
```

### Package Layout

```
com.ppgpt.gateway/
├── adapter/          # AI provider adapters (OpenAI, Azure, AWS Bedrock)
├── config/           # Spring configuration (WebClient, Redis, R2DBC)
├── controller/       # REST controllers (Auth, Chat, Admin, Mcp, McpOAuth)
├── domain/           # R2DBC entity classes
├── dto/              # Request/Response DTOs
├── event/            # Spring application events (token usage → dashboard metrics)
├── repository/       # R2DBC reactive repositories
├── security/         # JWT provider, auth filter, security config
├── service/          # Business logic (Auth, Chat, Admin, Quota, Crypto, McpServer)
└── util/             # Core utilities (JsonUtil, SecuritySanitizerUtil, McpConstants, TokenizerUtil)
```

---

## Getting Started

### Prerequisites

| Tool | Version |
|---|---|
| Docker + Docker Compose | v2.x+ |
| Java | 17+ / 21+ (for local dev only) |
| Maven | 3.9+ (for local dev only) |

### Quick Start (Docker Compose)

```bash
# 1. Clone the repository
git clone <repo-url>
cd pp-gpt

# 2. Generate a secure encryption key (64 hex chars = 32 bytes = AES-256)
openssl rand -hex 32

# 3. Set the key in docker-compose.yml under ENCRYPTION_KEY (backend service)

# 4. Start all services
docker compose up -d

# Services:
#   Frontend  → http://localhost:80
#   Backend   → http://localhost:8080
#   MariaDB   → localhost:3306
#   Redis     → localhost:6379
```

> **Note:** The schema is applied automatically on first startup. Two seed users are created:
>
> | Username | Password | Role |
> |---|---|---|
> | `admin` | `admin123` | ADMIN |
> | `admin2` | `admin123` | ADMIN |

---

## Configuration

### Environment Variables

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | R2DBC connection URL | `r2dbc:mariadb://localhost:3306/ppgpt` |
| `DB_USER` | Database username | `ppgpt` |
| `DB_PASS` | Database password | — |
| `REDIS_HOST` | Redis hostname | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `ENCRYPTION_KEY` | 64-char hex string for AES-256-GCM | — (required) |
| `app.mock-ad.enabled` | Enable mock Azure AD auth | `true` |
| `app.jwt.secret` | JWT signing secret | — (required) |
| `app.jwt.expiry-ms` | JWT token lifetime (ms) | `86400000` (24h) |
| `spring.codec.max-in-memory-size` | Max WebFlux in-memory buffer limit for multimodal image payloads | `32MB` |

---

## API Reference

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Login (LOCAL or AZURE_AD) |
| `GET` | `/api/v1/auth/me` | Get current user info + today's usage |

**Login request body:**
```json
{
  "username": "admin",
  "password": "admin123",
  "authSource": "LOCAL"
}
```

---

### Chat

> All chat endpoints require `Authorization: Bearer <token>`

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/chat/models` | List models available to the authenticated user's group |
| `POST` | `/api/v1/chat/stream` | Stream a chat completion (SSE) |
| `GET` | `/api/v1/chat/history?page=0&size=20` | Paginated chat history |

**Stream request body:**
```json
{
  "modelId": "<uuid>",
  "message": "Convert 10 inches to centimeters",
  "images": [
    "data:image/png;base64,iVBORw0KG..."
  ],
  "sessionId": "<client-uuid>",
  "history": [
    { "role": "user",      "content": "Previous message" },
    { "role": "assistant", "content": "Previous response" }
  ]
}
```

**SSE stream response** — each event `data` is a JSON object:
```json
{ "content": "10 inches = ", "done": false }
{ "content": "25.4 cm.",    "done": false }
{ "content": "",             "done": true  }
```

---

## AWS Bedrock Model Compatibility

The gateway supports 100+ models across 18+ model providers on AWS Bedrock via dual-protocol support (Anthropic Protocol + OpenAI/Converse Protocol):

| Model Provider | Supported Models | Multimodal (Vision) | Tool Calling (MCP) | Protocol Used |
|---|---|:---:|:---:|---|
| **Anthropic** | Claude 3, 3.5 (Sonnet, Haiku, Opus), Claude 4 | ✅ | ✅ | Anthropic Messages API |
| **Meta** | Llama 3, 3.1, 3.2 (Vision), 3.3, Llama 4 | ✅ | ✅ | OpenAI / Converse API |
| **Amazon** | Nova Pro, Nova Lite, Nova Micro, Nova Premier | ✅ | ✅ | OpenAI / Converse API |
| **Mistral AI** | Mistral Large 2, Mixtral 8x7B, Pixtral Large | ✅ | ✅ | OpenAI / Converse API |
| **OpenAI / OSS** | GPT-OSS, GPT-4o proxies on Bedrock | ✅ | ✅ | OpenAI Stream API |
| **DeepSeek** | DeepSeek V3, DeepSeek R1 | ✅ | ✅ | OpenAI API + Reasoning Sanitizer |
| **Qwen (Alibaba)** | Qwen3, Qwen3 Coder, Qwen3 VL | ✅ | ✅ | OpenAI API + Reasoning Sanitizer |
| **Google** | Gemma 3, Gemma 4 | ✅ | ✅ | OpenAI / Converse API |
| **Cohere** | Command R, Command R+ | ❌ | ✅ | OpenAI / Converse API |
| **AI21 Labs** | Jamba 1.5 Large, Jamba 1.5 Mini | ❌ | ✅ | OpenAI / Converse API |
| **Others** | MiniMax M2, Moonshot Kimi, NVIDIA Nemotron, Writer, Grok, GLM | ✅ | ✅ | OpenAI / Converse API |

> **Note:** Specialized non-chat APIs (e.g. Stability AI image generators, TwelveLabs video indexers, Cohere Embeddings) are task-specific APIs outside the scope of LLM Chat Completion.

---

## Model Context Protocol (MCP) & Legacy REST Tool Governance

The gateway implements a comprehensive, enterprise-grade Model Context Protocol (MCP 2024-11-05) and Legacy REST Tool Engine:

1. **Protocol Capabilities Discovery**:
   - Performs standard MCP Handshake (`method: "initialize"`) discovering `tools`, `resources`, and `prompts` capabilities.
   - Automatically issues spec-compliant `notifications/initialized` handshake completions.
   - Gracefully handles simplified or non-standard MCP servers (`tools/list` fallback).

2. **Legacy REST & Manual Tool Fallback**:
   - Detects non-MCP REST endpoints and marks them as `⚠️ Legacy REST / Manual`.
   - **🌐 Visual REST Builder**: Interactive REST form mode allowing administrators to configure HTTP methods (`POST`, `GET`, `PUT`, `DELETE`, `PATCH`), subpath endpoints (`/api/v1/payments`), sample JSON bodies, and custom HTTP headers (`X-Tenant-Id`, `X-Custom-Header`) with automatic JSON Schema compilation.
   - **🛠️ Advanced JSON Schema Mode**: Raw JSON Schema editor for defining multi-parameter function schemas.
   - **📄 OpenAPI 3.0 Spec Importer**: Automatically parses raw JSON/YAML OpenAPI specifications, extracting paths, parameters, and request bodies into namespaced LLM tools.
   - **HTTP Endpoint Reachability Ping**: Regularly verifies legacy endpoint health, updating availability badges (`🟢 Available` vs `🔴 Unreachable`) while retaining manual tool definitions.

3. **Vulnerability Guards & Governance**:
   - **Prompt Injection Filter**: Sanitizes tool descriptions against jailbreak prompts (e.g. `ignore all previous instructions`, `system override`) before forwarding to LLMs.
   - **Namespacing Guard**: Prefixes all discovered and manual tools with `server_name__tool_name` to prevent tool name collision across multiple servers.
   - **Per-Group Selective Permission Matrix**: Fine-grained checkbox matrix allowing administrators to enable or disable specific tools per user group.

4. **Multi-Pass Agentic Tool Loop**:
   - **Pass 1 (Tool Discovery & Execution)**: The gateway attaches authorized tools to the LLM prompt. If the model invokes a tool, the gateway executes it via JSON-RPC / REST without leaking raw JSON to the end user.
   - **Pass 2 (Answer Synthesis)**: Tool execution results are passed back to the model with tools cleared, synthesizing a clean, natural language response.

---

## Prometheus & Grafana Monitoring

Standard Prometheus metrics exposed at `/actuator/prometheus`:

| Metric Name | Type | Description |
|---|---|---|
| `ai_gateway_chat_latency_seconds` | Timer | Streaming response latency per provider (`provider="OPENAI|AZURE|AWS_BEDROCK"`) |
| `ai_gateway_chat_requests_total` | Counter | Total chat requests per provider and status (`status="COMPLETE|ERROR|CANCEL"`) |
| `jvm_memory_used_bytes` | Gauge | JVM heap and non-heap memory consumption |

---

## License

This project is intended for educational and internal enterprise use.
