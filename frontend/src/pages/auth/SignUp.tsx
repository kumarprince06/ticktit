import { useState, type ChangeEvent, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { authApi, type AuthResponse } from "../../api/auth";
import { ApiError } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { Field, FormError } from "../../components/Field";
import { GoogleButton } from "../../components/GoogleButton";
import { AuthCard } from "./AuthCard";

export function SignUp() {
  const { completeSignIn } = useAuth();
  const navigate = useNavigate();
  const from = (useLocation().state as { from?: string } | null)?.from ?? "/";
  const [form, setForm] = useState({ name: "", email: "", phone: "", password: "" });
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  const set = (key: keyof typeof form) => (e: ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await authApi.signup({ ...form, phone: form.phone.trim() || undefined });
      navigate("/check-email", { state: { email: form.email } });
    } catch (err) {
      setError(err instanceof ApiError ? err : new ApiError(0, "UNKNOWN", "Something went wrong. Please try again."));
    } finally {
      setBusy(false);
    }
  };

  const google = (res: AuthResponse) => {
    completeSignIn(res);
    navigate(from, { replace: true });
  };

  return (
    <AuthCard title="Create your account" subtitle="Book with friends and let everyone pay their own share.">
      <form onSubmit={submit} noValidate>
        <FormError message={error?.errors ? null : error?.message} />
        {error?.code === "EMAIL_ALREADY_EXISTS" && (
          <p className="auth-links"><Link to="/signin">Sign in instead</Link></p>
        )}
        <Field label="Full name" name="name" autoComplete="name" value={form.name} onChange={set("name")}
          error={error?.errors?.name} required />
        <Field label="Email" name="email" type="email" autoComplete="email" value={form.email} onChange={set("email")}
          error={error?.errors?.email} required />
        <Field label="Mobile number (optional)" name="phone" type="tel" autoComplete="tel-national" inputMode="numeric"
          placeholder="98XXXXXXXX" value={form.phone} onChange={set("phone")} error={error?.errors?.phone}
          hint="Only used to contact you about bookings. Not for signing in." />
        <Field label="Password" name="password" type="password" autoComplete="new-password" value={form.password}
          onChange={set("password")} error={error?.errors?.password}
          hint="At least 8 characters, with an uppercase letter, a number and a symbol." required />
        <button className="btn btn--accent btn--block" disabled={busy}>
          {busy ? "Creating account…" : "Create account"}
        </button>
      </form>
      <GoogleButton onSignedIn={google} />
      <p className="auth-switch">
        Already have an account? <Link to="/signin" state={{ from }}>Sign in</Link>
      </p>
    </AuthCard>
  );
}
