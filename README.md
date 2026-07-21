# pp-gpt — Enterprise AI Gateway

A self-hosted, provider-agnostic AI gateway that proxies streaming LLM requests through a unified REST API. Built with Spring Boot WebFlux (fully reactive, non-blocking) and designed to be deployed with Docker Compose.

---

## Features

- **Multi-provider streaming** — OpenAI, Azure OpenAI, and AWS Bedrock via a pluggable adapter pattern
- **Multimodal support** — native image and file upload support for vision-capable models (OpenAI GPT-4o, Azure OpenAI, Claude 3 on AWS Bedrock) with pre-flight safety checks and UI image preview
- **Credit quota system** — per-user daily credit limits enforced atomically via Redis Lua scripts, with MariaDB as durable source of truth
- **Guardrail safety layer** — optional per-group safety model that evaluates prompts before forwarding to the primary model
- **Admin dashboard** — full CRUD for models, user groups, credit rates, and users; paginated audit logs; executive analytics by group and model
- **JWT authentication** — LOCAL (BCrypt password) and AZURE_AD (mock LDAP / JIT provisioning) auth sources
- **AES-256-GCM credential encryption** — provider API keys are encrypted at rest and never returned in API responses
- **Conversation history** — configurable sliding context window (`max_history_messages`) per model
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
├── controller/       # REST controllers (Auth, Chat, Admin)
├── domain/           # R2DBC entity classes
├── dto/              # Request/Response DTOs
├── event/            # Spring application events (token usage → dashboard metrics)
├── repository/       # R2DBC reactive repositories
├── security/         # JWT provider, auth filter, security config
├── service/          # Business logic (Auth, Chat, Admin, Quota, Crypto)
└── util/             # TokenizerUtil (JTokkit BPE)
```

---

## Getting Started

### Prerequisites

| Tool | Version |
|---|---|
| Docker + Docker Compose | v2.x+ |
| Java | 21+ (for local dev only) |
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

**Auth response** includes `token` (Bearer JWT), `role`, `groupName`, `maxDailyCredits`, and `creditsUsedToday`.

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
  "message": "Describe what is in this image.",
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
{ "content": "Hello,",  "done": false }
{ "content": " world!", "done": false }
{ "content": "",        "done": true  }
```

---

### Admin (ROLE_ADMIN only)

> All admin endpoints require `Authorization: Bearer <admin-token>`

#### Models
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/admin/models` | List all models |
| `GET` | `/api/v1/admin/models/{id}` | Get a model |
| `POST` | `/api/v1/admin/models` | Create a model |
| `PUT` | `/api/v1/admin/models/{id}` | Update a model |
| `DELETE` | `/api/v1/admin/models/{id}` | Delete a model |

**Model payload:**
```json
{
  "name": "GPT-4o Production",
  "provider": "OPENAI",
  "modelName": "gpt-4o",
  "endpointUrl": "https://api.openai.com/v1/chat/completions",
  "credentials": "{\"apiKey\": \"sk-...\"}",
  "isActive": true,
  "timeoutMs": 60000,
  "temperature": 0.7,
  "systemPrompt": "You are a helpful assistant.",
  "maxHistoryMessages": 10,
  "modelType": "GENERATION",
  "supportsVision": true
}
```

> `credentials` is **write-only** — encrypted server-side (AES-256-GCM) and never returned in GET responses.

#### User Groups
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/admin/groups` | List all groups with allowed model IDs |
| `POST` | `/api/v1/admin/groups` | Create a group |
| `PUT` | `/api/v1/admin/groups/{id}` | Update group + replace model access list |
| `DELETE` | `/api/v1/admin/groups/{id}` | Delete a group |

#### Credit Rates
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/admin/credits` | List all credit rates |
| `POST` | `/api/v1/admin/credits` | Upsert credit rate for a model |
| `DELETE` | `/api/v1/admin/credits/{id}` | Delete a credit rate |

#### Users
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/admin/users` | List all users |
| `POST` | `/api/v1/admin/users` | Create a user |
| `PUT` | `/api/v1/admin/users/{id}` | Update user email / group / password |
| `DELETE` | `/api/v1/admin/users/{id}` | Delete a user |

