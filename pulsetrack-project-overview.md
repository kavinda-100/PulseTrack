# PulseTrack — Project Overview

## 1. Project Summary

**PulseTrack** is a real-time application event tracking and analytics platform.

It allows developers to create tracking projects, generate API keys, send structured events from applications, process those events asynchronously, and query dashboards and reports.

Examples of tracked events:

- `page_viewed`
- `button_clicked`
- `user_registered`
- `login_failed`
- `order_created`
- `payment_completed`
- `api_request_completed`
- `application_error`

PulseTrack is intentionally smaller than a complete observability or product-analytics platform. Its goal is to provide hands-on experience with microservice boundaries, asynchronous processing, caching, persistence, security, observability, deployment, and failure handling.

---

## 2. System Scope

PulseTrack contains four business microservices:

1. **Project Service**
2. **Event Ingestion Service**
3. **Event Processing Service**
4. **Analytics Query Service**

It also uses two shared platform services:

5. **Authentication Service**
6. **API Gateway**

Total services in the complete learning system: **six**.

The four business services are the main PulseTrack domain. Authentication and the API Gateway are reusable infrastructure services that can also support future projects.

---

## 3. Why This Project Is Medium Complexity

PulseTrack is more functional than a single CRUD service while remaining achievable.

It demonstrates:

- Independent microservice responsibilities
- Database ownership
- REST communication
- Kafka-based asynchronous communication
- Redis-heavy caching
- Event validation
- Event deduplication
- Batch ingestion
- Stream-style processing
- Precomputed analytics
- Query caching
- API-key authentication
- User authentication and authorization
- Rate limiting
- Retries and dead-letter topics
- Structured logging
- Metrics and distributed tracing
- CI/CD and container deployment

The MVP does not include:

- Full-text log search
- Elasticsearch or OpenSearch
- ClickHouse
- Custom query languages
- Session replay
- Heat maps
- Machine-learning anomaly detection
- Multi-region replication
- Complex funnels
- Real-time WebSocket dashboards

These can be considered only after the core platform works.

---

# 4. High-Level Architecture

```text
Dashboard Client
      |
      | Redis-backed user session
      v
API Gateway
      |
      +-----------------------> Project Service
      |
      +-----------------------> Analytics Query Service


Tracked Application / SDK
      |
      | Project API key
      v
API Gateway
      |
      v
Event Ingestion Service
      |
      | tracking.events.raw.v1
      v
Apache Kafka
      |
      v
Event Processing Service
      |
      +------> PostgreSQL
      |
      +------> Redis live counters
      |
      | tracking.events.processed.v1
      v
Analytics Query Service cache invalidation
```

---

# 5. Service Responsibilities

## 5.1 Authentication Service

The Authentication Service is a shared platform service.

Responsibilities:

- Form-based registration and login
- Google OAuth2 login
- Password hashing
- User roles and permissions
- Redis-backed sessions
- Session-cookie creation
- Logout and session invalidation
- Session validation for the API Gateway

Authentication uses opaque Redis-backed sessions rather than JWTs.

Suggested roles:

- `USER`
- `ADMIN`

Suggested permissions:

- `project:create`
- `project:read`
- `project:update`
- `project:delete`
- `analytics:read`

---

## 5.2 API Gateway

The API Gateway is the only public entry point.

Responsibilities:

- Route requests to the correct service
- Validate user sessions for dashboard endpoints
- Pass trusted user identity to internal services
- Remove identity headers supplied by external clients
- Apply rate limits
- Add or forward correlation IDs
- Handle CORS
- Apply request-size limits
- Provide consistent gateway errors
- Optionally perform client-side load balancing when multiple service instances exist

Example routes:

```text
/api/v1/auth/**        -> Authentication Service
/api/v1/projects/**    -> Project Service
/api/v1/events/**      -> Event Ingestion Service
/api/v1/analytics/**   -> Analytics Query Service
```

