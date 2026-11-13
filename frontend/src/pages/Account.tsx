import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Avatar } from "../components/Avatar";

export function Account() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
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
