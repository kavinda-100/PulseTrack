# PulseTrack Authentication Service

The PulseTrack Authentication Service is the shared identity, authorization, and session service for PulseTrack and
future projects. It authenticates users through Google OpenID Connect (OIDC) and GitHub OAuth2, stores the application
account and authorization data in PostgreSQL, and gives the browser an opaque Redis-backed session cookie.

It does not implement local registration, passwords, password resets, or JWT-based application access.

## What this service owns

- Google OIDC and GitHub OAuth2 browser login.
- Provider-account linking to a local PulseTrack user.
- Roles, permissions, and administrative user management.
- Redis-backed authenticated browser sessions and logout.
- Session revocation when an administrator suspends a user.

PulseTrack's API Gateway should validate these sessions for dashboard-facing routes and pass only trusted user identity
to internal services. Project API keys belong to the event-ingestion path, not this service.

## Architecture

```text
Browser
  |
  | GET /oauth2/authorization/google or /oauth2/authorization/github
  v
Auth Service ---- authorization-code flow ----> Google / GitHub
  |                                                |
  | <----------- callback with authorization code -+
  |
  +--> PostgreSQL: app_users, oauth_accounts, roles, permissions
  +--> Redis: Spring Session data
  |
  +--> SESSION cookie + redirect to the frontend
```

## Prerequisites

- Java 25.
- Docker and Docker Compose.
- A Google OAuth client and a GitHub OAuth app for local development.

From the repository root, start PostgreSQL and Redis:

```bash
docker compose up -d
```

The local dependency ports are PostgreSQL `5433`, Redis `6379`, Adminer `9000`, and Redis Insight `5540`.

## Configure OAuth providers

Create a **Web application** OAuth client in Google Cloud and a GitHub OAuth App. Configure these exact
local-development callback URLs:

| Provider | Callback URL                                     |
|----------|--------------------------------------------------|
| Google   | `http://localhost:8001/login/oauth2/code/google` |
| GitHub   | `http://localhost:8001/login/oauth2/code/github` |

Google must be allowed to return the `openid`, `profile`, and `email` scopes. GitHub must be allowed to return
`read:user` and `user:email`; the latter lets the service request `/user/emails` when a user's email is private.

For a deployed environment, register its HTTPS callback URLs with both providers, set `SESSION_COOKIE_SECURE=true`, and
update the application's redirect URI configuration before deployment. The current application configuration contains
local callback URLs, so deployment redirect URIs are an explicit configuration/code-review concern.

## Configuration

The service reads configuration from environment variables. Never put provider secrets in source control.

```bash
export GOOGLE_CLIENT_ID='your-google-client-id'
export GOOGLE_CLIENT_SECRET='your-google-client-secret'
export GITHUB_CLIENT_ID='your-github-client-id'
export GITHUB_CLIENT_SECRET='your-github-client-secret'

# Optional local defaults are shown for clarity.
export FRONTEND_URL='http://localhost:3000'
export BOOTSTRAP_SUPER_ADMIN_EMAIL='admin@example.com'
export SESSION_COOKIE_SECURE='false'
```

Useful optional settings include `SERVER_PORT` (default `8001`), `DATABASE_URL`, `DATABASE_USERNAME`,
`DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_DATABASE`, `SESSION_TIMEOUT` (default `1d`), and
`GITHUB_API_VERSION`.

`BOOTSTRAP_SUPER_ADMIN_EMAIL` is normalized (trimmed and lower-cased) before comparison. On that person's first
successful login, the service grants both `USER` and `SUPER_ADMIN`.

## Run the service

```bash
cd auth-service
./mvnw spring-boot:run
```

The service starts at `http://localhost:8001`. Open `/` for a small endpoint directory, then begin login at either:

```text
http://localhost:8001/oauth2/authorization/google
http://localhost:8001/oauth2/authorization/github
```

Run the test suite with:

```bash
cd auth-service
./mvnw test
```

## OAuth2 and OIDC flow

Both providers use the OAuth 2.0 authorization-code flow. Google additionally provides an OpenID Connect ID token, so
its flow verifies identity through OIDC claims.

```text
1. Browser -> Auth Service
   GET /oauth2/authorization/{provider}

2. Auth Service -> Provider
   Spring Security stores the authorization request/state in the browser session
   and redirects the browser to Google or GitHub.

3. User -> Provider
   The user authenticates and grants the configured scopes.

4. Provider -> Auth Service
   The provider redirects to /login/oauth2/code/{provider} with a code and state.

5. Auth Service -> Provider
   Spring Security validates state, exchanges the code for tokens, and loads
   the provider user data.

6. Auth Service -> PostgreSQL
   The service validates and links the external identity, then loads local
   roles and permissions.

7. Auth Service -> Redis / Browser
   Spring Session persists the authenticated security context in Redis, the
   browser receives SESSION, and the response redirects to /auth/callback.
```

