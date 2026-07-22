# PulseTrack

PulseTrack is a real-time application event-tracking and analytics platform. It lets developers create tracking projects, issue API keys, ingest structured application events, process them asynchronously, and query dashboards and reports.

The project is intentionally sized as a practical microservices learning platform: it combines service boundaries, persistence, Redis caching, Kafka processing, authentication, authorization, observability, failure handling, and deployment without attempting to be a full observability suite.

## Current status

| Service | Status | Responsibility |
| --- | --- | --- |
| Authentication Service | Implemented | Google OIDC and GitHub OAuth2 login, local users, RBAC, and Redis-backed browser sessions. |
| API Gateway | Planned | Public routing, session validation, rate limiting, CORS, and trusted identity propagation. |
| Project Service | Planned | Tracking projects, membership, API keys, event schemas, and project configuration. |
| Event Ingestion Service | Planned | Fast API-key-authenticated event intake, validation, deduplication, rate limiting, and Kafka publication. |
| Event Processing Service | Planned | Kafka consumption, normalization, durable event storage, aggregates, retries, and dead-letter handling. |
| Analytics Query Service | Planned | Read-optimized dashboard APIs, aggregate queries, Redis caching, and cache invalidation. |

The [project overview](pulsetrack-project-overview.md) is the detailed target architecture and MVP specification. The implemented authentication service extends its original Google-only MVP description with GitHub OAuth2 support.

## Target architecture

```text
Dashboard Client
      |
      | Redis-backed user session
      v
API Gateway
      |-----------------------> Authentication Service
      |-----------------------> Project Service
      |-----------------------> Analytics Query Service

Tracked Application / SDK
      |
      | Project API key
      v
API Gateway -> Event Ingestion Service -> Kafka: tracking.events.raw.v1
                                             |
                                             v
                                    Event Processing Service
                                      |                |
                                      v                v
                                PostgreSQL      Redis live counters
                                      |
                                      v
                         Kafka: tracking.analytics.updated.v1
                                      |
                                      v
                         Analytics Query Service cache invalidation
```

## What PulseTrack tracks

PulseTrack accepts structured events such as `page_viewed`, `button_clicked`, `user_registered`, `login_failed`, `order_created`, `payment_completed`, `api_request_completed`, and `application_error`.

Events are sent with a project API key, validated at ingestion, published to Kafka, processed idempotently, stored in PostgreSQL, aggregated by time period, and served to dashboards through cached analytics APIs.

## Core platform flow

1. A user signs in through the Authentication Service using Google OIDC or GitHub OAuth2 and receives an opaque Redis-backed session cookie.
2. Through the API Gateway, the user creates a project and receives a project API key from the planned Project Service.
3. An application sends single events or batches using that API key.
4. The planned Event Ingestion Service validates, rate-limits, deduplicates, and publishes accepted events to `tracking.events.raw.v1`, returning `202 Accepted`.
5. The planned Event Processing Service consumes, normalizes, persists, and aggregates events; temporary failures retry and permanent failures go to a dead-letter topic.
6. The planned Analytics Query Service reads precomputed PostgreSQL data, caches frequent queries in Redis, and invalidates relevant cache entries after analytics updates.

## Technology direction

- Java 25 and Spring Boot services
- PostgreSQL for durable service-owned data
- Redis for sessions, caches, deduplication, rate limiting, and live counters
- Apache Kafka for asynchronous event flows
- Spring Security OAuth2 Client for user authentication
- Spring Session Redis for opaque browser sessions
- Actuator, Micrometer, Prometheus, structured logging, tracing, and correlation IDs for observability

Each service owns its own database or PostgreSQL schema. Services must not read another service's tables directly, and Redis is never the durable source of truth for business data.

## Run the implemented service locally

The root Compose file currently provides the Authentication Service's PostgreSQL and Redis dependencies:

```bash
docker compose up -d
```

Configure Google and GitHub credentials, then start the service:

```bash
export GOOGLE_CLIENT_ID='your-google-client-id'
export GOOGLE_CLIENT_SECRET='your-google-client-secret'
export GITHUB_CLIENT_ID='your-github-client-id'
export GITHUB_CLIENT_SECRET='your-github-client-secret'

cd auth-service
./mvnw spring-boot:run
```

The service starts at `http://localhost:8001`. Begin login with:

- `http://localhost:8001/oauth2/authorization/google`
- `http://localhost:8001/oauth2/authorization/github`

Run its tests with `cd auth-service && ./mvnw test`. Stop local dependencies with `docker compose down`; add `-v` only when intentionally removing local PostgreSQL and Redis data.

## Authentication service

The completed [Authentication Service guide](auth-service/README.md) covers local setup, OAuth providers, sessions, local authorization, endpoints, and operating notes. Its companion [OAuth2 implementation blueprint](auth-service/OAUTH2_FLOW.md) is the start-to-finish reference for recreating Google OIDC and GitHub OAuth2 authentication in another Spring Boot project.

The service uses stable provider subjects for identity (`sub` for Google and numeric `id` for GitHub), requires verified email, safely links provider accounts to local users, and keeps application authorization independent from provider roles.

## MVP boundaries

The MVP supports structured events, batches of up to 100 events, fixed dashboard queries, hourly/daily aggregates, one Kafka cluster, and at-least-once processing with idempotency.

It deliberately excludes arbitrary log-file ingestion, full-text search, Elasticsearch/OpenSearch, ClickHouse, custom query languages, session replay, heat maps, advanced funnels, machine-learning anomaly detection, multi-region replication, real-time WebSocket dashboards, Kubernetes in the first deployment, and client SDKs before the HTTP contract stabilizes.

## Development roadmap

1. Finish the platform foundation: Authentication Service, API Gateway, common responses, and correlation IDs.
2. Build Project Service with ownership, API-key hashing/rotation, schemas, Redis configuration cache, and configuration-change events.
3. Build the event pipeline: ingestion, batch validation, deduplication, rate limiting, Kafka publication, processing, persistence, retries, and dead-letter topics.
4. Build analytics: aggregates, live Redis counters, query APIs, caching, and event-driven invalidation.
5. Add production readiness: health/metrics endpoints, structured logging/tracing, Testcontainers integration tests, Dockerfiles, CI/CD, and deployment checks.

## Documentation

- [Complete project overview](pulsetrack-project-overview.md) — service contracts, topic design, database ownership, testing, observability, CI/CD, and definition of done.
- [Authentication Service README](auth-service/README.md) — operating guide for the completed service.
- [OAuth2 implementation blueprint](auth-service/OAUTH2_FLOW.md) — reusable Google and GitHub authentication design guide.

## Security principles

- Never commit database, OAuth client, API-key, or cookie secrets.
- Store only hashed project API keys.
- Do not expose provider tokens, OAuth authorization codes, Redis session cookies, or sensitive event properties in logs or client errors.
- Authenticate dashboard traffic with the user session and ingest traffic with project API keys.
- Enforce project membership before returning project or analytics data.
- Use versioned Kafka event contracts, idempotent consumers, and dead-letter topics for permanent processing failures.
