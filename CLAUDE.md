# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./mvnw clean package -DskipTests

# Run (dev profile, requires env vars or application-dev.properties)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run all tests (uses H2 in-memory + mocked Redis/Email)
./mvnw test

# Run a single test class
./mvnw test -Dtest=AuthControllerIntegrationTest

# Run a single test method
./mvnw test -Dtest=AuthControllerIntegrationTest#shouldLoginSuccessfully

# Compile only (fast check)
./mvnw compile -q
```

## Architecture

Spring Boot 3.3 / Java 17 monolith deployed on **Koyeb** (production), with **PostgreSQL** (Neon), **Redis** (Upstash), and **Cloudinary** for file storage.

### Package layout

```
com.social.backend
├── common/          # Shared exceptions, DTOs, utils
├── components/      # Feature modules (each has controller/service/repository/dto/entity)
│   ├── admin/       # User/post moderation
│   ├── ai/          # Groq/Ollama caption + text generation
│   ├── auth/        # JWT login, register, OAuth2, password reset, email verification
│   ├── bookmark/    # Post bookmarks
│   ├── comment/     # Comments + reactions
│   ├── email/       # Transactional email via Resend API
│   ├── follow/      # Follow/unfollow
│   ├── like/        # Post/comment likes
│   ├── mention/     # @mention extraction service
│   ├── messaging/   # DMs + conversations + online status
│   ├── notification/# In-app notifications (DB + WebSocket push)
│   ├── poll/        # Post polls + votes
│   ├── post/        # Posts with images, reposts, polls
│   ├── report/      # Content reporting
│   ├── storage/     # File upload abstraction (Local / Cloudinary / S3)
│   ├── story/       # Ephemeral stories
│   └── user/        # Profiles, roles, ban
├── config/          # SecurityConfig, WebSocketConfig, RedisConfig, WebConfig, OpenApiConfig
└── security/        # JwtAuthenticationFilter, JwtTokenProvider, JwtUtil, UserDetailsImpl
```

### Authentication flow

Two JWT helper classes coexist for historical reasons:
- **`JwtUtil`** — used by `AuthServiceImpl` and `OAuth2SuccessHandler` (generates tokens from username string, has `extractUsername`)
- **`JwtTokenProvider`** — used by `JwtAuthenticationFilter` and `WebSocketConfig` (validates tokens, has `getUsernameFromToken`)

Both read the same `jwt.secret` / `jwt.expiration` properties and produce compatible tokens.

Auth flow: `POST /api/auth/login` → `AuthServiceImpl` authenticates → issues short-lived JWT (default 24h) + long-lived refresh token stored in DB.

OAuth2 (Google) flow: Google callback → `OAuth2SuccessHandler` → stores `jwt::refreshToken` in Redis under key `oauth2:code:<uuid>` (TTL 2 min) → redirects frontend with `?code=<uuid>` only → frontend calls `GET /api/auth/oauth2/token?code=<uuid>` to exchange for real tokens.

### Real-time (WebSocket / Redis)

WebSocket uses STOMP over SockJS at `/ws`. `WebSocketConfig` validates JWT on CONNECT frames.

`RedisPubSubService` publishes to Redis channel `ws:<username>` so that notifications and messages work across multiple instances. `RedisConfig` subscribes to `ws:*` pattern and routes to `RedisPubSubService`. In tests, all Redis beans are mocked (see `TestRedisConfig`).

### Storage abstraction

`StorageService` has three implementations selected via `storage.type` property:
- `local` — saves to `uploads/` directory, serves via `/uploads/**` static handler
- `cloudinary` — production default
- `s3` — available but not active

`store()` validates images only (jpg/jpeg/png/gif, max 5 MB, content-type must start with `image/`). `storeRaw()` is used for video/audio in stories and voice messages — validates content-type against whitelist (`audio/webm`, `audio/ogg`, `audio/mp4`, `audio/mpeg`, `audio/wav`, `video/mp4`, `video/quicktime`, `video/webm`) and enforces 50 MB max size.

### Profiles

| Profile | DB | Notes |
|---------|-----|-------|
| `dev` | MySQL (local) | Default if no `SPRING_PROFILES_ACTIVE` set |
| `prod` | PostgreSQL (Neon) | `ddl-auto=validate` (schema non modificato), Swagger disabilitato, `server.forward-headers-strategy=framework` per Koyeb |
| `test` | H2 in-memory | `RedisConfig` excluded (`@Profile("!test")`); `TestRedisConfig` provides mocks |

### Rate limiting

`RateLimitInterceptor` (Redis-based, registered in `WebConfig`) protects auth endpoints:

| Endpoint | Limite |
|----------|--------|
| `POST /api/auth/login` | 10 req / min per IP |
| `POST /api/auth/register` | 5 req / min per IP |
| `POST /api/auth/forgot-password` | 5 req / 15 min per IP |
| `POST /api/auth/resend-verification` | 3 req / 15 min per IP |

Chiavi Redis: `rate_limit:<path>:<ip>`. Fail-open se Redis non è raggiungibile. Risponde `429` con header `Retry-After`.

### Security config

`SecurityConfig` defines:
- Public GET endpoints: `/api/users/**`, `/api/posts/**`, `/api/comments/**`, `/api/follows/**`, `/api/stories/user/**`
- Public entirely: `/api/auth/**`, `/oauth2/**`, `/uploads/**`, `/ws/**`, Swagger paths
- Public health check only: `/actuator/health` (usato da Koyeb per liveness probe)
- Swagger è disabilitato in produzione via `application-prod.properties` (`springdoc.swagger-ui.enabled=false`)
- `ADMIN` only: `/api/admin/**`
- Everything else: authenticated

`JwtAuthenticationFilter` runs on every non-public request, loads `UserDetails`, and blocks banned users with 403 before setting the security context.

### Notifications

`NotificationServiceImpl` persists to DB and pushes real-time via `NotificationWebSocketController` → STOMP `/queue/notifications/<userId>`. The WebSocket push has a Redis pub/sub fallback for multi-instance deployments.
