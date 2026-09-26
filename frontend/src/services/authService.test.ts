import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { login, AuthError } from './authService';
import { scenario } from '../test/scenario';

describe('authService', () => {
  const mockFetch = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('login error handling', () => {
    it(scenario('AUTH-LOGIN-E01', 'throws AuthError with server message for JSON error bodies'), async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: () => Promise.resolve({ error: 'invalid_credentials', message: 'Invalid email or password' }),
      });

      const err = await login('user@test.com', 'wrong').catch((e) => e);

      expect(err).toBeInstanceOf(AuthError);
      expect(err.message).toBe('Invalid email or password');
      expect(err.code).toBe('invalid_credentials');
    });

    it(scenario('AUTH-LOGIN-E02', 'throws readable message when error body is non-JSON (gateway HTML)'), async () => {
      // UAT run 36076869672: Cloudflare 502 returned an HTML body; the old
      // code let response.json() reject with a raw SyntaxError that reached
      // the UI ("The string did not match the expected pattern.").
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 502,
        json: () => Promise.reject(new SyntaxError('Unexpected token <')),
      });

      const err = await login('user@test.com', 'pw').catch((e) => e);

      expect(err).toBeInstanceOf(AuthError);
      expect(err.message).toBe('The service is temporarily unavailable. Please try again.');
      expect(err.code).toBe('502');
    });

    it(scenario('AUTH-LOGIN-E03', 'throws readable message for non-JSON 4xx bodies'), async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 403,
        json: () => Promise.reject(new SyntaxError('Unexpected token <')),
      });

      const err = await login('user@test.com', 'pw').catch((e) => e);

      expect(err).toBeInstanceOf(AuthError);
      expect(err.message).toBe('Login failed. Please try again.');
      expect(err.code).toBe('403');
    });

    it(scenario('AUTH-LOGIN-E04', 'throws readable message when JSON body has no message field'), async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        json: () => Promise.resolve({}),
      });

      const err = await login('user@test.com', 'pw').catch((e) => e);

      expect(err).toBeInstanceOf(AuthError);
      expect(err.message).toBe('The service is temporarily unavailable. Please try again.');
    });
  });
});
