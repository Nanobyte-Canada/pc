import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User } from '../types/auth';
import { apiFetch } from '../services/api';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  csrfToken: string | null;

  // Actions
  setUser: (user: User | null) => void;
  setCsrfToken: (token: string | null) => void;
  logout: () => void;
  setLoading: (loading: boolean) => void;
  checkAuth: () => Promise<void>;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      isAuthenticated: false,
      isLoading: true,
      csrfToken: null,

      setUser: (user) =>
        set({
          user,
          isAuthenticated: !!user,
          isLoading: false,
        }),

      setCsrfToken: (token) => set({ csrfToken: token }),

      logout: () =>
        set({
          user: null,
          isAuthenticated: false,
          csrfToken: null,
        }),

      setLoading: (loading) => set({ isLoading: loading }),

      checkAuth: async () => {
        try {
          set({ isLoading: true });
          const response = await apiFetch('/auth/me');

          if (response.ok) {
            const user = await response.json();
            set({
              user,
              isAuthenticated: true,
              isLoading: false,
            });
          } else {
            set({
              user: null,
              isAuthenticated: false,
              isLoading: false,
            });
          }
        } catch {
          set({
            user: null,
            isAuthenticated: false,
            isLoading: false,
          });
        }
      },
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);

// Selector hooks for common use cases
export const useUser = () => useAuthStore((state) => state.user);
export const useIsAuthenticated = () => useAuthStore((state) => state.isAuthenticated);
export const useIsLoading = () => useAuthStore((state) => state.isLoading);
