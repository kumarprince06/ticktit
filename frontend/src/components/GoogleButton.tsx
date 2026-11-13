import { GoogleLogin } from "@react-oauth/google";
import { useState } from "react";
import { authApi, type AuthResponse } from "../api/auth";
import { ApiError } from "../api/client";

export const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined;

/**
 * "Continue with Google": one button for both sign-up and sign-in. Google gives us an ID token,
 * the backend checks it and returns our own tokens. Hidden when VITE_GOOGLE_CLIENT_ID is not set.
 */
export function GoogleButton({ onSignedIn }: { onSignedIn: (res: AuthResponse) => void }) {
  const [error, setError] = useState<string | null>(null);
  if (!GOOGLE_CLIENT_ID) return null;

  return (
    <div className="google-block">
      <div className="divider"><span>or</span></div>
      <div className="google-button">
        <GoogleLogin
          text="continue_with"
          shape="pill"
          width="320"
          onSuccess={async ({ credential }) => {
            setError(null);
            if (!credential) return setError("Google sign-in failed. Please try again.");
            try {
              onSignedIn(await authApi.google(credential));
            } catch (e) {
              setError(e instanceof ApiError ? e.message : "Google sign-in failed. Please try again.");
            }
          }}
          onError={() => setError("Google sign-in was cancelled or failed.")}
        />
      </div>
      {error && <p className="field__error" role="alert">{error}</p>}
    </div>
  );
}
