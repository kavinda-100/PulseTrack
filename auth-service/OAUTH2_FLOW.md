# OAuth2 Implementation Blueprint: Google OIDC and GitHub OAuth2

This document is the implementation guide for building the PulseTrack-style OAuth2 authentication service in another
Spring Boot project. It starts with an empty service and ends with browser login through Google and GitHub, durable
local accounts, role-based authorization, and opaque Redis-backed sessions.

It describes the architecture and responsibilities, not source-code examples. Adapt package names, API paths, frontend
URLs, role names, and domain permissions to the new project; preserve the identity and session safety rules unless the
new project deliberately adopts a different security model.

## 1. Target outcome

The completed service has these boundaries:

- Google supplies an OpenID Connect identity. Google `sub` is the stable provider identity.
- GitHub supplies an OAuth2 identity. GitHub numeric `id` is the stable provider identity; the service separately
  requests a primary, verified email.
- PostgreSQL owns the application's canonical users, provider-account links, roles, and permissions.
- Redis owns short-lived authenticated browser sessions. The browser receives only an opaque `SESSION` cookie, not an
  application JWT.
- The application authorizes requests with its own roles and permissions after external identity has been verified.

```text
Browser -> /oauth2/authorization/{provider} -> Google or GitHub
Browser <- /login/oauth2/code/{provider}    <- authorization code + state

Auth Service -> provider token/user-info APIs
Auth Service -> PostgreSQL: local identity + authorization
Auth Service -> Redis: authenticated Spring Session
Auth Service -> Browser: SESSION cookie, then frontend success redirect
```

## 2. Design rules to decide before writing files

Adopt these rules first. They prevent the most common account-linking and session-revocation mistakes.

1. Treat the provider subject as the permanent external identity. Never use email as the provider account key.
2. Require a verified email before creating or linking a local account. For GitHub, require a primary and verified email
   from the emails API.
3. Search by `(provider, providerSubject)` before considering a matching local email.
4. When a new provider account has a verified email matching an existing local user, link it to that user; otherwise
   create a new local user.
5. Keep provider-specific profile fields separate from the canonical application profile. A later provider login must
   not overwrite the application's canonical email, name, or avatar without an explicit profile-update policy.
6. Give the authenticated principal the local user ID as its name. Redis session indexing and bulk revocation then work
   independently of Google or GitHub.
7. Use an opaque cookie backed by Redis for browser access. Provider access tokens are only for retrieving provider data
   during login, not application credentials.
8. Reject suspended, disabled, or otherwise inactive users before creating an authenticated principal.
9. Do not log OAuth client secrets, authorization codes, access tokens, session IDs, cookies, or full provider identity
   payloads.

## 3. Build order from zero

### Step 1: Create the project and dependencies

Create a Spring Boot web service using Java 25. Add dependencies for Spring Web MVC, Spring Security OAuth2 Client,
Spring Data JPA, PostgreSQL, Spring Session Data Redis, validation, and a Spring HTTP client. Add Spring Security,
OAuth2 Client, JPA, Redis Session, and Web MVC test support. Actuator and Prometheus support are optional but useful for
operations.

In this project, dependency selection and Java version are defined by `pom.xml`.

### Step 2: Provision local infrastructure

Run PostgreSQL for durable identities and authorization data, and Redis for sessions. The repository's
`docker-compose.yml` starts PostgreSQL on host port `5433` and Redis on `6379`; Adminer and Redis Insight are optional
local inspection tools.

Before startup, supply these secrets through environment variables:

- `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`
- `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET`

Set `FRONTEND_URL` to the browser application origin. Set `BOOTSTRAP_SUPER_ADMIN_EMAIL` only when the new project needs
a first administrative user. Use `SESSION_COOKIE_SECURE=false` only for HTTP local development.

### Step 3: Register both providers

Create a Google **Web application** OAuth client and a GitHub OAuth App. Register callback URLs that exactly match the
application configuration.

| Provider | Local callback                                   | Required scopes              | Stable subject |
|----------|--------------------------------------------------|------------------------------|----------------|
| Google   | `http://localhost:8001/login/oauth2/code/google` | `openid`, `profile`, `email` | OIDC `sub`     |
| GitHub   | `http://localhost:8001/login/oauth2/code/github` | `read:user`, `user:email`    | numeric `id`   |

