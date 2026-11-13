import { Link, useNavigate } from "react-router-dom";
import { groupApi, showWhen } from "../api/groups";
import { useApi } from "../hooks/useApi";
import { useAuth } from "../auth/AuthContext";
import { Avatar } from "../components/Avatar";

export function Account() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const groups = useApi(() => groupApi.mine(), []);
  if (!user) return null;

  return (
    <div className="container narrow section">
      <div className="account-head">
        <Avatar name={user.name} url={user.avatarUrl} size={64} />
        <h1>Your account</h1>
      </div>
      <dl className="details">
        <dt>Name</dt>
        <dd>{user.name}</dd>
        <dt>Email</dt>
        <dd>{user.email}</dd>
        <dt>Account type</dt>
        <dd>{user.role === "USER" ? "Member" : user.role}</dd>
      </dl>

      <section className="section">
        <h2>Your groups</h2>
        {groups.data?.length ? (
          <ul className="group-list">
            {groups.data.map((g) => (
              <li key={g.id}>
                <Link to={`/groups/${g.id}`} className="group-list__item">
                  <strong>{g.item.title}</strong>
                  <span className="muted">{showWhen(g.item.startsAt)} · {g.item.venue}</span>
                  <span className="muted">{g.memberCount} member{g.memberCount === 1 ? "" : "s"} · {g.youAreLeader ? "You organise" : "Joined"}{g.status !== "OPEN" && ` · ${g.status.toLowerCase()}`}</span>
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <p className="empty">{groups.loading ? "Loading…" : "No groups yet. Pick a show and tap \"Book together\" to start one."}</p>
        )}
      </section>

      <section className="section">
        <h2>Your bookings</h2>
        <p className="empty">No bookings yet. When you book with a group, your tickets will show up here.</p>
      </section>

      <button
        className="btn btn--ghost"
        onClick={async () => {
          await signOut();
          navigate("/");
        }}
      >
        Sign out
      </button>
    </div>
  );
}