The Event Ingestion route accepts project API keys.

Dashboard and project-management routes use the user's Redis-backed session.

---

## 5.3 Project Service

The Project Service manages tracking projects and ingestion credentials.

### Responsibilities

- Create, update, list, and archive tracking projects
- Generate project API keys
- Revoke and rotate API keys
- Configure allowed event names
- Configure event-property rules
- Configure retention settings
- Verify project ownership
- Publish project-configuration changes to Kafka
- Cache project and API-key configuration in Redis

### PostgreSQL ownership

The Project Service owns:

- `projects`
- `project_members`
- `api_keys`
- `event_schemas`
- `project_settings`
- `audit_entries`

### Redis usage

Suggested keys:

```text
pulsetrack:project:{projectId}
pulsetrack:api-key:{keyHash}
pulsetrack:schema:{projectId}:{eventName}
pulsetrack:membership:{projectId}:{userId}
```

These caches reduce database queries made during high-volume event ingestion.

### Main endpoints

```text
POST   /api/v1/projects
GET    /api/v1/projects
GET    /api/v1/projects/{projectId}
PATCH  /api/v1/projects/{projectId}
DELETE /api/v1/projects/{projectId}

POST   /api/v1/projects/{projectId}/api-keys
GET    /api/v1/projects/{projectId}/api-keys
POST   /api/v1/projects/{projectId}/api-keys/{keyId}/rotate
DELETE /api/v1/projects/{projectId}/api-keys/{keyId}

PUT    /api/v1/projects/{projectId}/schemas/{eventName}
GET    /api/v1/projects/{projectId}/schemas
DELETE /api/v1/projects/{projectId}/schemas/{eventName}
```

### Produced Kafka topics

```text
tracking.project.created.v1
tracking.project.updated.v1
tracking.project.archived.v1
tracking.api-key.changed.v1
tracking.schema.changed.v1
```

The Event Ingestion Service consumes configuration-change events and invalidates or refreshes its local Redis-backed configuration cache.

---

## 5.4 Event Ingestion Service

The Event Ingestion Service receives events quickly and publishes them to Kafka.

It should remain mostly stateless so multiple instances can scale horizontally.

### Responsibilities

- Accept single or batched events
- Authenticate requests using a project API key
- Resolve API keys through Redis
- Validate request size and basic event structure
- Apply per-project rate limiting
- Reject malformed events
- Deduplicate event IDs
- Add server metadata
- Publish accepted events to Kafka
- Return `202 Accepted`
- Produce ingestion metrics

### Event request

```json
{
  "eventId": "0190ec80-5844-7a54-b42e-b4cc5a35dc91",
  "eventName": "order_created",
  "anonymousId": "browser-8bd19",
  "userId": "user-471",
  "occurredAt": "2026-07-12T13:30:00Z",
  "properties": {
    "orderId": "ORD-1058",
    "currency": "USD",
    "amount": 49.99
  },
  "context": {
    "applicationVersion": "1.4.0",
    "platform": "web"
  }
}
```

### Batch endpoint

```text
POST /api/v1/events/batch
```

The MVP supports up to 100 events per batch.

### Response

```json
{
  "success": true,
  "message": "Events accepted for processing",
  "timestamp": "2026-07-12T13:30:01",
  "data": {
    "accepted": 98,
    "rejected": 2,
    "batchId": "8b977258-4a48-44e5-9322-f989096f734c"
  }
}
```

### Redis usage

Suggested keys:

```text
pulsetrack:api-key:{keyHash}
pulsetrack:schema:{projectId}:{eventName}
pulsetrack:dedup:{projectId}:{eventId}
pulsetrack:rate:{projectId}:{timeWindow}
pulsetrack:ingestion-status:{batchId}
```

Redis is used for:

- API-key lookup
- Project configuration caching
- Event-schema caching
- Event-ID deduplication
- Rate limiting
- Short-lived batch status
- Temporary failure counters

