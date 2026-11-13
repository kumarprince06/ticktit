import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { authApi } from "../../api/auth";
import { ApiError } from "../../api/client";
import { AuthCard } from "./AuthCard";

/** Opened from the link in the verification email: /verify-email?token=... */
export function VerifyEmail() {
  const token = useSearchParams()[0].get("token");
  const [state, setState] = useState<"checking" | "ok" | "failed">(token ? "checking" : "failed");
  const [message, setMessage] = useState("This link is invalid or has expired. Please request a new one.");
  const called = useRef(false);

  useEffect(() => {
    // links work once: never call twice (React dev mode runs effects twice)
    if (!token || called.current) return;
    called.current = true;
    authApi
      .verifyEmail(token)
      .then(() => setState("ok"))
      .catch((e) => {
        if (e instanceof ApiError) setMessage(e.message);
        setState("failed");
      });
  }, [token]);

  if (state === "checking") return <AuthCard title="Verifying your email…" />;
  if (state === "ok") {
    return (
      <AuthCard title="Email verified" subtitle="Your account is ready. Sign in to start booking.">
        <Link to="/signin" className="btn btn--accent btn--block">Sign in</Link>
      </AuthCard>
    );
  }
  return (
    <AuthCard title="Link didn't work" subtitle={message}>
      <p className="muted">Sign in with your email and password and we'll offer to send a new link.</p>
      <Link to="/signin" className="btn btn--ghost btn--block">Go to sign in</Link>
    </AuthCard>
  );
}
