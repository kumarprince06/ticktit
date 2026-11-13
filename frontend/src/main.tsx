import { GoogleOAuthProvider } from "@react-oauth/google";
import { StrictMode, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { App } from "./App";
import { AuthProvider } from "./auth/AuthContext";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { GOOGLE_CLIENT_ID } from "./components/GoogleButton";
import "./styles.css";

/** Loads Google's script only when a client id is configured. */
function MaybeGoogle({ children }: { children: ReactNode }) {
  return GOOGLE_CLIENT_ID ? <GoogleOAuthProvider clientId={GOOGLE_CLIENT_ID}>{children}</GoogleOAuthProvider> : <>{children}</>;
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ErrorBoundary>
      <MaybeGoogle>
        {/* opt in to React Router v7 behaviour now (also silences its upgrade warnings) */}
        <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </MaybeGoogle>
    </ErrorBoundary>
  </StrictMode>,
);