### Produced Kafka topics

```text
tracking.events.raw.v1
tracking.events.rejected.v1
```

Kafka record key:

```text
projectId
```

This keeps events for the same project in a consistent partition-ordering scope.

---

## 5.5 Event Processing Service

The Event Processing Service transforms raw events into durable analytics data.

### Responsibilities

- Consume raw events from Kafka
- Perform detailed schema validation
- Normalize event names and properties
- Enrich events with server metadata
- Remove or mask configured sensitive fields
- Detect duplicate events
- Persist normalized events
- Update hourly and daily aggregates
- Update Redis live counters
- Publish processed-event notifications
- Retry temporary failures
- Send permanently failing messages to a dead-letter topic

### PostgreSQL ownership

The Event Processing Service owns the analytics-write database.

Suggested tables:

- `events`
- `processed_events`
- `hourly_event_counts`
- `daily_event_counts`
- `daily_active_users`
- `hourly_error_counts`
- `processing_failures`
- `outbox_events`

### Main event table

Suggested columns:

```text
id
event_id
project_id
event_name
anonymous_id
user_id
occurred_at
received_at
processed_at
properties_json
context_json
source_ip_hash
status
```

Important indexes:

```text
(project_id, occurred_at DESC)
(project_id, event_name, occurred_at DESC)
(project_id, user_id, occurred_at DESC)
(project_id, event_id) UNIQUE
```

### Redis live analytics

Suggested keys:

```text
pulsetrack:live:events:{projectId}:{minute}
pulsetrack:live:event:{projectId}:{eventName}:{minute}
pulsetrack:live:users:{projectId}:{day}
pulsetrack:live:errors:{projectId}:{minute}
pulsetrack:top-events:{projectId}:{hour}
```

Possible Redis structures:

- Counters for total events
- HyperLogLog for approximate unique users
- Sorted sets for top event names
- Hashes for per-minute event counts
- Sets for short-lived processing guards

PostgreSQL remains the durable source of truth.

### Consumed Kafka topic

```text
tracking.events.raw.v1
```

### Produced Kafka topics

```text
tracking.events.processed.v1
tracking.analytics.updated.v1
tracking.events.failed.v1
```

### Retry topics

```text
tracking.events.raw.v1-retry
tracking.events.raw.v1-dlt
```

All event consumers must be idempotent because Kafka messages can be delivered more than once.

---

## 5.6 Analytics Query Service

The Analytics Query Service provides read-optimized APIs for dashboards and reports.

### Responsibilities

- Verify project membership
- Return summary metrics
- Return event time series
- Return top event types
- Return active-user metrics
- Return error trends
- Return recent events
- Query precomputed PostgreSQL aggregates
- Cache expensive and common queries
- Invalidate caches after analytics-update events
- Export small reports as JSON or CSV

### Main endpoints

```text
GET /api/v1/analytics/projects/{projectId}/summary
GET /api/v1/analytics/projects/{projectId}/timeseries
GET /api/v1/analytics/projects/{projectId}/events/top
GET /api/v1/analytics/projects/{projectId}/users/active
GET /api/v1/analytics/projects/{projectId}/errors
GET /api/v1/analytics/projects/{projectId}/events/recent
GET /api/v1/analytics/projects/{projectId}/events/{eventName}
```

Example query:

```text
GET /api/v1/analytics/projects/{projectId}/timeseries
    ?eventName=order_created
    &from=2026-07-01T00:00:00Z
    &to=2026-07-12T23:59:59Z
    &interval=HOUR
```

### Redis usage

Suggested cache keys:

```text
pulsetrack:analytics:summary:{projectId}:{range}
pulsetrack:analytics:timeseries:{queryHash}
pulsetrack:analytics:top-events:{projectId}:{range}
pulsetrack:analytics:active-users:{projectId}:{range}
pulsetrack:analytics:errors:{projectId}:{range}
pulsetrack:analytics:recent:{projectId}
```

