import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { authApi } from "../../api/auth";
import { ApiError } from "../../api/client";
import { Field, FormError } from "../../components/Field";
import { AuthCard } from "./AuthCard";

export function ForgotPassword() {
  const navigate = useNavigate();
  const [email, setEmail] = useState((useLocation().state as { email?: string } | null)?.email ?? "");
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await authApi.forgotPassword(email);
      navigate("/reset-password", { state: { email } });
    } catch (err) {
      setError(err instanceof ApiError ? err : new ApiError(0, "UNKNOWN", "Something went wrong. Please try again."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthCard title="Reset your password" subtitle="Enter your account email and we'll send you a 6-digit code.">
      <form onSubmit={submit} noValidate>
        <FormError message={error?.errors ? null : error?.message} />
        <Field label="Email" name="email" type="email" autoComplete="email" value={email}
          onChange={(e) => setEmail(e.target.value)} error={error?.errors?.email} required />
        <button className="btn btn--accent btn--block" disabled={busy || !email}>
          {busy ? "Sending…" : "Send code"}
        </button>
      </form>
      <p className="auth-switch"><Link to="/signin">Back to sign in</Link></p>
    </AuthCard>
  );
}