#### Analytics & Audit
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/admin/dashboard/analytics?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD` | Aggregated token + credit usage by group and model |
| `GET` | `/api/v1/admin/audit-logs?modelId=&startDate=&endDate=&page=0&size=20` | Paginated chat audit log |

---

## AI Provider Configuration

### Provider Keys

| `provider` value | Adapter |
|---|---|
| `OPENAI` | OpenAI-compatible (also works with LiteLLM, Ollama, etc.) |
| `AZURE` | Azure OpenAI Service |
| `AWS_BEDROCK` | AWS Bedrock (Claude via LiteLLM proxy) |

> If no adapter matches the `provider` field, the factory falls back to the `OPENAI` adapter.

### Credential Formats

**OpenAI / OpenAI-compatible:**
```json
{ "apiKey": "sk-..." }
```

**Azure OpenAI:**
```json
{ "apiKey": "...", "apiVersion": "2024-02-01" }
```

**AWS Bedrock:**
```json
{ "apiKey": "...", "region": "us-east-1" }
```

---

## Credit & Quota System

Credits are deducted per request based on token usage and per-model multipliers:

```
credits = (inputTokens × inputMultiplier) + (outputTokens × outputMultiplier)
```

**Flow:**
1. **Pre-flight** — estimate input tokens → atomically pre-deduct from Redis (Lua script)
2. **Post-stream** — count actual output tokens → correct the Redis balance → upsert to `token_usage` table
3. **Redis fallback** — if Redis is unavailable, quota is checked directly against the DB

Credit limits are per-user per-day (`max_daily_credits` on the user's group) and reset at UTC midnight.

---

## Guardrail Safety

A group can optionally set a `guardrailModelId` pointing to a designated safety-classifier model (`modelType: GENERATION`).

When configured, every request is first sent to the guardrail model with the prompt:
```
Evaluate the following user prompt for safety.
Respond with only 'SAFE' or 'UNSAFE'. Prompt: <user_message>
```

- Response contains `UNSAFE` → request is **blocked**, estimated credits are refunded
- Guardrail call fails → system **fail-opens** (request proceeds)

---

## Prometheus & Grafana Monitoring

The backend automatically collects and exposes standard Prometheus metrics at `/actuator/prometheus` (no authentication required).

### Key Metrics Exposed

| Metric Name | Type | Description |
|---|---|---|
| `ai_gateway_chat_latency_seconds` | Timer | Streaming response latency per provider (`provider="OPENAI\|AZURE\|AWS_BEDROCK"`) |
| `ai_gateway_chat_requests_total` | Counter | Total chat requests per provider and final status (`status="COMPLETE\|ERROR\|CANCEL"`) |
| `jvm_memory_used_bytes` | Gauge | JVM heap and non-heap memory consumption |
| `system_cpu_usage` | Gauge | System CPU utilization percentage |
| `r2dbc_connections_active` | Gauge | Active R2DBC MariaDB connection pool count |

### Prometheus Integration (`prometheus.yml`)

Add the following scrape target to your `prometheus.yml` configuration:

```yaml
scrape_configs:
  - job_name: 'pp-gpt-gateway'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['ppgpt-backend:8080']  # Replace with container name or host IP
```

### Useful Grafana PromQL Queries

- **Request Rate per Provider:** `sum(rate(ai_gateway_chat_requests_total[5m])) by (provider, status)`
- **P95 Streaming Latency:** `histogram_quantile(0.95, sum(rate(ai_gateway_chat_latency_seconds_bucket[5m])) by (le, provider))`
- **Heap Memory Usage:** `jvm_memory_used_bytes{area="heap"}`

---

## Database Schema

| Table | Purpose |
|---|---|
| `users` | User accounts (LOCAL + AZURE_AD) |
| `user_groups` | Groups with daily credit limits and optional guardrail model |
| `models` | AI model configurations (encrypted credentials) |
| `group_model_access` | Many-to-many: which groups can access which models |
| `credit_rates` | Per-model input/output credit multipliers |
| `token_usage` | Daily per-user credit usage (durable quota store) |
| `chat_logs` | Full prompt + response audit trail |
| `dashboard_metrics` | Aggregated daily token usage by group and model |

---

## Security

- JWT tokens signed with a configurable HMAC secret; configurable expiry (default 24h)
- `ROLE_ADMIN` required for all `/api/v1/admin/**` endpoints
- `ROLE_USER` or `ROLE_ADMIN` required for `/api/v1/chat/**`
- `/api/v1/auth/**` is public (no token required)
- Provider credentials encrypted with AES-256-GCM before storing in the database
- Passwords hashed with BCrypt (strength 12)

---

## CI/CD Pipeline

The project includes an automated GitHub Actions pipeline (`.github/workflows/ci-cd.yml`) that runs on every `push` or `pull_request` to the `main` branch.

### Automated Pipeline Jobs

1. **Backend CI (`backend-ci`)**: Sets up JDK 17, caches Maven dependencies, executes unit tests (`mvn clean test`), and verifies the backend Docker build.
2. **Frontend CI (`frontend-ci`)**: Sets up Node.js 20, caches npm packages, runs production asset build (`npm run build`), and verifies the frontend Docker build.
3. **Security Scan (`security-scan`)**: Executes Aqua Security Trivy filesystem scan to detect high and critical vulnerabilities across the codebase.

---

## Local Development

```bash
# Start only infrastructure services
docker compose up -d mariadb redis

# Run the backend locally
cd backend
mvn spring-boot:run

# Backend available at http://localhost:8080
```

---

## License

This project is intended for educational and internal use.
