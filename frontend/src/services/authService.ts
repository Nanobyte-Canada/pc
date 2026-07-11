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
  const error: AuthErrorResponse = await response.json();
  throw new AuthError(error.error, error.message, error.field, error.lockedUntil);
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
