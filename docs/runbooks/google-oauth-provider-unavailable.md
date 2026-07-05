# Runbook: Google OAuth Sign-In Failures

> **Trigger**: Users see *"Google sign-in is temporarily unavailable"* (`?error=provider_unavailable`) on `/login`.

## Symptom

Clicking "Continue with Google" returns a redirect to `/login?error=provider_unavailable`. The banner reads "Google sign-in is temporarily unavailable."

**Before the #57/#58 fix** (pre-d476aac): `provider_unavailable` could be returned for _any_ non-`GoogleOAuthException` error, including legitimate Google API errors (e.g., `redirect_uri_mismatch`, `invalid_client`). The real Google error was masked.

**After the fix**: `provider_unavailable` is reserved for truly unexpected errors. Google API errors now produce `auth_failed` with a `google_error:<code>` log marker, making the actual failure discoverable in logs.

## Log Markers

| Marker | Meaning | Action |
|--------|---------|--------|
| `AUTH_CALLBACK_UNEXPECTED` | A generic, unexpected exception was caught in `googleCallback`. No Google API call was involved (or the error wasn't from Google's API). | Check the full stack trace in logs. Could be a network issue, serialization failure, or application bug. |
| `google_error:<code>` | Google's token/userinfo endpoint returned a structured error (e.g., `google_error:redirect_uri_mismatch`, `google_error:invalid_client`, `google_error:invalid_grant`). | See the Diagnosis section below for the specific code. |
| `AUTH_GOOGLE_CREDENTIALS_MISSING` | Emitted at **boot time** when `GOOGLE_CLIENT_ID` or `GOOGLE_CLIENT_SECRET` is blank and `APP_ENVIRONMENT` is not `local`. | Provision the credentials in Vault (see below). |

## Diagnosis

### 1. Check for boot-time credential warning

```bash
# On the prod/UAT host
docker compose logs portfolio-backend | grep AUTH_GOOGLE_CREDENTIALS_MISSING
```

If present, `GOOGLE_CLIENT_ID` and/or `GOOGLE_CLIENT_SECRET` are unset. Proceed to Step 2.

### 2. Verify Google credentials in Vault

Google OAuth credentials are stored in Vault at `secret/data/portfolio/<env>`.

```bash
# Check common secrets (includes shared infra tokens)
vault kv get secret/portfolio/common | grep GOOGLE

# Check environment-specific secrets
vault kv get secret/portfolio/prod | grep GOOGLE
vault kv get secret/portfolio/uat | grep GOOGLE
```

Ensure both `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are present and non-empty.

**Credential location**: `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` must be in the **environment-specific** path (e.g., `secret/data/portfolio/prod`), not in `secret/data/portfolio/common`.

### 3. Verify the authorized redirect URI in Google Cloud Console

The redirect URI sent in the OAuth flow must match exactly what is configured in the Google Cloud Console under **APIs & Services > Credentials > OAuth 2.0 Client IDs**.

The redirect URI used by the application is either:
- **Explicit**: `GOOGLE_REDIRECT_URI` env var (if set) — e.g., `https://portfolio.nanobyte.ca/auth/google/callback`
- **Derived** (fallback): `CORS_ALLOWED_ORIGINS[0] + "/auth/google/callback"` — e.g., `https://portfolio.nanobyte.ca/auth/google/callback`

To check which redirect URI is in use:
```bash
# On the UAT/prod host
docker compose exec portfolio-backend env | grep -E 'GOOGLE_REDIRECT_URI|CORS_ALLOWED_ORIGINS'
```

Then verify this URI is listed in Google Cloud Console:
1. Go to https://console.cloud.google.com/apis/credentials
2. Find "Web client" under OAuth 2.0 Client IDs
3. Under "Authorized redirect URIs", confirm the URI from above is present
4. If using multiple environments, add both prod and UAT URIs (e.g., `https://portfolio.nanobyte.ca/auth/google/callback` AND `https://uatportfolio.nanobyte.ca/auth/google/callback`)

### 4. Check Google error code from application logs

When `google_error:<code>` appears in logs, the `<code>` matches Google's OAuth error codes:

| Google Error Code | Meaning | Fix |
|-------------------|---------|-----|
| `invalid_client` | Client ID or secret is wrong/unset | Verify in Vault (Step 2) |
| `redirect_uri_mismatch` | Redirect URI doesn't match Google Cloud Console | Verify in Console (Step 3) |
| `invalid_grant` | The authorization code is invalid/expired/already used | User should retry login |
| `access_denied` | User denied the OAuth consent | Normal user action; no fix needed |

## Remediation Steps

### Unset credentials (AUTH_GOOGLE_CREDENTIALS_MISSING or google_error:invalid_client)

1. Obtain the Google OAuth 2.0 credentials from Google Cloud Console.
2. Add them to Vault:
   ```bash
   vault kv patch secret/portfolio/prod GOOGLE_CLIENT_ID="<client-id>" GOOGLE_CLIENT_SECRET="<client-secret>"
   ```
3. Re-deploy to pick up the new secrets.

### Redirect URI mismatch (google_error:redirect_uri_mismatch)

1. Verify the redirect URI the app is using (see Step 3 above).
2. Option A: Add the URI to Google Cloud Console (recommended).
3. Option B: Set `GOOGLE_REDIRECT_URI` to a value that matches what's in the Console:
   ```bash
   vault kv patch secret/portfolio/prod GOOGLE_REDIRECT_URI="https://portfolio.nanobyte.ca/auth/google/callback"
   ```

### Unexpected errors (AUTH_CALLBACK_UNEXPECTED)

1. Check the full stack trace in application logs.
2. Possible causes:
   - Network connectivity issues to `oauth2.googleapis.com` or `www.googleapis.com`
   - DNS resolution failures
   - TLS certificate issues
   - Internal application errors (NPE, serialization)
3. If the error is transient, it may self-resolve. If persistent, investigate infrastructure.

## Verification

After applying fixes:
1. Check boot logs for absence of `AUTH_GOOGLE_CREDENTIALS_MISSING` (unless local):
   ```bash
   docker compose logs portfolio-backend | grep AUTH_GOOGLE_CREDENTIALS_MISSING
   ```
2. Test the Google sign-in flow:
   ```bash
   curl -v "https://<frontend>/auth/google" 2>&1 | grep -i location
   # Should redirect to Google with proper client_id and redirect_uri
   ```
3. On a failed callback (invalid code/state), logs should contain `google_error:` — the error code from Google's API, not a generic `provider_unavailable`.

## Related

- [Infrastructure Reference](../reference/infrastructure.md)
- [Configurations Reference](../reference/configurations.md)
- Issue: [#39](https://github.com/Nanobyte-Canada/pc/issues/39)