### Google OIDC processing

1. Spring Security validates Google's OIDC response using the configured issuer, `https://accounts.google.com`.
2. `CustomOidcUserService` reads the ID-token/user-info claims.
3. The Google `sub` claim becomes the stable provider subject. Email, verified-email status, name, preferred username,
   and picture are profile data.
4. The service rejects a missing subject/email or an unverified email.
5. It converts the local user into an `AppOidcPrincipal` containing the local user UUID, local profile data, provider
   type, OIDC claims, roles, and permissions.

### GitHub OAuth2 processing

1. Spring Security loads GitHub's OAuth2 user attributes.
2. `CustomOAuth2UserService` requires GitHub's numeric `id` and `login`; `name` and `avatar_url` are optional.
3. Because GitHub may not expose an email in the profile response, the service calls`https://api.github.com/user/emails`
   with the provider access token.
4. It accepts only a nonblank **primary, verified** GitHub email. Missing email data, an API failure, or an
   unverified/non-primary email fails login.
5. It converts the local user into an `AppOAuth2Principal` containing the local user UUID, local profile data, provider
   type, GitHub attributes, roles, and permissions.

### Local account creation and linking

The provider subject—not email—is the permanent identity key:

1. The service first looks up `oauth_accounts` by `(provider, provider_subject)`.
2. When found, it checks the local user is active, refreshes provider-specific metadata and login timestamps, and
   retains the canonical PulseTrack profile.
3. When not found, it normalizes the verified provider email and finds an existing `app_users` row with that email. If
   found, it links the new provider account to that user; otherwise it creates a new user.
4. Every new user receives `USER`; the configured bootstrap email also receives `SUPER_ADMIN`.
5. The service creates one `oauth_accounts` row for the provider. Each provider subject is globally unique and each
   local user may have only one account for a given provider.

Provider-specific email, username, display name, and avatar remain on `oauth_accounts`. They never silently replace the
canonical `app_users` email, display name, or avatar. A `SUSPENDED` or non-active user cannot log in.

## Sessions and browser integration

After login, Spring Session saves the authenticated security context in Redis under the `pulsetrack:auth:sessions`
namespace. The principal name is the local user UUID, which allows all of that user's sessions to be found and revoked.

The browser receives an opaque `SESSION` cookie. Its current defaults are:

| Setting  | Value                                                  |
|----------|--------------------------------------------------------|
| Lifetime | `1d`                                                   |
| Path     | `/`                                                    |
| HttpOnly | `true`                                                 |
| SameSite | `Lax`                                                  |
| Secure   | `false` locally; controlled by `SESSION_COOKIE_SECURE` |

The frontend must make authenticated cross-origin requests with credentials enabled. For example:

```js
fetch('http://localhost:8001/api/v1/auth/me', {
  credentials: 'include'
});
```

Only `FRONTEND_URL` is allowed by CORS, and credentialed CORS is enabled. A successful login redirects to
`${FRONTEND_URL}/auth/callback`; an OAuth failure redirects to `${FRONTEND_URL}/auth/error`.

`POST /logout` invalidates the current HTTP/Redis session, clears the security context, deletes `SESSION`, and redirects
to `FRONTEND_URL`. Suspending a user revokes every Redis session indexed under that user's UUID; activating a user does
not restore a session, so they must log in again.

## API reference

Unauthenticated requests to protected routes receive `401`; insufficient authorities receive `403`.

