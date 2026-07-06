# Runbook: Google OAuth Provider Unavailable

How to diagnose and resolve Google sign-in failures in production and UAT.

---

## Symptoms

1. **User sees `?error=provider_unavailable`** on the login page redirect
   (`https://portfolio.nanobyte.ca/login?error=provider_unavailable`).
   This indicates an *unexpected exception* during the callback — typically an HTTP-level
   failure reaching Google's token/userinfo endpoints, or a misconfigured redirect URI
   causing Google to reject the callback.

2. **User sees `?error=auth_failed`** on the login page redirect
   (`https://portfolio.nanobyte.ca/login?error=auth_failed`).
   This indicates a *GoogleOAuthException* — an invalid/missing state token, expired state,
   or missing/invalid authorization `code` parameter from Google.

3. **Google redirects back with `?error=...`** in the URL before the callback handler
   runs. This occurs when Google itself rejects the authentication flow (e.g., misconfigured
   client, untrusted redirect URI).

---

## Verify Credentials

The app reads `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` at startup. If either
is missing or wrong, the OAuth flow will fail.

### On the Prod Host

SSH to the production server and inspect the environment file:

```bash
ssh deploy@ssh.nanobyte.ca
grep GOOGLE_ /opt/portfolio/prod/.env
```

Expected output:
```
GOOGLE_CLIENT_ID=xxxxxxxxxxxx.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-xxxxxxxxxxxxxxxxxxxx
```

If either is missing, add it and restart:

```bash
docker compose -f /opt/portfolio/prod/docker-compose.yml up -d
```

### In HashiCorp Vault

The canonical source of truth is Vault at `secret/data/portfolio/prod` (KV v2).
The deploy workflow fetches these values and writes them to the `.env` file.

Access Vault at `https://vault.nanobyte.ca` and navigate to
`secret/portfolio/prod`. Verify the following keys are present:

- `GOOGLE_CLIENT_ID` — the OAuth 2.0 client ID from Google Cloud Console
- `GOOGLE_CLIENT_SECRET` — the OAuth 2.0 client secret from Google Cloud Console

If values are missing or need updating, edit the secret in Vault and re-deploy
(see [configurations.md](../reference/configurations.md) for the full Vault key
reference and deploy workflow).

### Rotating Credentials

1. Generate new credentials in Google Cloud Console → APIs & Services → Credentials.
2. Update the values in Vault (`secret/portfolio/prod` and `secret/portfolio/uat`
   as needed).
3. Re-run the deploy workflow (`gh workflow run deploy.yml`) with the target
   environment and latest image tag.
4. Verify Google sign-in works on the deployed environment.

---

## Verify Redirect URI

### Background

The redirect URI is **not configurable via environment variable** — it is
dynamically constructed as:

```
<CORS_ALLOWED_ORIGINS first entry> + "/auth/google/callback"
```

This means:
- **Prod:** `https://portfolio.nanobyte.ca/auth/google/callback`
- **UAT:** `https://uatportfolio.nanobyte.ca/auth/google/callback`

The nginx container in production (running inside the frontend Docker container
at `frontend/nginx.conf`) proxies `/auth/` to the backend:

```nginx
location /auth/ {
    proxy_pass http://backend:8080;
    ...
}
```

### Google Cloud Console Verification