For production, register the HTTPS URL of that deployment with each provider. Scheme, host, port, and path must all
match exactly. The source service currently has local callback URLs in its application configuration, so production
redirects need explicit configuration/code review.

### Step 4: Configure application properties

Configure these areas before wiring security:

- HTTP port, error-message exposure, and session cookie name/path/lifetime.
- PostgreSQL URL, username, password, and JDBC driver.
- JPA settings, UTC timestamps, and the schema-management approach.
- Redis host, port, timeout, database, indexed session repository, namespace, save mode, and flush mode.
- Google and GitHub OAuth2 client registrations, scopes, authorization-code grant, and redirect URLs.
- Google issuer metadata for OIDC validation.
- Frontend origin, bootstrap administrator email, and GitHub email API URL/version.

The source implementation uses a one-day `SESSION` cookie/session timeout, `HttpOnly`, `SameSite=Lax`, cookie path `/`,
Redis namespace `pulsetrack:auth:sessions`, and an environment-controlled secure-cookie flag. Configure the equivalent
values for the new project.

### Step 5: Model local identity and authorization

Create a canonical user table and a provider-account table. The canonical user contains the application user UUID,
canonical email and normalized email, canonical display name/avatar, verified status, account status, timestamps,
optimistic-lock version, and assigned roles.

The provider account contains its own UUID, parent user, provider enum, provider subject, provider email and
verification status, username, display name, avatar, and timestamps. Enforce both uniqueness constraints:

- `(provider, provider_subject)` is unique globally.
- `(user_id, provider)` is unique, so one local user can link one account from each provider.

Create role and permission tables with many-to-many user-role and role-permission relations. Define an account-status
enum with at least `ACTIVE`, `SUSPENDED`, and `DISABLED`.

### Step 6: Add data access and local authority mapping

Implement repositories that can:

- Find a user by normalized email.
- Load a user with roles and permissions.
- Load a provider account by provider and provider subject together with its user and authorities.
- Find an existing provider account for a user/provider pair.
- Look up roles and permissions by their enum names.

Build a mapper that turns each local role into `ROLE_<ROLE_NAME>` and each local permission into its authority string,
such as `profile:read`. This keeps providers responsible only for authentication; the application remains responsible
for authorization.

### Step 7: Bootstrap roles and permissions

At application startup, ensure every declared permission exists and create or update the role definitions. The source
service has `USER`, `ADMIN`, and `SUPER_ADMIN`, with the hierarchy `SUPER_ADMIN > ADMIN > USER`.

The bootstrap user receives `USER` on creation and additionally receives `SUPER_ADMIN` when their normalized email
matches the configured bootstrap email. Do not use a provider-specific email match without normalizing it.

### Step 8: Build the shared social-authentication service

Create a provider-neutral identity object containing provider, provider subject, email, email verification state,
username, display name, and avatar URL. Both provider-specific user services convert provider data into this object.

The shared service must:

1. Reject a missing provider, subject, email, or verified-email state.
2. Look up a provider account first.
3. If found, reject inactive users, refresh provider metadata and login timestamps, and reload roles/permissions.
4. If not found, find a canonical user by normalized verified email or create one with initial roles.
5. Reject a duplicate account for the same provider on the chosen local user.
6. Create the provider-account link, update login timestamps, and return the user with roles and permissions loaded.

Run this work in one database transaction. The resulting local user is the only input used to create application
authorities and a session principal.

### Step 9: Build the Google OIDC user service

Use Spring Security's OIDC user service to validate the Google OIDC response through the configured issuer and load the
OIDC user. Allow only the `google` registration ID in this component.

Create the shared external identity from Google claims:

- Provider subject: `sub`
- Email and verification: OIDC email claims
- Username: preferred username
- Display name: full name, then preferred username, then email
- Avatar: picture

Pass it to the shared social-authentication service. Combine provider authorities with the mapped local roles and
permissions. Return an OIDC-compatible application principal that preserves ID token, user-info, and claims while
exposing local user ID, canonical profile, provider, and application authorities.

### Step 10: Build the GitHub OAuth2 user service

Use Spring Security's default OAuth2 user service to fetch GitHub's user attributes. Allow only the `github`
registration ID in this component. Require GitHub `id` and `login`; treat `name` and `avatar_url` as optional.

