import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { authApi } from "../../api/auth";
import { FormNotice } from "../../components/Field";
import { AuthCard } from "./AuthCard";

/** After sign-up: the account works once the link in the email is opened. */
export function CheckEmail() {
  const email = (useLocation().state as { email?: string } | null)?.email;
  const [sent, setSent] = useState(false);

  return (
    <AuthCard
      title="Verify your email"
      subtitle={<>We sent a link to <strong>{email ?? "your email"}</strong>. Open it to activate your account. It works once and expires in 24 hours.</>}
    >
      <FormNotice message={sent ? "If that account still needs verifying, a new link is on its way." : null} />
      {email && !sent && (
        <button className="btn btn--ghost btn--block" onClick={() => authApi.resendVerification(email).finally(() => setSent(true))}>
          Send the link again
        </button>
      )}
      <p className="auth-switch">
        Already verified? <Link to="/signin">Sign in</Link>
      </p>
    </AuthCard>
  );
}
