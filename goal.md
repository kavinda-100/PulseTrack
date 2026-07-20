## Google Auth flow

```text
Google OIDC
    |
    v
CustomOidcUserService
    |
    v
ExternalIdentity
    |
    v
SocialAuthenticationService
    |
    ├── Existing GOOGLE + subject?
    │      └── Login linked AppUser
    │
    ├── New Google account, existing verified email?
    │      └── Add GOOGLE provider to existing AppUser
    │
    └── New provider and new email?
           └── Create AppUser + GOOGLE OAuthAccount
```