import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Logo } from "../../components/Logo";

type Props = { title: string; subtitle?: ReactNode; children?: ReactNode; showLogo?: boolean };

export function AuthCard({ title, subtitle, children, showLogo = true }: Props) {
  return (
    <div className="auth-page">
      <div className="auth-card">
        {showLogo && (
          <Link to="/" className="auth-card__logo" aria-label="Ticktit home">
            <Logo size={32} />
          </Link>
        )}
        <h1>{title}</h1>
        {subtitle && <p className="auth-card__subtitle">{subtitle}</p>}
        {children}
      </div>
    </div>
  );
}
