import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../api/client";
import { groupApi, showWhen, type GroupView } from "../api/groups";
import { Avatar } from "../components/Avatar";
import { NotFound } from "./NotFound";

const REFRESH_MS = 5000;   // new members show up without a reload

/** The group's home: who's in, the invite link, and (for the organiser) remove / cancel. Seats come next. */
export function GroupPage() {
  const id = useParams().id!;
  const navigate = useNavigate();
  const [group, setGroup] = useState<GroupView | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let current = true;
    const load = () =>
      groupApi.view(id)
        .then((g) => current && (setGroup(g), setError(null)))
        .catch((e) => current && setError(e instanceof ApiError ? e : new ApiError(0, "UNKNOWN", "Something went wrong.")));
    load();
    const timer = setInterval(load, REFRESH_MS);
    return () => {
      current = false;
      clearInterval(timer);
    };
  }, [id]);

  if (error?.code === "NOT_FOUND") return <NotFound />;
  if (!group) {
    return error
      ? <div className="container section"><p className="form-error" role="alert">{error.message}</p></div>
      : <div className="page-loading" aria-busy="true">Loading your group…</div>;
  }

  const act = async (action: () => Promise<unknown>, after?: () => void) => {
    setActionError(null);
    try {
      await action();
      after ? after() : setGroup(await groupApi.view(id));
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : "Something went wrong. Please try again.");
    }
  };
  const open = group.status === "OPEN";
  const spotsLeft = group.maxMembers - group.members.length;

  return (
    <div className="container narrow section group-page">
      <Link to={group.item.link} className="back">← {group.item.title}</Link>

      <header className="group-head">
        <p className="eyebrow eyebrow--dark">Group · {group.code}</p>
        <h1>{group.item.title}</h1>
        <p className="muted">{showWhen(group.item.startsAt)} · {group.item.venue} · {group.item.detail}</p>
        {group.status === "CANCELLED" && <p className="form-error">This group was cancelled.</p>}
      </header>

      {open && (
        <section className="share-box">
          <h2>Invite your friends</h2>
          <p className="muted">Anyone with this link can join until the group is full ({spotsLeft} spot{spotsLeft === 1 ? "" : "s"} left).</p>
          <div className="share-box__link">
            <input readOnly value={group.shareLink} aria-label="Invite link" onFocus={(e) => e.currentTarget.select()} />
            <button className="btn btn--ghost btn--small" onClick={() =>
              navigator.clipboard.writeText(group.shareLink).then(() => { setCopied(true); setTimeout(() => setCopied(false), 2000); })}>
              {copied ? "Copied" : "Copy"}
            </button>
          </div>
          <a className="btn btn--whatsapp" href={group.whatsappShareUrl} target="_blank" rel="noopener noreferrer">
            Share on WhatsApp
          </a>
        </section>
      )}

      <section className="section">
        <h2>Who's coming ({group.members.length}/{group.maxMembers})</h2>
        <ul className="members">
          {group.members.map((m) => (
            <li key={m.userId} className="member">
              <Avatar name={m.name} url={m.avatarUrl} size={40} />
              <div className="member__who">
                <strong>{m.name}{m.you && " (you)"}</strong>
                <span className="muted">{m.role === "LEADER" ? "Organiser" : "Joined"}</span>
              </div>
              {open && m.role !== "LEADER" && (group.youAreLeader || m.you) && (
                <button className="link-button" onClick={() =>
                  act(() => groupApi.removeMember(group.id, m.userId), m.you ? () => navigate("/account") : undefined)}>
                  {m.you ? "Leave" : "Remove"}
                </button>
              )}
            </li>
          ))}
        </ul>
        {actionError && <p className="form-error" role="alert">{actionError}</p>}
      </section>

      {open && (
        <section className="next-step">
          <h2>Next: choose seats</h2>
          <p className="muted">
            {group.youAreLeader
              ? "Once your friends have joined, you'll pick seats for everyone and each person pays their own share."
              : "The organiser picks seats for the group. You'll then pay for your own seat."}{" "}
            Seat locking and payment are coming in the next update.
          </p>
          {group.youAreLeader && (
            <button className="btn btn--ghost" onClick={() => {
              if (confirm("Cancel this group? Everyone will be removed.")) act(() => groupApi.cancel(group.id));
            }}>
              Cancel group
            </button>
          )}
        </section>
      )}
    </div>
  );
}
