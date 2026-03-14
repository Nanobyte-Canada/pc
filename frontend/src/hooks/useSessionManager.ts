import { useEffect, useRef, useCallback } from 'react';
import { useAuthStore } from '../stores/authStore';
import { API_URL } from '../services/api';

const ACTIVITY_THROTTLE_MS = 30_000; // 30 seconds
const PROACTIVE_REFRESH_INTERVAL_MS = 5 * 60_000; // 5 minutes
const SESSION_TIMEOUT_MS = 6 * 60 * 60_000; // 6 hours
const WARNING_BEFORE_TIMEOUT_MS = 5 * 60_000; // 5 minutes before timeout

function getCsrfTokenFromCookie(): string | null {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

export function useSessionManager() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const logout = useAuthStore((s) => s.logout);
  const setSessionExpired = useAuthStore((s) => s.setSessionExpired);

  const lastActivityRef = useRef(Date.now());
  const lastRefreshRef = useRef(Date.now());
  const warningFiredRef = useRef(false);

  const handleActivity = useCallback(() => {
    const now = Date.now();
    if (now - lastActivityRef.current > ACTIVITY_THROTTLE_MS) {
      lastActivityRef.current = now;
      warningFiredRef.current = false;
    }
  }, []);

  const doRefresh = useCallback(async (): Promise<boolean> => {
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
      if (response.ok) {
        lastRefreshRef.current = Date.now();
        return true;
      }
      return false;
    } catch {
      return false;
    }
  }, []);

  const forceLogout = useCallback(() => {
    logout();
    setSessionExpired(true);
    window.location.href = '/login';
  }, [logout, setSessionExpired]);

  // Extend session (called from warning modal "Stay logged in")
  const extendSession = useCallback(async () => {
    const success = await doRefresh();
    if (success) {
      lastActivityRef.current = Date.now();
      warningFiredRef.current = false;
    } else {
      forceLogout();
    }
  }, [doRefresh, forceLogout]);

  useEffect(() => {
    if (!isAuthenticated) return;

    const activityEvents = ['mousemove', 'keydown', 'scroll', 'click', 'touchstart'] as const;
    for (const event of activityEvents) {
      window.addEventListener(event, handleActivity, { passive: true });
    }

    const intervalId = setInterval(() => {
      const now = Date.now();
      const idleTime = now - lastActivityRef.current;

      // Session expired due to idle
      if (idleTime >= SESSION_TIMEOUT_MS) {
        forceLogout();
        return;
      }

      // Fire warning event before timeout
      if (
        !warningFiredRef.current &&
        idleTime >= SESSION_TIMEOUT_MS - WARNING_BEFORE_TIMEOUT_MS
      ) {
        warningFiredRef.current = true;
        window.dispatchEvent(new CustomEvent('session-expiring'));
      }

      // Proactively refresh if user was active since last refresh
      if (
        lastActivityRef.current > lastRefreshRef.current &&
        now - lastRefreshRef.current >= PROACTIVE_REFRESH_INTERVAL_MS
      ) {
        doRefresh();
      }
    }, 60_000); // Check every minute

    return () => {
      for (const event of activityEvents) {
        window.removeEventListener(event, handleActivity);
      }
      clearInterval(intervalId);
    };
  }, [isAuthenticated, handleActivity, doRefresh, forceLogout]);

  return { extendSession };
}
