export const API_URL = import.meta.env.VITE_API_URL || ''

/**
 * Get CSRF token from cookie (set by Spring Security's CookieCsrfTokenRepository)
 */
function getCsrfTokenFromCookie(): string | null {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Authenticated fetch wrapper that includes credentials and CSRF token
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

  return response;
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