Suggested TTL strategy:

- Live summary: 10–20 seconds
- Recent events: 15–30 seconds
- Hourly time series: 1–5 minutes
- Historical reports: 10–30 minutes
- Project membership: 1–5 minutes

The service consumes `tracking.analytics.updated.v1` to invalidate affected hot caches.

---

# 6. Event Flow

## 6.1 User creates a tracking project

1. The user authenticates through the Authentication Service.
2. The client calls the Project Service through the API Gateway.
3. The Project Service creates the project in PostgreSQL.
4. The Project Service creates an API key and stores only its secure hash.
5. Project configuration is cached in Redis.
6. A `tracking.project.created.v1` event is published.

## 6.2 An application sends events

1. The application sends a batch with its project API key.
2. The API Gateway applies request-size and rate rules.
3. The Event Ingestion Service resolves the API key from Redis.
4. Basic validation and deduplication occur.
5. Accepted events are published to `tracking.events.raw.v1`.
6. The client receives `202 Accepted`.

## 6.3 Events are processed

1. The Event Processing Service consumes raw events.
2. Detailed validation and normalization occur.
3. Events are persisted in PostgreSQL.
4. Aggregate tables are updated.
5. Redis live counters are updated.
6. `tracking.analytics.updated.v1` is published.
7. Failed records are retried or sent to a DLT.

## 6.4 A user opens the dashboard

1. The browser sends the Redis-backed session cookie.
2. The API Gateway validates the session.
3. The Analytics Query Service verifies project membership.
4. Redis is checked for the requested report.
5. PostgreSQL is queried on a cache miss.
6. The result is cached and returned.

---

# 7. Kafka Topic Design

```text
tracking.project.created.v1
tracking.project.updated.v1
tracking.project.archived.v1
tracking.api-key.changed.v1
tracking.schema.changed.v1

tracking.events.raw.v1
tracking.events.rejected.v1
tracking.events.processed.v1
tracking.events.failed.v1

tracking.analytics.updated.v1
```

Every event uses a common envelope:

```json
{
  "eventId": "0190ec80-5844-7a54-b42e-b4cc5a35dc91",
  "eventType": "tracking.event.raw",
  "eventVersion": 1,
  "occurredAt": "2026-07-12T13:30:00Z",
  "producedAt": "2026-07-12T13:30:01Z",
  "source": "event-ingestion-service",
  "correlationId": "42572b18-10c6-48f7-b628-01c2d47ba18b",
  "projectId": "781b886c-208b-4d57-b3ef-aa087b1a6c6d",
  "payload": {}
}
```

Rules:

- Event types and versions are explicit.
- Consumers reject unsupported versions.
- Consumers are idempotent.
- Retry only temporary failures.
- Validation failures do not retry indefinitely.
- Every service logs `eventId`, `projectId`, and `correlationId`.
- Sensitive data must not be placed in Kafka headers or logs.
- Schema evolution must remain backward compatible where possible.

---

# 8. Database Ownership

Each service owns its own database or PostgreSQL schema.

## Project Service database

```text
projects
project_members
api_keys
event_schemas
project_settings
audit_entries
```

## Event Processing database

```text
events
processed_events
hourly_event_counts
daily_event_counts
daily_active_users
hourly_error_counts
processing_failures
outbox_events
```

## Authentication Service database

```text
users
oauth_accounts
roles
permissions
user_roles
role_permissions
```

Services must not directly query another service's tables.

Redis is not the source of truth for durable business data.

---

# 9. Shared Response Format

Successful JSON responses use:

```java
public record ApiResponse<T>(
        boolean success,
        String message,
        LocalDateTime timestamp,
        T data
) {
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, LocalDateTime.now(), data);
    }
}
```

Errors use the common `ErrorResponse` format and global exception-handling pattern defined for the platform.

Recommended common exceptions:

