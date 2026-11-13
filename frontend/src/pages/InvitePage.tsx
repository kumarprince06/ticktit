import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../api/client";
import { groupApi, showWhen } from "../api/groups";
import { useAuth } from "../auth/AuthContext";
import { useApi } from "../hooks/useApi";
import { NotFound } from "./NotFound";

/** Opened from the WhatsApp link: /g/K7Q2MA. Anyone can see the preview; joining needs an account. */
export function InvitePage() {
  const code = useParams().code!;
  const { status } = useAuth();
  const navigate = useNavigate();
  const preview = useApi(() => groupApi.preview(code), [code]);
  const [joining, setJoining] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (preview.error?.code === "NOT_FOUND") return <NotFound />;
  if (!preview.data) return <div className="page-loading" aria-busy="true">Opening invite…</div>;

  const p = preview.data;
  const full = p.memberCount >= p.maxMembers;
  const here = `/g/${code}`;

  const join = async () => {
    setJoining(true);
    setError(null);
    try {
      const { groupId } = await groupApi.join(code);
      navigate(`/groups/${groupId}`, { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Couldn't join. Please try again.");
      setJoining(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card invite-card">
        <p className="eyebrow eyebrow--dark">You're invited</p>
        <h1>{p.item.title}</h1>
        <p className="muted">{showWhen(p.item.startsAt)}</p>
        <p className="muted">{p.item.venue} · {p.item.city}</p>
        <p className="invite-card__count">{p.memberCount} of {p.maxMembers} have joined</p>

        {p.status === "CANCELLED" ? (
          <p className="form-error">The organiser cancelled this group.</p>
        ) : p.status === "BOOKED" ? (
          <p className="form-notice">This group has already booked its tickets.</p>
        ) : full ? (
          <p className="form-error">This group is full.</p>
        ) : !p.item.bookable ? (
          <p className="form-error">This show can't be booked any more.</p>
        ) : status === "signed-in" ? (
          <button className="btn btn--accent btn--block" onClick={join} disabled={joining}>
            {joining ? "Joining…" : "Join the group"}
          </button>
        ) : status === "signed-out" ? (
          <>
            <Link to="/signin" state={{ from: here }} className="btn btn--accent btn--block">Sign in to join</Link>
            <p className="auth-switch">New to Ticktit? <Link to="/signup" state={{ from: here }}>Create an account</Link></p>
          </>
        ) : null}
        {error && <p className="form-error" role="alert">{error}</p>}
        <p className="auth-switch"><Link to={p.item.link}>About the movie</Link></p>
      </div>
    </div>
  );
}
