export interface User {
  id: number;
  email: string;
  name: string | null;
  avatarUrl: string | null;
  emailVerified: boolean;
  roles: string[];
  identities: Identity[];
  lastLoginAt: string | null;
  createdAt: string;
}

export interface Identity {
  provider: string;
  providerEmail: string | null;
  connectedAt: string;
}

export interface AuthResponse {
  user: User;
  message?: string;
}

export interface SignupResponse {
  message: string;
  userId: number;
}

export interface MessageResponse {
  message: string;
}

export interface AuthErrorResponse {
  error: string;
  message: string;
  field?: string;
  lockedUntil?: string;
}

export interface SignupRequest {
  email: string;
  password: string;
  name?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}