Use the provider access token only to request GitHub's emails endpoint. Send GitHub's bearer authorization header,
accepted media type, and configured API-version header. Select only a nonblank email marked both primary and verified.
Convert an HTTP/API error, missing email list, or missing verified primary email into an OAuth authentication failure.

Use GitHub numeric `id`, verified primary email, login, resolved display name, and avatar to create the shared external
identity. Authenticate it through the shared service, combine provider/local authorities, and return an
OAuth2-compatible application principal with the local user ID as its name.

### Step 11: Wire the security filter chain

Configure CORS for exactly the frontend origin and enable credentialed requests. Disable HTTP Basic and form login.
Permit the OAuth authorization and callback paths and the public status path; require authentication for everything
else.

Enable OAuth2 login with both custom user services: use the OIDC service for Google and the plain OAuth2 service for
GitHub. Configure the login success redirect to the frontend callback route and the failure redirect to its error route.
Configure logout to invalidate the HTTP session, clear authentication, delete `SESSION`, and redirect to the frontend
root.

Return `401` for unauthenticated protected requests and `403` for authenticated users missing an authority. Review CSRF
deliberately: this source implementation disables it, which requires a separate production risk decision for a
cookie-authenticated browser application.

### Step 12: Enable Redis sessions and expose session-aware routes

Enable indexed Redis HTTP sessions. The repository must index sessions by the local user UUID returned from the
principal name. Expose a lightweight public authentication-status route and a protected current-user profile route.
Logout is handled by Spring Security's logout endpoint.

To support user suspension, add a session service that finds sessions by principal name and deletes each one. Persist
the user's suspended state before revoking Redis sessions. Reactivating a user must not recreate a session; they
authenticate again through a provider.

### Step 13: Verify every path

Test startup, first/returning login for both providers, account linking, email-validation failures, protected route
access, logout, session revocation, and role/permission rules. The source tree currently contains only a Spring
context-load test; a new project should add focused unit tests and web/security integration tests for all behavior
described in this guide.

## 4. Runtime flow reference

### Google OIDC flow

```text
1. Browser requests /oauth2/authorization/google.
2. Spring Security saves the authorization request/state and redirects to Google.
3. User authenticates and consents at Google.
4. Google redirects to /login/oauth2/code/google with code and state.
5. Spring Security validates state, exchanges the code, validates OIDC data via Google's issuer, and loads the OIDC user.
6. The Google user service converts claims to a shared external identity.
7. The shared service finds or creates/links the local user and loads local authorities.
8. The application OIDC principal becomes the SecurityContext authentication.
9. Spring Session writes the security context to Redis and sends SESSION to the browser.
10. The browser is redirected to {FRONTEND_URL}/auth/callback.
```

### GitHub OAuth2 flow

```text
1. Browser requests /oauth2/authorization/github.
2. Spring Security saves the authorization request/state and redirects to GitHub.
3. User authenticates and consents at GitHub.
4. GitHub redirects to /login/oauth2/code/github with code and state.
5. Spring Security validates state, exchanges the code, and loads GitHub user attributes.
6. The GitHub user service requests /user/emails with the provider access token.
7. It selects a primary, verified email and constructs the shared external identity.
8. The shared service finds or creates/links the local user and loads local authorities.
9. The application OAuth2 principal becomes the SecurityContext authentication.
10. Spring Session writes the security context to Redis, sends SESSION, and redirects to {FRONTEND_URL}/auth/callback.
```

For either flow, an unsupported provider, invalid callback/state, token/user-info error, missing required identity data,
unverified email, inactive user, or account-linking conflict fails authentication and redirects to
`{FRONTEND_URL}/auth/error`.

## 5. File inventory and implementation order

The following inventory covers every source, configuration, test, build, and documentation file in `auth-service`.
**Core** files are required for this implementation. **Supporting** files are part of the complete service but can be
adapted. **Optional** files implement administration, diagnostics, or documentation beyond the login path.

### Project, build, and configuration