- `ResourceNotFoundException`
- `ResourceConflictException`
- `InvalidRequestException`
- `UnauthorizedException`
- `ForbiddenOperationException`
- `RateLimitExceededException`
- `InvalidApiKeyException`
- `DuplicateEventException`
- `UnsupportedEventVersionException`
- `EventValidationException`
- `ExternalServiceUnavailableException`

Unexpected production errors must return a safe generic message rather than exposing an internal exception message.

---

# 10. Standard Service Folder Structure

Each service follows the shared structure and adds domain-specific packages when required.

```text
src/main/java/com/kavinda/{service}
├── controller
├── service
├── repository
├── entity
├── config
├── exception
│   └── types
├── dto
│   ├── request
│   ├── response
│   └── event
├── mapper
├── security
├── messaging
│   ├── producer
│   └── consumer
├── cache
├── validation
└── util

src/main/resources
├── application.yml
├── application-local.yml
├── application-test.yml
└── db/migration

src/test/java/com/kavinda/{service}
├── controller
├── service
├── repository
├── messaging
└── integration
```

---

# 11. Observability

Every service uses:

- SLF4J
- Logback
- Structured JSON logs
- Spring Boot Actuator
- Micrometer
- Prometheus metrics
- Micrometer Tracing
- Correlation IDs

Required service endpoints:

```text
/actuator/health
/actuator/health/liveness
/actuator/health/readiness
/actuator/info
/actuator/prometheus
```

Suggested custom metrics:

```text
pulsetrack.events.received
pulsetrack.events.accepted
pulsetrack.events.rejected
pulsetrack.events.processed
pulsetrack.events.duplicate
pulsetrack.events.failed
pulsetrack.kafka.processing.duration
pulsetrack.cache.hit
pulsetrack.cache.miss
pulsetrack.analytics.query.duration
pulsetrack.analytics.cache.eviction
pulsetrack.rate_limit.rejected
```

Suggested log fields:

```text
service
environment
correlationId
traceId
spanId
projectId
userId
eventId
eventName
topic
partition
offset
durationMs
status
```

Do not log:

- Raw API keys
- Passwords
- OAuth tokens
- Session cookies
- Sensitive event properties
- Full stack traces in client responses

---

# 12. Local Infrastructure

Docker Compose runs:

- PostgreSQL
- Redis
- Kafka
- Prometheus
- Grafana

The Java services may run locally through Maven during development.

Each service still includes its own production `Dockerfile`.

Optional later additions:

- Kafka UI
- pgAdmin
- RedisInsight
- OpenTelemetry Collector
- Tempo or Zipkin

---

# 13. Testing Strategy

## Unit tests

- Event validation
- API-key verification
- Schema rules
- Deduplication
- Cache-key construction
- Aggregation calculations
- Authorization and ownership
- DTO mapping

## Integration tests

Use Testcontainers for:

- PostgreSQL
- Redis
- Kafka

Test scenarios:

- Create a project and API key.
- Send a valid event batch.
- Reject invalid API keys.
- Reject malformed events.
- Rate-limit excessive requests.
- Prevent duplicate event insertion.
- Consume Kafka events.
- Retry temporary failures.
- Send permanent failures to the DLT.
- Update aggregate tables.
- Read cached analytics.
- Invalidate cached analytics after updates.
- Prevent users from reading another project's analytics.

## Contract tests

Verify:

- Kafka event-envelope compatibility
- REST API response shapes
- API Gateway identity headers
- Versioned event contracts

---

# 14. CI/CD

Each microservice has an independent GitHub Actions workflow.

## Pull request pipeline

1. Check out source.
2. Install Java 25.
3. Restore Maven cache.
4. Run formatting and static checks.
5. Run unit tests.
6. Run Testcontainers integration tests.
7. Run `./mvnw -B verify`.
8. Upload test reports.

## Main branch or release pipeline

