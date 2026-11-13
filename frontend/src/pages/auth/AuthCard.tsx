import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Logo } from "../../components/Logo";

export function AuthCard({ title, subtitle, children }: { title: string; subtitle?: ReactNode; children?: ReactNode }) {
  return (
    <div className="auth-page">
      <div className="auth-card">
        <Link to="/" className="auth-card__logo" aria-label="Ticktit home">
          <Logo size={32} />
        </Link>
        <h1>{title}</h1>
        {subtitle && <p className="auth-card__subtitle">{subtitle}</p>}
        {children}
      </div>
    </div>
  );
}
