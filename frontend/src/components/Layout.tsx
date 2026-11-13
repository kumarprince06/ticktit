import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { CITIES } from "../data/catalog";
import { useCity } from "../hooks/useCity";

export function Layout() {
  return (
    <>
      <Header />
      <main id="main">
        <Outlet />
      </main>
      <Footer />
    </>
  );
}

function Header() {
  const { status, user } = useAuth();
  const [city, setCity] = useCity();
  const navigate = useNavigate();

  return (
    <header className="site-header">
      <div className="container site-header__row">
        <Link to="/" className="logo" aria-label="Ticktit home">
          <svg viewBox="0 0 32 32" width="28" height="28" aria-hidden="true">
            <path d="M5 9h22v5a2 2 0 0 0 0 4v5H5v-5a2 2 0 0 0 0-4z" fill="var(--accent)" />
            <path d="M12 9v14" stroke="var(--ink)" strokeWidth="1.6" strokeDasharray="2 2" />
          </svg>
          <span>Ticktit</span>
        </Link>

        <form
          className="search"
          role="search"
          onSubmit={(e) => {
            e.preventDefault();
            const q = new FormData(e.currentTarget).get("q")?.toString().trim();
            navigate(q ? `/?q=${encodeURIComponent(q)}` : "/");
          }}
        >
          <input name="q" type="search" placeholder="Search movies" aria-label="Search movies" />
        </form>

        <div className="site-header__actions">
          <label className="city-select">
            <span className="visually-hidden">City</span>
            <select value={city} onChange={(e) => setCity(e.target.value)}>
              {CITIES.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </label>
          {status === "signed-in" && user ? (
            <UserMenu name={user.name} />
          ) : status === "signed-out" ? (
            <Link to="/signin" className="btn btn--accent btn--small">Sign in</Link>
          ) : null}
        </div>
      </div>
    </header>
  );
}

function UserMenu({ name }: { name: string }) {
  const { signOut } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    const close = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && setOpen(false);
    document.addEventListener("click", close);
    return () => document.removeEventListener("click", close);
  }, []);

  return (
    <div className="user-menu" ref={ref}>
      <button className="avatar" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        {name.trim().charAt(0).toUpperCase()}
      </button>
      {open && (
        <div className="user-menu__panel" role="menu">
          <p className="user-menu__name">Hi, {name.split(" ")[0]}</p>
          <NavLink to="/account" role="menuitem" onClick={() => setOpen(false)}>Your account</NavLink>
          <button
            role="menuitem"
            onClick={async () => {
              setOpen(false);
              await signOut();
              navigate("/");
            }}
          >
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}

function Footer() {
  return (
    <footer className="site-footer">
      <div className="container site-footer__row">
        <p>
          <strong>Ticktit</strong> · Book together. Everyone pays their own share.
        </p>
        <p className="muted">Demo catalogue. All movies, cinemas and people shown are fictional.</p>
      </div>
    </footer>
  );
}