| Method   | Path                                                   | Access                             | Purpose                                                        |
|----------|--------------------------------------------------------|------------------------------------|----------------------------------------------------------------|
| `GET`    | `/`                                                    | Public                             | Service and endpoint directory                                 |
| `GET`    | `/oauth2/authorization/google`                         | Public                             | Begin Google login                                             |
| `GET`    | `/oauth2/authorization/github`                         | Public                             | Begin GitHub login                                             |
| `GET`    | `/login/oauth2/code/{provider}`                        | Public callback                    | Provider redirect handled by Spring Security                   |
| `GET`    | `/api/v1/auth/status`                                  | Public                             | Return `authenticated` or `unauthenticated`                    |
| `GET`    | `/api/v1/auth/me`                                      | `profile:read`                     | Return the authenticated local profile                         |
| `POST`   | `/logout`                                              | Authenticated session              | End the current session                                        |
| `GET`    | `/debug/principal`                                     | Authenticated                      | Inspect local principal and authorities; development/debug use |
| `GET`    | `/admin`                                               | `ROLE_ADMIN`                       | Example admin-protected operation                              |
| `GET`    | `/super-admin`                                         | `ROLE_SUPER_ADMIN`                 | Example super-admin-protected operation                        |
| `POST`   | `/api/v1/admin/users/{userId}/assign-roles`            | `role:assign`                      | Assign a role                                                  |
| `DELETE` | `/api/v1/admin/users/{userId}/remove-roles/{roleName}` | `role:remove`                      | Remove a role                                                  |
| `PATCH`  | `/api/v1/admin/users/{userId}/suspend`                 | `ROLE_ADMIN` or `ROLE_SUPER_ADMIN` | Suspend user and revoke sessions                               |
| `PATCH`  | `/api/v1/admin/users/{userId}/activate`                | `ROLE_ADMIN` or `ROLE_SUPER_ADMIN` | Reactivate user                                                |

### Example responses

`GET /api/v1/auth/status` without a session:

```json
{
  "status": "unauthenticated"
}
```

`GET /api/v1/auth/me` returns a local profile:

```json
{
  "id": "b7e4bcde-2d3c-4df0-a27e-c9898651a6ab",
  "email": "user@example.com",
  "displayName": "Pulse User",
  "avatarUrl": "https://example.com/avatar.png",
  "emailVerified": true,
  "status": "ACTIVE",
  "roles": ["USER"],
  "permissions": ["profile:read", "project:create"],
  "lastLoginAt": "2026-07-20T10:00:00Z",
  "createdAt": "2026-07-20T09:00:00Z",
  "updatedAt": "2026-07-20T10:00:00Z"
}
```

Assign a role with:

```http
POST /api/v1/admin/users/{userId}/assign-roles
Content-Type: application/json

{ "role": "ADMIN" }
```

## Authorization model

Roles are initialized at the application startup. The hierarchy is:

```text
SUPER_ADMIN > ADMIN > USER
```

The service maps every assigned role to `ROLE_<ROLE_NAME>` and every role permission to its authority string. `USER` has
profile, project, and analytics permissions; `ADMIN` adds user-management read/update/disable and role-read permissions;
`SUPER_ADMIN` receives every defined permission, including `role:assign` and `role:remove`.

Only a super administrator can assign or remove `ADMIN` and `SUPER_ADMIN`. A user cannot remove their own `SUPER_ADMIN`
role, and every user must retain at least one role. An administrator cannot suspend or activate a super administrator,
and nobody can suspend themselves.

## Persistence model

- `app_users` stores the canonical PulseTrack account, status, canonical profile, timestamps, and roles.
- `oauth_accounts` stores provider-specific identities and metadata. Its unique keys protect
  `(provider, provider_subject)` and `(user, provider)`.
- `roles`, `permissions`, `user_roles`, and `role_permissions` implement the local RBAC model.
- Redis stores session state, not a JWT. PostgreSQL owns durable account and authorization data.

Hibernate currently uses `ddl-auto: update`, which is convenient for development but should be replaced by reviewed
database migrations before production use.

## Troubleshooting and production notes

| Symptom                                      | Check                                                                                                         |
|----------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| Provider shows a redirect URI error          | The provider console URL exactly matches the configured callback URL, including scheme, host, port, and path. |
| GitHub login fails after consent             | Ensure `user:email` is granted and the account has a primary, verified email.                                 |
| Login returns to frontend but `/me` is `401` | Use `credentials: 'include'`, verify `FRONTEND_URL`, cookie settings, and Redis availability.                 |
| The intended first admin is only a user      | Set `BOOTSTRAP_SUPER_ADMIN_EMAIL` before their first successful login.                                        |
| A suspended user still appears logged in     | Confirm Redis is reachable; suspension revokes all sessions indexed for that local user.                      |
| Service cannot start                         | Ensure all four OAuth client variables are set and PostgreSQL/Redis are running.                              |

Before exposing the service publicly, use HTTPS, set secure cookies, use production provider redirect URIs, restrict
`FRONTEND_URL` to the real frontend, and review the current CSRF configuration. CSRF is disabled in the implemented
security filter chain, which may not be appropriate for a cookie-authenticated production deployment. Do not log client
secrets, access tokens, cookies, or complete provider identity payloads.

Stop local infrastructure when finished:

```bash
docker compose down
```

Use `docker compose down -v` only when intentionally deleting local PostgreSQL and Redis data.
