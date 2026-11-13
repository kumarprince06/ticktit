import { useEffect } from "react";
import { Route, Routes, useLocation } from "react-router-dom";
import { RequireAuth } from "./auth/RequireAuth";
import { Layout } from "./components/Layout";
import { Account } from "./pages/Account";
import { CheckEmail } from "./pages/auth/CheckEmail";
import { ForgotPassword } from "./pages/auth/ForgotPassword";
import { ResetPassword } from "./pages/auth/ResetPassword";
import { SignIn } from "./pages/auth/SignIn";
import { SignUp } from "./pages/auth/SignUp";
import { VerifyEmail } from "./pages/auth/VerifyEmail";
import { Home } from "./pages/Home";
import { MovieDetail } from "./pages/MovieDetail";
import { NotFound } from "./pages/NotFound";
import { SeatPicker } from "./pages/SeatPicker";

export function App() {
  const { pathname } = useLocation();
  // Braces matter: an effect may only return a clean-up function. Some browser extensions make scrollTo return
  // a Promise, and returning that crashed the whole app.
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="movies/:slug" element={<MovieDetail />} />
        <Route path="shows/:showId/seats" element={<RequireAuth><SeatPicker /></RequireAuth>} />
        <Route path="account" element={<RequireAuth><Account /></RequireAuth>} />

        <Route path="signin" element={<SignIn />} />
        <Route path="signup" element={<SignUp />} />
        <Route path="check-email" element={<CheckEmail />} />
        <Route path="verify-email" element={<VerifyEmail />} />
        <Route path="forgot-password" element={<ForgotPassword />} />
        <Route path="reset-password" element={<ResetPassword />} />

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