| File                                                                 | Class                       | Classification | Responsibility                                                                                                                   |
|----------------------------------------------------------------------|-----------------------------|----------------|----------------------------------------------------------------------------------------------------------------------------------|
| `pom.xml`                                                            | Maven build                 | Core           | Declares Java 25 and all web, OAuth2 client, JPA, Redis Session, validation, observability, and test dependencies.               |
| `src/main/resources/application.yaml`                                | Runtime configuration       | Core           | Supplies server, cookie, data-store, session, Google/GitHub client, frontend, bootstrap-admin, GitHub API, and logging settings. |
| `src/main/java/com/kavinda/auth_service/AuthServiceApplication.java` | Application entry point     | Supporting     | Starts component scanning and the Spring Boot application.                                                                       |
| `src/main/java/.../config/SecurityConfig.java`                       | Security filter chain       | Core           | Defines CORS, public/protected routes, OAuth2 login, custom user services, redirects, logout, and 401/403 handling.              |
| `src/main/java/.../config/OAuthClientConfig.java`                    | Provider-service beans      | Core           | Exposes Spring's default OIDC and OAuth2 user services for delegation.                                                           |
| `src/main/java/.../config/RedisSessionConfig.java`                   | Redis session configuration | Core           | Enables indexed Redis HTTP sessions with the application namespace and save behavior.                                            |
| `src/main/java/.../config/RestClientConfig.java`                     | HTTP-client bean            | Core           | Provides the REST client builder used for GitHub's email API.                                                                    |
| `src/main/java/.../config/RoleHierarchyConfig.java`                  | Role hierarchy              | Supporting     | Makes higher roles inherit lower-role access.                                                                                    |
| `src/main/java/.../config/AuthorizationDataInitializer.java`         | Startup runner              | Supporting     | Runs role/permission initialization when the application starts.                                                                 |

### Identity, persistence, and repositories

| File                                                        | Class                  | Classification | Responsibility                                                                                                         |
|-------------------------------------------------------------|------------------------|----------------|------------------------------------------------------------------------------------------------------------------------|
| `src/main/java/.../entity/AppUser.java`                     | Canonical user entity  | Core           | Stores the local account, canonical profile, status, timestamps, optimistic version, and roles.                        |
| `src/main/java/.../entity/OAuthAccount.java`                | Linked provider entity | Core           | Stores a provider subject and provider-specific metadata; enforces provider identity and per-user/provider uniqueness. |
| `src/main/java/.../entity/OAuthProvider.java`               | Provider enum          | Core           | Defines `GOOGLE` and `GITHUB` consistently across the persistence and security layers.                                 |
| `src/main/java/.../entity/UserStatus.java`                  | Status enum            | Core           | Defines whether a local user can authenticate.                                                                         |
| `src/main/java/.../entity/Role.java`                        | Role entity            | Supporting     | Owns role metadata and the role-permission relation.                                                                   |
| `src/main/java/.../entity/Permission.java`                  | Permission entity      | Supporting     | Stores individual application permission definitions.                                                                  |
| `src/main/java/.../entity/RoleName.java`                    | Role enum              | Supporting     | Defines the local role vocabulary.                                                                                     |
| `src/main/java/.../entity/PermissionName.java`              | Permission enum        | Supporting     | Defines permission authority strings.                                                                                  |
| `src/main/java/.../repository/IAppUserRepository.java`      | User repository        | Core           | Finds normalized email and eagerly loads a user with roles and permissions.                                            |
| `src/main/java/.../repository/IOAuthAccountRepository.java` | Provider repository    | Core           | Finds provider identities with their user authorities and detects duplicate user/provider links.                       |
| `src/main/java/.../repository/IRoleRepository.java`         | Role repository        | Supporting     | Finds roles with permissions for bootstrap and role management.                                                        |
| `src/main/java/.../repository/IPermissionRepository.java`   | Permission repository  | Supporting     | Finds permissions for bootstrap.                                                                                       |
| `src/main/java/.../utils/EmailNormalizer.java`              | Email utility          | Core           | Trims and lower-cases email using a locale-stable rule for matching/linking.                                           |

### Provider processing and local principals