1. Repeat CI checks.
2. Build the service JAR.
3. Build the Docker image.
4. Tag with commit SHA and version.
5. Scan the image.
6. Push to a container registry.
7. Deploy the service.
8. Run readiness checks.
9. Stop promotion or roll back on failure.

Services should be independently buildable and deployable.

---

# 15. MVP Functionality

The MVP includes:

## Authentication and Gateway

- Form login
- Google OAuth2 login
- Redis-backed session
- Gateway routing
- Session validation
- API-key route support
- Redis rate limiting
- Correlation IDs

## Project Service

- Project CRUD
- Project ownership
- API-key generation
- API-key revocation
- Basic event schemas
- Redis configuration cache
- Kafka configuration events

## Event Ingestion Service

- Single-event endpoint
- Batch endpoint
- API-key authentication
- Basic validation
- Redis deduplication
- Redis rate limiting
- Kafka publication
- `202 Accepted` response

## Event Processing Service

- Kafka consumption
- Detailed validation
- Event normalization
- PostgreSQL event persistence
- Hourly aggregation
- Daily aggregation
- Live Redis counters
- Retry and DLT
- Idempotent processing

## Analytics Query Service

- Summary endpoint
- Time-series endpoint
- Top-events endpoint
- Active-user endpoint
- Error-summary endpoint
- Recent-events endpoint
- Redis query caching
- Event-driven cache invalidation

---

# 16. Scope Guardrails

To keep the project medium rather than highly complex:

- Support structured events only.
- Do not ingest arbitrary log files.
- Limit batch size to 100 events.
- Keep event properties as validated JSON.
- Support fixed dashboard queries rather than a query language.
- Store raw events for a limited development retention period.
- Start with hourly and daily aggregates.
- Use one Kafka cluster.
- Use one PostgreSQL server with separate databases or schemas locally.
- Use one Redis instance locally with clear key prefixes.
- Use at-least-once processing plus idempotency.
- Delay transactional-outbox implementation until the base flow works.
- Avoid Kubernetes during the first deployment.
- Do not build client SDKs until the HTTP ingestion contract is stable.

---

# 17. Suggested Development Order

## Phase 1 — Platform foundation

1. Authentication Service
2. Redis session management
3. API Gateway
4. Correlation IDs and common responses

## Phase 2 — Project management

5. Project Service
6. Project and membership model
7. API-key generation and hashing
8. Redis API-key cache
9. Project configuration events

## Phase 3 — Event pipeline

10. Event Ingestion Service
11. Batch validation
12. Redis deduplication and rate limiting
13. Kafka raw-event publication
14. Event Processing Service
15. PostgreSQL persistence
16. Retry, DLT, and idempotency

## Phase 4 — Analytics

17. Hourly and daily aggregates
18. Redis live counters
19. Analytics Query Service
20. Query caching
21. Cache invalidation events

## Phase 5 — Production readiness

22. Actuator and Prometheus
23. Structured logging and tracing
24. Testcontainers integration tests
25. Dockerfiles
26. GitHub Actions
27. Deployment and readiness checks

---

# 18. Definition of Done

PulseTrack MVP is complete when:

- Users can authenticate and create tracking projects.
- Projects can generate and revoke API keys.
- Applications can submit event batches.
- Invalid keys and malformed events are rejected.
- Accepted events return quickly with `202 Accepted`.
- Events travel through Kafka.
- Duplicate events do not create duplicate records.
- Events are persisted in PostgreSQL.
- Hourly and daily analytics are generated.
- Redis caches project configuration and analytics queries.
- Redis handles ingestion deduplication and rate limiting.
- Analytics endpoints return summaries and time series.
- Users cannot access another user's projects.
- Failed Kafka messages are retried and eventually sent to a DLT.
- All services expose health and Prometheus endpoints.
- Logs contain correlation and trace identifiers.
- Integration tests run with PostgreSQL, Redis, and Kafka.
- Every service has an independent CI pipeline and Docker image.
