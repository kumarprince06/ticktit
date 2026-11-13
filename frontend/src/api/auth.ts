import { api } from "./client";

/** Mirrors the backend's AuthDtos. */
export type User = { id: string; name: string; email: string; role: string; avatarUrl: string | null };
export type AuthResponse = { accessToken: string; user: User; isNewUser?: boolean | null };

export const authApi = {
  signup: (body: { name: string; email: string; phone?: string; password: string }) =>
    api("/auth/signup", { method: "POST", body }),
  verifyEmail: (token: string) => api(`/auth/verify-email?token=${encodeURIComponent(token)}`),
  resendVerification: (email: string) => api("/auth/resend-verification", { method: "POST", body: { email } }),

  login: (email: string, password: string) => api("/auth/login", { method: "POST", body: { email, password } }),
  verifyOtp: (email: string, otp: string) =>
    api<AuthResponse>("/auth/login/verify-otp", { method: "POST", body: { email, otp } }),
  resendOtp: (email: string) => api("/auth/login/resend-otp", { method: "POST", body: { email } }),
  google: (idToken: string) => api<AuthResponse>("/auth/google", { method: "POST", body: { idToken } }),

  forgotPassword: (email: string) => api("/auth/forgot-password", { method: "POST", body: { email } }),
  resetPassword: (email: string, otp: string, newPassword: string) =>
    api("/auth/reset-password", { method: "POST", body: { email, otp, newPassword } }),

  me: () => api<User>("/me", { auth: true }),
  logout: () => api("/auth/logout", { method: "POST" }),
};