| File                                                              | Class                     | Classification | Responsibility                                                                                                                                    |
|-------------------------------------------------------------------|---------------------------|----------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `src/main/java/.../service/auth/CustomOidcUserService.java`       | Google OIDC loader        | Core           | Delegates OIDC loading, accepts Google only, builds a Google external identity, authenticates locally, and returns an OIDC application principal. |
| `src/main/java/.../service/auth/CustomOAuth2UserService.java`     | GitHub OAuth2 loader      | Core           | Delegates OAuth2 user loading, accepts GitHub only, retrieves verified email, authenticates locally, and returns an OAuth2 application principal. |
| `src/main/java/.../service/auth/SocialAuthenticationService.java` | Shared identity service   | Core           | Validates, creates, links, updates, and reloads provider/local identity and local authorities transactionally.                                    |
| `src/main/java/.../security/GitHubEmailClient.java`               | GitHub emails client      | Core           | Calls GitHub's email endpoint and enforces primary, verified email selection.                                                                     |
| `src/main/java/.../security/GitHubEmailResponse.java`             | GitHub email response     | Core           | Represents the email fields used by the GitHub emails client.                                                                                     |
| `src/main/java/.../security/ExternalIdentity.java`                | Provider-neutral identity | Core           | Normalizes Google/GitHub identity data before local persistence.                                                                                  |
| `src/main/java/.../security/AppPrincipal.java`                    | Local principal contract  | Core           | Exposes local user ID/profile/provider/authorities and authority lookup.                                                                          |
| `src/main/java/.../security/AppOidcPrincipal.java`                | OIDC local principal      | Core           | Preserves OIDC token/claims while naming the authentication with the local UUID.                                                                  |
| `src/main/java/.../security/AppOAuth2Principal.java`              | OAuth2 local principal    | Core           | Preserves OAuth2 attributes while naming the authentication with the local UUID.                                                                  |
| `src/main/java/.../mappers/UserAuthorityMapper.java`              | Authority mapper          | Core           | Converts local roles and permissions into Spring Security authorities.                                                                            |

### Session, application APIs, and authorization support

| File                                                                  | Class                         | Classification | Responsibility                                                        |
|-----------------------------------------------------------------------|-------------------------------|----------------|-----------------------------------------------------------------------|
| `src/main/java/.../service/UserSessionService.java`                   | Session revocation service    | Supporting     | Deletes all Redis sessions indexed by local user UUID.                |
| `src/main/java/.../service/UserProfileService.java`                   | Profile service               | Supporting     | Loads the authenticated user's local profile, roles, and permissions. |
| `src/main/java/.../controllers/AuthController.java`                   | Auth API                      | Supporting     | Exposes public status and protected current-profile routes.           |
| `src/main/java/.../dtos/UserProfileResponse.java`                     | Profile response              | Supporting     | Defines the current-user API response.                                |
| `src/main/java/.../service/auth/AuthorizationBootstrapService.java`   | Authorization bootstrap       | Supporting     | Idempotently creates permissions and configures roles at startup.     |
| `src/main/java/.../filters/OAuth2Filter.java`                         | Authenticated-response filter | Supporting     | Adds no-cache response headers when a request is authenticated.       |
| `src/main/java/.../exceptions/ErrorResponse.java`                     | Error payload                 | Supporting     | Defines the service error-response shape.                             |
| `src/main/java/.../exceptions/GlobalExceptionHandler.java`            | Exception advice              | Supporting     | Maps local domain and validation errors to HTTP error responses.      |
| `src/main/java/.../exceptions/types/ResourceNotFoundException.java`   | Domain exception              | Supporting     | Signals a missing local resource.                                     |
| `src/main/java/.../exceptions/types/ResourceConflictException.java`   | Domain exception              | Supporting     | Signals duplicate/conflicting local state.                            |
| `src/main/java/.../exceptions/types/ForbiddenOperationException.java` | Domain exception              | Supporting     | Signals a prohibited local action.                                    |

### Optional administration and diagnostics

| File                                                              | Class                  | Classification | Responsibility                                                                                                          |
|-------------------------------------------------------------------|------------------------|----------------|-------------------------------------------------------------------------------------------------------------------------|
| `src/main/java/.../service/UserAdministrationService.java`        | User status management | Optional       | Suspends/activates users, guards administrator boundaries, and revokes sessions on suspension.                          |
| `src/main/java/.../service/UserRoleService.java`                  | Role management        | Optional       | Assigns/removes roles while protecting super-admin and no-role invariants.                                              |
| `src/main/java/.../controllers/UserAdministrationController.java` | Status-management API  | Optional       | Exposes administrator suspension/activation routes.                                                                     |
| `src/main/java/.../controllers/UserRoleController.java`           | Role-management API    | Optional       | Exposes role assignment/removal routes.                                                                                 |
| `src/main/java/.../dtos/UserStatusUpdateResponse.java`            | Status response        | Optional       | Returns user status and revoked-session count.                                                                          |
| `src/main/java/.../dtos/AssignRoleRequest.java`                   | Role request           | Optional       | Validates role assignment input.                                                                                        |
| `src/main/java/.../controllers/HomeController.java`               | Home/debug API         | Optional       | Provides endpoint discovery, protected examples, and principal diagnostics. Remove or secure diagnostics in production. |

