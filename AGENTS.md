# Repository Guidelines

## Project Structure & Module Organization

PulseTrack is a microservice-based event-tracking platform. The active service is
`auth-service/`, a Spring Boot authentication and session service. Production
code is under `auth-service/src/main/java/com/kavinda/auth_service/`; keep new
classes within this base package. Runtime configuration belongs in
`auth-service/src/main/resources/application.yaml`, and tests mirror production
packages under `auth-service/src/test/java/`.

`docker-compose.yml` starts local PostgreSQL, Redis, and their admin tools.
`pulsetrack-project-overview.md` describes the planned platform boundaries and
should guide cross-service design decisions.

## Build, Test, and Development Commands

Run commands from the repository root unless noted otherwise:

- `docker compose up -d` starts PostgreSQL on `5432` and Redis on `6379`.
- `cd auth-service && ./mvnw test` compiles and runs the JUnit test suite.
- `cd auth-service && ./mvnw package` produces the Spring Boot JAR in `target/`.
- `cd auth-service && ./mvnw spring-boot:run` starts the service, normally on
  port `8001`.

Use Java 25, as declared in `auth-service/pom.xml`. Stop local dependencies with
`docker compose down` when finished; add `-v` only when intentionally removing
local database and Redis data.

## Coding Style & Naming Conventions

Use standard Java conventions: four-space indentation, one public top-level
class per file, `PascalCase` for classes, `camelCase` for methods and fields, and
`UPPER_SNAKE_CASE` for constants. Name Spring components by responsibility, such
as `SessionService`, `AuthController`, and `UserRepository`. Keep controllers
thin, validate request DTOs, and place authentication and persistence logic in
dedicated services and repositories. No formatter or linter is configured;
match the surrounding code and rely on IDE Java formatting.

## Testing Guidelines

Tests use JUnit 5 and Spring Boot test support. Name test classes `*Tests` and
test methods for observable behavior, for example `loginRejectsInvalidPassword`.
Add focused unit tests for service logic and integration tests for web, security,
JPA, or Redis behavior. Run `./mvnw test` before opening a change. There is no
configured coverage threshold; still cover changed behavior and failure paths.

## Configuration, Commits & Pull Requests

Do not commit credentials. Supply database, Redis, OAuth, and cookie settings
through the environment variables referenced by `application.yaml` (notably
`GOOGLE_CLIENT_SECRET` and `SESSION_COOKIE_SECURE`).

This checkout has no usable Git history to infer a house style. Use concise,
imperative Conventional Commit messages, e.g. `feat(auth): add logout endpoint`
or `fix(session): expire invalid sessions`. Pull requests should state the
motivation, summarize behavior and configuration changes, link relevant issues,
include test results, and attach screenshots only for user-facing changes.
