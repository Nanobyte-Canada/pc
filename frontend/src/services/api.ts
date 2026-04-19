export const API_URL = import.meta.env.VITE_API_URL || ''

/**
 * Get CSRF token from cookie (set by Spring Security's CookieCsrfTokenRepository)
 */
function getCsrfTokenFromCookie(): string | null {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

// Token refresh mutex to prevent concurrent refresh attempts
let isRefreshing = false;
let refreshPromise: Promise<boolean> | null = null;

async function attemptTokenRefresh(): Promise<boolean> {
  if (isRefreshing && refreshPromise) {
    return refreshPromise;
  }

  isRefreshing = true;
  refreshPromise = (async () => {
    try {
      const csrfToken = getCsrfTokenFromCookie();
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (csrfToken) {
        headers['X-XSRF-TOKEN'] = csrfToken;
      }

      const response = await fetch(`${API_URL}/auth/refresh`, {
        method: 'POST',
        headers,
        credentials: 'include',
      });

      return response.ok;
    } catch {
      return false;
    } finally {
      isRefreshing = false;
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    public detail: string,
    public title?: string
  ) {
    super(detail)
    this.name = 'ApiError'
  }
}

export async function parseErrorResponse(response: Response): Promise<ApiError> {
  try {
    const contentType = response.headers.get('content-type') || ''
    if (contentType.includes('application/problem+json') || contentType.includes('application/json')) {
      const body = await response.json()
      return new ApiError(
        body.status || response.status,
        body.code || body.errorCode || 'UNKNOWN',
        body.detail || body.message || response.statusText,
        body.title
      )
    }
  } catch {
    // Failed to parse body
  }
  return new ApiError(response.status, 'UNKNOWN', response.statusText)
}

/**
 * Authenticated fetch wrapper that includes credentials, CSRF token,
 * and automatic 401 retry via token refresh.
 */
export async function apiFetch(
  endpoint: string,
  options: RequestInit = {}
): Promise<Response> {
  const headers = new Headers(options.headers);

  // Set Content-Type if not already set and not a GET request
  if (!headers.has('Content-Type') && options.method && options.method !== 'GET') {
    headers.set('Content-Type', 'application/json');
  }

  // Add CSRF token from cookie (Spring Security expects X-XSRF-TOKEN header)
  const csrfToken = getCsrfTokenFromCookie();
  if (csrfToken) {
    headers.set('X-XSRF-TOKEN', csrfToken);
  }

  const response = await fetch(`${API_URL}${endpoint}`, {
    ...options,
    headers,
    credentials: 'include',
  });

  // Auto-refresh on 401, but skip for auth endpoints to avoid loops
  if (response.status === 401 && !endpoint.startsWith('/auth/')) {
    const refreshed = await attemptTokenRefresh();
    if (refreshed) {
      // Retry original request with fresh tokens
      const retryHeaders = new Headers(options.headers);
      if (!retryHeaders.has('Content-Type') && options.method && options.method !== 'GET') {
        retryHeaders.set('Content-Type', 'application/json');
      }
      const newCsrf = getCsrfTokenFromCookie();
      if (newCsrf) {
        retryHeaders.set('X-XSRF-TOKEN', newCsrf);
      }
      return fetch(`${API_URL}${endpoint}`, {
        ...options,
        headers: retryHeaders,
        credentials: 'include',
      });
    }

    // Refresh failed — session is dead, force logout
    const { useAuthStore } = await import('../stores/authStore');
    useAuthStore.getState().logout();
    useAuthStore.getState().setSessionExpired(true);
    window.location.href = '/login';
  }

  return response;
}

/**
 * Fetch wrapper for services routed through the Vite dev proxy (market-data, strategy).
 * Does NOT prepend API_URL since these requests must go through the Vite proxy
 * at the same origin, not directly to the portfolio backend.
 */
export async function proxyFetch(
  endpoint: string,
  options: RequestInit = {}
): Promise<Response> {
  const headers = new Headers(options.headers);

  if (!headers.has('Content-Type') && options.method && options.method !== 'GET') {
    headers.set('Content-Type', 'application/json');
  }

  return fetch(endpoint, {
    ...options,
    headers,
  });
}

// Legacy exports for backward compatibility
export function setCsrfToken(_token: string | null): void {
  void _token;
  // No-op: CSRF token is now read from cookie
}

export function getCsrfToken(): string | null {
  return getCsrfTokenFromCookie();
}

export interface VersionResponse {
  version: string
  environment: string
}

export interface HealthResponse {
  status: string
  timestamp: string
}

export async function getVersion(): Promise<VersionResponse> {
  const response = await apiFetch('/api/v1/version')
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }
  return response.json()
}

export async function getHealth(): Promise<HealthResponse> {
  const response = await apiFetch('/health')
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }
  return response.json()
}