### Tests, service documentation, and local tooling

| File                                                                      | Classification     | Responsibility                                                                                                                     |
|---------------------------------------------------------------------------|--------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `src/test/java/com/kavinda/auth_service/AuthServiceApplicationTests.java` | Supporting         | Verifies that the Spring application context starts; extend it with focused OAuth2, persistence, session, and authorization tests. |
| `README.md`                                                               | Supporting         | Documents how to configure, run, operate, and consume this completed service.                                                      |
| `OAUTH2_FLOW.md`                                                          | Core documentation | This implementation blueprint.                                                                                                     |
| `mvnw` and `mvnw.cmd`                                                     | Supporting         | Maven Wrapper launchers for repeatable builds without a separately installed Maven.                                                |

## 6. API and session contract

The source service exposes these relevant routes. A new project may rename domain APIs but should retain the OAuth
authorization and callback conventions unless it also changes Spring Security configuration and provider-console
callback registration.

| Method  | Path                                    | Access            | Role in the flow                                              |
|---------|-----------------------------------------|-------------------|---------------------------------------------------------------|
| `GET`   | `/oauth2/authorization/google`          | Public            | Starts Google authorization.                                  |
| `GET`   | `/oauth2/authorization/github`          | Public            | Starts GitHub authorization.                                  |
| `GET`   | `/login/oauth2/code/google`             | Provider callback | Spring Security processes Google callback.                    |
| `GET`   | `/login/oauth2/code/github`             | Provider callback | Spring Security processes GitHub callback.                    |
| `GET`   | `/api/v1/auth/status`                   | Public            | Reports whether the request has an authenticated session.     |
| `GET`   | `/api/v1/auth/me`                       | `profile:read`    | Returns canonical local profile and authorization data.       |
| `POST`  | `/logout`                               | Session-based     | Invalidates session and deletes the session cookie.           |
| `PATCH` | `/api/v1/admin/users/{userId}/suspend`  | Administrator     | Optional: persists suspension and revokes all sessions.       |
| `PATCH` | `/api/v1/admin/users/{userId}/activate` | Administrator     | Optional: restores active status without restoring a session. |

The frontend makes protected requests with browser credentials enabled. The service redirects successful login to
`{FRONTEND_URL}/auth/callback` and login failure to `{FRONTEND_URL}/auth/error`.

## 7. Acceptance checklist

Use this checklist before treating an implementation as complete.

- Application startup fails clearly when required provider secrets or infrastructure are unavailable.
- Google first login creates a local user, Google provider account, initial roles, and a Redis session.
- A returning Google login finds the provider subject and updates only provider metadata/login timestamps.
- GitHub first login succeeds when its emails API returns a primary, verified email, including when the profile email is
  private.
- GitHub login fails safely when the emails API fails, has no email, or has no primary verified email.
- A new Google or GitHub identity with the same verified normalized email links to the existing local user, without
  replacing canonical profile fields.
- Duplicate `(provider, provider_subject)` and duplicate `(user, provider)` links are impossible.
- Missing subject/email, unverified email, unsupported provider, suspended user, and disabled user fail authentication.
- A logged-in browser reaches protected profile routes through the opaque session cookie; a browser without it receives
  `401`.
- Logout removes the current session and session cookie.
- Suspending a user removes all Redis sessions for their local UUID; reactivation requires a new OAuth2 login.
- Role/permission mapping produces the required authorities and protects administration routes.
- Production review covers HTTPS, secure cookies, exact redirect URIs, restricted CORS origin, CSRF strategy, migration
  strategy, and secret/token logging.

## 8. What to change for another project

Change domain-specific names, local user profile fields, roles, permissions, API routes, frontend destination, Redis
namespace, and operational settings. Keep the two provider-specific user services separate, keep the shared
account-linking service provider-neutral, and preserve local UUID-based session indexing. That separation is what allows
new providers to be added later without duplicating persistence and authorization rules.
