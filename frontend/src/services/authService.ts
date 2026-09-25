import { useAuthStore } from '../stores/authStore';
import { apiFetch, API_URL } from './api';
import type {
  AuthResponse,
  AuthErrorResponse,
} from '../types/auth';

class AuthError extends Error {
  constructor(
    public code: string,
    message: string,
    public field?: string,
    public lockedUntil?: string
  ) {
    super(message);
    this.name = 'AuthError';
  }
}

async function handleAuthError(response: Response): Promise<never> {
  let body: Partial<AuthErrorResponse> | null = null;
  try {
    const parsed: unknown = await response.json();
    if (parsed && typeof parsed === 'object') {
      body = parsed as Partial<AuthErrorResponse>;
    }
  } catch {
    // Non-JSON body — e.g. an HTML error page from Cloudflare/CDN on a 502.
    body = null;
  }

  if (body && typeof body.message === 'string' && body.message) {
    throw new AuthError(
      body.error ?? String(response.status),
      body.message,
      body.field,
      body.lockedUntil
    );
  }

  // No usable JSON error body: surface a human-readable message instead of a
  // raw JSON parse error (UAT run 36076869672 — WebKit surfaced
  // "The string did not match the expected pattern." from a Cloudflare 502 HTML page).
  throw new AuthError(
    String(response.status),
    response.status >= 500
      ? 'The service is temporarily unavailable. Please try again.'
      : 'Login failed. Please try again.'
  );
}

export async function logout(): Promise<void> {
  try {
    await apiFetch('/auth/logout', { method: 'POST' });
  } finally {
    useAuthStore.getState().logout();
  }
}

export async function refreshToken(): Promise<AuthResponse | null> {
  const response = await apiFetch('/auth/refresh', {
    method: 'POST',
  });

  if (!response.ok) {
    useAuthStore.getState().logout();
    return null;
  }

  const data: AuthResponse = await response.json();
  useAuthStore.getState().setUser(data.user);
  return data;
}

export async function getMe(): Promise<AuthResponse> {
  const response = await apiFetch('/auth/me');

  if (!response.ok) {
    await handleAuthError(response);
  }

  const user = await response.json();
  useAuthStore.getState().setUser(user);
  return { user };
}

export function initiateGoogleLogin(): void {
  window.location.href = `${API_URL}/auth/google`;
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const response = await apiFetch('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });

  if (!response.ok) {
    await handleAuthError(response);
  }

  const data: AuthResponse = await response.json();
  useAuthStore.getState().setUser(data.user);
  return data;
}

export interface UpdateProfileData {
  name?: string;
  avatarUrl?: string;
}

export async function updateProfile(data: UpdateProfileData): Promise<AuthResponse> {
  const response = await apiFetch('/auth/profile', {
    method: 'PUT',
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    await handleAuthError(response);
  }

  const user = await response.json();
  useAuthStore.getState().setUser(user);
  return { user };
}

export { AuthError };
