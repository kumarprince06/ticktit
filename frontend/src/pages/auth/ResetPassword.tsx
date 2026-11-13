import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { authApi } from "../../api/auth";
import { ApiError } from "../../api/client";
import { Field, FormError } from "../../components/Field";
import { AuthCard } from "./AuthCard";

export function ResetPassword() {
  const navigate = useNavigate();
  const [email, setEmail] = useState((useLocation().state as { email?: string } | null)?.email ?? "");
  const [otp, setOtp] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await authApi.resetPassword(email, otp, password);
      navigate("/signin", { state: { notice: "Password changed. You've been signed out everywhere, so please sign in again." } });
    } catch (err) {
      setError(err instanceof ApiError ? err : new ApiError(0, "UNKNOWN", "Something went wrong. Please try again."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthCard title="Choose a new password" subtitle="If an account exists for this email, we've sent it a 6-digit code.">
      <form onSubmit={submit} noValidate>
        <FormError message={error?.errors ? null : error?.message} />
        <Field label="Email" name="email" type="email" autoComplete="email" value={email}
          onChange={(e) => setEmail(e.target.value)} error={error?.errors?.email} required />
        <Field label="Code from the email" name="otp" inputMode="numeric" autoComplete="one-time-code" maxLength={6}
          className="otp-input" value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
          error={error?.errors?.otp} required />
        <Field label="New password" name="newPassword" type="password" autoComplete="new-password" value={password}
          onChange={(e) => setPassword(e.target.value)} error={error?.errors?.newPassword}
          hint="At least 8 characters, with an uppercase letter, a number and a symbol." required />
        <button className="btn btn--accent btn--block" disabled={busy || otp.length !== 6 || !password}>
          {busy ? "Saving…" : "Save new password"}
        </button>
      </form>
      <p className="auth-switch">
        No code? <Link to="/forgot-password" state={{ email }}>Send it again</Link>
      </p>
    </AuthCard>
  );
}
