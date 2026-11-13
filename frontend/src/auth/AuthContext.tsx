import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { authApi, type AuthResponse, type User } from "../api/auth";
import { refreshAccessToken, setAccessToken } from "../api/client";

type Status = "loading" | "signed-in" | "signed-out";

type AuthState = {
  status: Status;
  user: User | null;
  /** Call with the response of verify-otp or Google sign-in. */
  completeSignIn: (res: AuthResponse) => void;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>("loading");
  const [user, setUser] = useState<User | null>(null);

  // On page load: if the refresh cookie is still valid, quietly restore the session.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const token = await refreshAccessToken();
      if (token) {
        try {
          const me = await authApi.me();
          if (!cancelled) {
            setUser(me);
            setStatus("signed-in");
          }
          return;
        } catch {
          setAccessToken(null);
        }
      }
      if (!cancelled) setStatus("signed-out");
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const completeSignIn = useCallback((res: AuthResponse) => {
    setAccessToken(res.accessToken);
    setUser(res.user);
    setStatus("signed-in");
  }, []);

  const signOut = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setAccessToken(null);
      setUser(null);
      setStatus("signed-out");
    }
  }, []);

  return <AuthContext.Provider value={{ status, user, completeSignIn, signOut }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>");
  return ctx;
}