1. Go to [Google Cloud Console](https://console.cloud.google.com) → APIs & Services → Credentials.
2. Find the OAuth 2.0 Client ID matching your `GOOGLE_CLIENT_ID`.
3. Under **Authorized redirect URIs**, verify the following entries exist **exactly**:

| Environment | Redirect URI |
|-------------|-------------|
| Production  | `https://portfolio.nanobyte.ca/auth/google/callback` |
| UAT         | `https://uatportfolio.nanobyte.ca/auth/google/callback` |

4. If any URI is missing, add it and click **Save**. Changes take effect immediately.

> **Note:** The OAuth redirect URI in Google Cloud Console must match the
> dynamically constructed URI exactly — trailing slashes, protocol (`https`),
> and hostname all matter. A mismatch causes Google to reject the callback
> with an `redirect_uri_mismatch` error. See issue #11 for the prior fix
> that introduced the derived-redirect-URI approach.

---

## Log Marker Reference

The following log markers (introduced by #57/#58) help operators triage callback
failures in Loki or Docker logs.

### `AUTH_CALLBACK_UNEXPECTED`

**Appears when:** An unanticipated exception (not a `GoogleOAuthException`) is
thrown during the Google callback handler in `AuthController.googleCallback()`.

**Log level:** `ERROR`

**Example:**
```
ERROR Unexpected error during Google OAuth callback
```

**Cause categories:**
- Network timeout or DNS failure reaching Google's token/userinfo endpoints
  (`oauth2.googleapis.com`, `openidconnect.googleapis.com`)
- WebClient configuration error (proxy, SSL, etc.)
- Database connection error while looking up the user or state
- Null/unexpected response from Google's APIs

**Triage:**
1. Check Loki for the full stack trace of the `Exception`.
2. Verify network connectivity from the backend container:
   ```bash
   docker compose -f /opt/portfolio/prod/docker-compose.yml exec prod-backend \
     curl -v https://oauth2.googleapis.com/token
   ```
3. Check that `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are set (see
   [Verify Credentials](#verify-credentials) above).

### `AUTH_CALLBACK_GOOGLE_HTTP`

**Appears when:** The WebClient HTTP request to Google's token or userinfo
endpoint returns a non-2xx response (4xx/5xx).

**Log level:** `WARN`

**Example:**
```
WARN Google OAuth HTTP error during token exchange: HTTP 401 Unauthorized
```

**Cause categories:**
- Invalid `GOOGLE_CLIENT_ID` or `GOOGLE_CLIENT_SECRET` → Google returns 401
- Redirect URI mismatch → Google returns 400 `redirect_uri_mismatch`
- Expired or already-used authorization code → Google returns 400 `invalid_grant`

**Triage:**
1. Check the HTTP status and body in the log message to identify the specific
   Google error.
2. Verify credentials and redirect URIs (see sections above).
3. If `invalid_grant`, the user may need to re-initiate login — the auth code
   is single-use and expires quickly.

### Additional AuthController Logs

| Log Marker | Level | Meaning |
|------------|-------|---------|
| `Google OAuth error: {error}` | WARN | Google returned `?error=...` in the callback URL (e.g., `access_denied`). |
| `Google OAuth callback failed: {message}` | ERROR | `GoogleOAuthException` thrown (invalid/expired state, bad code). User sees `?error=auth_failed`. |

### Triage Decision Tree

```
User reports Google sign-in failure
  │
  ├─ URL contains ?error=provider_unavailable
  │    └─ Check Loki for AUTH_CALLBACK_UNEXPECTED
  │       ├─ Stack trace shows network error?
  │       │    └─ Verify backend → Google connectivity
  │       └─ Stack trace shows DB error?
  │            └─ Check PostgreSQL health
  │
  ├─ URL contains ?error=auth_failed
  │    └─ Check Loki for "Google OAuth callback failed" or
  │       AUTH_CALLBACK_GOOGLE_HTTP
  │       ├─ HTTP 401? → Verify GOOGLE_CLIENT_ID/SECRET
  │       ├─ HTTP 400 redirect_uri_mismatch? → Verify Google Console URIs
  │       └─ HTTP 400 invalid_grant? → User should retry login
  │
  └─ Google never redirects back (browser shows Google error)
       └─ Verify redirect URI in Google Cloud Console
          matches https://portfolio.nanobyte.ca/auth/google/callback
```

---

## UAT Repro

Use UAT to confirm the error surfacing works correctly before deploying credential
or redirect URI changes to production.

### Prerequisites

- UAT environment is running at `https://uatportfolio.nanobyte.ca`
- UAT has its own Google Cloud Console OAuth client (or shares the prod client
  with the UAT redirect URI authorized)

### Repro: Wrong Redirect URI

1. In Vault, temporarily change `GOOGLE_REDIRECT_URI` (or `CORS_ALLOWED_ORIGINS`)
   to a deliberately wrong value, e.g., `http://localhost:3000`. Since the redirect
   URI is derived from `CORS_ALLOWED_ORIGINS` + `/auth/google/callback`, setting
   `CORS_ALLOWED_ORIGINS` to a value that doesn't match the authorized redirect
   URIs in Google Cloud Console will trigger the error.

2. Re-deploy UAT with the wrong value:
   ```bash
   gh workflow run deploy.yml -f environment=uat -f tag=main-<sha>
   ```

3. Navigate to `https://uatportfolio.nanobyte.ca/login` and click "Sign in with Google".

4. **Expected result:** Google redirects back with `?error=auth_failed` or the
   browser shows a Google error page. Check Loki for `AUTH_CALLBACK_GOOGLE_HTTP`
   indicating a `redirect_uri_mismatch`.

5. **Revert:** Restore the correct `CORS_ALLOWED_ORIGINS` value in Vault and
   re-deploy UAT.

### Verification Check

After restoring:
1. Sign in with Google on UAT — should succeed.
2. Verify no new `provider_unavailable` or `auth_failed` errors in Loki.
3. Proceed with confidence to production credential/URI changes.

---

## Related Issues

- **#11** — Derived-redirect-URI fix: the redirect URI is now dynamically
  constructed from `CORS_ALLOWED_ORIGINS` rather than hardcoded, fixing
  environment-specific callback handling.
- **#39** — Parent triage issue identifying the underlying prod trigger as
  credential/redirect-URI misconfiguration.
- **#57** — Introduced `AUTH_CALLBACK_GOOGLE_HTTP` log marker for HTTP-level
  Google OAuth errors during token exchange.
- **#58** — Introduced `AUTH_CALLBACK_UNEXPECTED` log marker for unexpected
  exceptions during the OAuth callback handler.
