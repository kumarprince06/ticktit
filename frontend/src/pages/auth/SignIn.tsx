import { useEffect, useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { authApi, type AuthResponse } from "../../api/auth";
import { ApiError } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { Field, FormError, FormNotice } from "../../components/Field";
import { GoogleButton } from "../../components/GoogleButton";
import { AuthCard } from "./AuthCard";

const RESEND_SECONDS = 60;

/** Step 1: email + password. Step 2: the 6-digit code emailed to the user. Only step 2 signs in. */
export function SignIn() {
  const { completeSignIn, status } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string; notice?: string } | null)?.from ?? "/";
  const notice = (location.state as { notice?: string } | null)?.notice;

  const [step, setStep] = useState<"password" | "otp">("password");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [otp, setOtp] = useState("");
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);
  const [resendIn, setResendIn] = useState(0);
  const [info, setInfo] = useState<string | null>(null);

  useEffect(() => {
    if (status === "signed-in") navigate(from, { replace: true });
  }, [status, from, navigate]);

  useEffect(() => {
    if (resendIn <= 0) return;
    const t = setTimeout(() => setResendIn((s) => s - 1), 1000);
    return () => clearTimeout(t);
  }, [resendIn]);

  const done = (res: AuthResponse) => {
    completeSignIn(res);
    navigate(from, { replace: true });
  };

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    setInfo(null);
    try {
      await action();
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, "UNKNOWN", "Something went wrong. Please try again."));
    } finally {
      setBusy(false);
    }
  }

  const submitPassword = (e: FormEvent) => {
    e.preventDefault();
    run(async () => {
      await authApi.login(email, password);
      setStep("otp");
      setOtp("");
      setResendIn(RESEND_SECONDS);
    });
  };

  const submitOtp = (e: FormEvent) => {
    e.preventDefault();
    run(async () => done(await authApi.verifyOtp(email, otp)));
  };

  if (step === "otp") {
    return (
      <AuthCard title="Check your email" subtitle={<>We sent a 6-digit code to <strong>{email}</strong>. It expires in 5 minutes.</>}>
        <form onSubmit={submitOtp} noValidate>
          <FormError message={error?.errors ? null : error?.message} />
          <FormNotice message={info} />
          <Field
            label="Sign-in code"
            name="otp"
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="[0-9]{6}"
            maxLength={6}
            className="otp-input"
            value={otp}
            onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
            error={error?.errors?.otp}
            autoFocus
            required
          />
          <button className="btn btn--accent btn--block" disabled={busy || otp.length !== 6}>
            {busy ? "Checking…" : "Sign in"}
          </button>
        </form>
        <div className="auth-links">
          <button
            className="link-button"
            disabled={resendIn > 0 || busy}
            onClick={() =>
              run(async () => {
                await authApi.resendOtp(email);
                setResendIn(RESEND_SECONDS);
                setInfo("A new code is on its way. Only the newest code works.");
              })
            }
          >
            {resendIn > 0 ? `Resend code in ${resendIn}s` : "Resend code"}
          </button>
          <button className="link-button" onClick={() => { setStep("password"); setError(null); }}>
            Use a different account
          </button>
        </div>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Sign in" subtitle="Welcome back. We'll email you a code to finish signing in.">
      <form onSubmit={submitPassword} noValidate>
        <FormNotice message={notice} />
        <FormError message={error?.errors ? null : error?.message} />
        {error?.code === "EMAIL_NOT_VERIFIED" && (
          <button
            type="button"
            className="link-button"
            onClick={() =>
              run(async () => {
                await authApi.resendVerification(email);
                setInfo("We've sent you a new verification link.");
              })
            }
          >
            Send the verification link again
          </button>
        )}
        <FormNotice message={info} />
        <Field label="Email" name="email" type="email" autoComplete="email" value={email}
          onChange={(e) => setEmail(e.target.value)} error={error?.errors?.email} required />
        <Field label="Password" name="password" type="password" autoComplete="current-password" value={password}
          onChange={(e) => setPassword(e.target.value)} error={error?.errors?.password} required />
        <div className="auth-links auth-links--right">
          <Link to="/forgot-password" state={{ email }}>Forgot password?</Link>
        </div>
        <button className="btn btn--accent btn--block" disabled={busy || !email || !password}>
          {busy ? "Checking…" : "Continue"}
        </button>
      </form>
      <GoogleButton onSignedIn={done} />
      <p className="auth-switch">
        New to Ticktit? <Link to="/signup" state={{ from }}>Create an account</Link>
      </p>
    </AuthCard>
  );
}
