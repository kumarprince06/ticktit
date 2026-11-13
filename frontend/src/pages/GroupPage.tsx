import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../api/client";
import { bookingApi, type BookingView } from "../api/bookings";
import { paymentApi, payWithRazorpay } from "../api/payments";
import { rupees } from "../api/catalog";
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
  const [booking, setBooking] = useState<BookingView | null>(null);

  useEffect(() => {
    let current = true;
    const load = () =>
      Promise.all([groupApi.view(id), bookingApi.current(id)])
        .then(([g, b]) => current && (setGroup(g), setBooking(b), setError(null)))
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
      if (after) after();
      else {
        setGroup(await groupApi.view(id));
        setBooking(await bookingApi.current(id));
      }
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : "Something went wrong. Please try again.");
    }
  };
  const open = group.status === "OPEN";
  const solo = group.maxMembers === 1;   // "Book for myself": same engine, no group screens
  const spotsLeft = group.maxMembers - group.members.length;

  return (
    <div className="container narrow section group-page">
      <Link to={group.item.link} className="back">← {group.item.title}</Link>

      <header className="group-head">
        <p className="eyebrow eyebrow--dark">{solo ? "Your booking" : "Group"} · {group.code}</p>
        <h1>{group.item.title}</h1>
        <p className="muted">{showWhen(group.item.startsAt)} · {group.item.venue} · {group.item.detail}</p>
        {group.status === "CANCELLED" && <p className="form-error">This {solo ? "booking" : "group"} was cancelled.</p>}
      </header>

      {open && !solo && (
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

      {!solo && <section className="section">
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
      </section>}
      {solo && actionError && <p className="form-error" role="alert">{actionError}</p>}

      {(open || booking) && (   /* a BOOKED group still shows its confirmed booking */
        <SeatsPanel group={group} booking={booking} act={act} solo={solo}
          refresh={async () => { setGroup(await groupApi.view(id)); setBooking(await bookingApi.current(id)); }}
          onExpired={() => { bookingApi.current(id).then(setBooking).catch(() => setBooking(null)); }}
          chooseSeats={() => navigate(`/shows/${group.item.itemId}/seats?group=${group.id}${solo ? "&solo=1" : ""}`)} />
      )}

      {open && group.youAreLeader && !booking && !solo && (
        <p className="group-cancel">
          <button className="link-button" onClick={() => {
            if (confirm("Cancel this group? Everyone will be removed.")) act(() => groupApi.cancel(group.id));
          }}>
            Cancel group
          </button>
        </p>
      )}
    </div>
  );
}

/**
 * The group's seats and money: choose seats, the 5-minute hold, who sits where, then payment (PRD F6/F7):
 * Split Pay (each pays their share) or Pay For All, the 10-minute window, paid/pending per person, and the result.
 */
function SeatsPanel({ group, booking, act, solo, onExpired, chooseSeats, refresh }: {
  group: GroupView;
  solo: boolean;
  booking: BookingView | null;
  act: (action: () => Promise<unknown>) => Promise<void>;
  onExpired: () => void;
  chooseSeats: () => void;
  refresh: () => Promise<void>;
}) {
  const deadline = booking?.status === "LOCKED" ? booking.lockExpiresAt : booking?.status === "PAYMENT_PENDING" ? booking.paymentDeadline : null;
  const left = useCountdown(deadline, onExpired);
  const [paying, setPaying] = useState(false);
  const [payMessage, setPayMessage] = useState<{ kind: "error" | "info"; text: string } | null>(null);

  if (!booking) {
    return (
      <section className="next-step">
        <h2>{solo ? "Choose your seats" : "Next: choose seats"}</h2>
        <p className="muted">
          {solo ? "Your seats were released. Pick them again and they'll be held for 5 minutes." : group.youAreLeader
            ? "When your friends have joined, pick seats for everyone. They're held for 5 minutes while you sort out who sits where."
            : "The organiser picks seats for the group. You'll then pay for your own seat."}
        </p>
        {group.youAreLeader && <button className="btn btn--accent" onClick={chooseSeats}>Choose seats</button>}
      </section>
    );
  }

  if (booking.status === "CONFIRMED") {
    return (
      <section className="seats-panel result result--ok">
        <p className="result__icon" aria-hidden="true">✓</p>
        <h2>You're booked!</h2>
        <p>Booking <strong>{booking.code}</strong> · {booking.seats.map((s) => s.seatNo).join(", ")} · {rupees(booking.totalPaise)} paid</p>
        {booking.invoiceNo && <p className="muted small">GST invoice {booking.invoiceNo}</p>}
        <p className="muted small">Your QR tickets will appear here once tickets are switched on.</p>
      </section>
    );
  }
  if (booking.status === "FAILED") {
    return (
      <section className="seats-panel result result--failed">
        <h2>The booking didn't go through</h2>
        <p>{booking.paidPaise > 0
          ? "Time ran out before everyone paid, so the seats were released. Everyone who paid is being refunded in full automatically (usually 5–7 working days)."
          : "Time ran out before payment, so the seats were released."}</p>
        {group.youAreLeader && <button className="btn btn--accent" onClick={chooseSeats}>Choose seats again</button>}
      </section>
    );
  }

  const pending = booking.status === "PAYMENT_PENDING";
  const urgent = left !== null && left <= 60;
  const split = booking.mode === "SPLIT";

  const pay = async (mode?: "SPLIT" | "PAY_FOR_ALL") => {
    setPaying(true);
    setPayMessage(null);
    try {
      const checkout = await paymentApi.checkout(booking.id, mode);
      await refresh();   // the window may have just started
      const result = await payWithRazorpay(checkout);
      if (result.outcome === "paid") setPayMessage({ kind: "info", text: "Payment received. Thank you!" });
      else if (result.outcome === "failed") setPayMessage({ kind: "error", text: `${result.message} You can try again.` });
      else setPayMessage({ kind: "info", text: "Payment window closed. Tap Pay whenever you're ready." });
    } catch (e) {
      setPayMessage({ kind: "error", text: e instanceof ApiError || e instanceof Error ? e.message : "Something went wrong. Please try again." });
    } finally {
      setPaying(false);
      await refresh();
    }
  };

  return (
    <section className="seats-panel">
      <div className="seats-panel__head">
        <h2>{pending ? (solo ? "Payment" : "Payment in progress") : "Your seats"}</h2>
        {left !== null && (
          <span className={`countdown${urgent ? " countdown--urgent" : ""}`} role="timer" aria-live={urgent ? "assertive" : "off"}>
            {pending ? "Pay within" : "Held for"} {Math.floor(left / 60)}:{String(left % 60).padStart(2, "0")}
          </span>
        )}
      </div>

      <table className="seats-table">
        <thead><tr><th>Seat</th>{!solo && <th>Who sits here</th>}<th className="num">{solo ? "Price" : "Pays"}</th></tr></thead>
        <tbody>
          {booking.seats.map((s) => (
            <tr key={s.seatNo}>
              <td><strong>{s.seatNo}</strong> <span className="muted">{s.category.toLowerCase()}</span></td>
              {!solo && <td>
                {booking.youAreLeader && !pending ? (
                  <select aria-label={`Who sits in ${s.seatNo}`} value={s.assignee?.userId ?? ""}
                    onChange={(e) => act(() => bookingApi.assign(booking.id, [{ seatNo: s.seatNo, userId: e.target.value }]))}>
                    {!s.assignee && <option value="">Choose…</option>}
                    {group.members.map((m) => <option key={m.userId} value={m.userId}>{m.name}{m.you ? " (you)" : ""}</option>)}
                  </select>
                ) : (s.assignee?.name ?? "Not decided yet")}
              </td>}
              <td className="num">{rupees(s.payablePaise)} {pending && (s.paid ? <span className="tag tag--paid">paid</span> : <span className="tag">due</span>)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot><tr><td colSpan={solo ? 1 : 2}>Total</td><td className="num">{rupees(booking.totalPaise)}</td></tr></tfoot>
      </table>
      <p className="muted small">Each seat includes the ticket price, a ₹20 convenience fee and 18% GST on the fee.</p>

      {!solo && <h3>{pending ? "Who has paid" : "Everyone's share"}</h3>}
      {!solo && <ul className="shares">
        {booking.shares.map((sh) => (
          <li key={sh.member.userId} className={pending ? (sh.paid ? "share--paid" : "share--due") : ""}>
            <span>{sh.member.name}</span>
            <span className="muted">{sh.seats.length ? sh.seats.join(", ") : "no seat"}</span>
            <strong>{pending ? (sh.paid ? "Paid" : `${rupees(sh.leftPaise)} due`) : rupees(sh.sharePaise)}</strong>
          </li>
        ))}
      </ul>}

      {payMessage && <p className={payMessage.kind === "error" ? "form-error" : "form-notice"} role="status">{payMessage.text}</p>}

      {/* Before payment starts: the organiser chooses how to pay */}
      {booking.youAreLeader && !pending && (
        <div className="seats-panel__actions">
          {solo ? (
            <button className="btn btn--accent" disabled={paying} onClick={() => pay()}>
              {paying ? "Opening payment…" : `Pay ${rupees(booking.totalPaise)}`}
            </button>
          ) : (
            <>
              <button className="btn btn--accent" disabled={paying} onClick={() => pay("SPLIT")}
                title="Everyone pays for their own seat within 10 minutes">
                {paying ? "Opening payment…" : "Split: everyone pays their share"}
              </button>
              <button className="btn btn--ghost" disabled={paying} onClick={() => pay("PAY_FOR_ALL")}>
                Pay for everyone ({rupees(booking.totalPaise)})
              </button>
            </>
          )}
          <button className="btn btn--ghost" disabled={paying} onClick={chooseSeats}>Change seats</button>
          <button className="link-button" disabled={paying} onClick={() => act(() => bookingApi.unlock(group.id))}>Release seats</button>
        </div>
      )}

      {/* Payment window open */}
      {pending && (
        <div className="seats-panel__actions">
          {booking.youOwePaise > 0 && (
            <button className="btn btn--accent" disabled={paying} onClick={() => pay()}>
              {paying ? "Opening payment…" : split ? `Pay your share ${rupees(booking.youOwePaise)}` : `Pay ${rupees(booking.youOwePaise)}`}
            </button>
          )}
          {split && booking.youAreLeader && booking.paidPaise < booking.totalPaise && (
            <button className="btn btn--ghost" disabled={paying} onClick={() => pay("PAY_FOR_ALL")}
              title="Pay for everyone who hasn't paid yet">
              Pay the rest for everyone ({rupees(booking.totalPaise - booking.paidPaise)})
            </button>
          )}
          {split && booking.youOwePaise === 0 && <p className="muted">You've paid. Waiting for the others…</p>}
          {split && booking.youAreLeader && (
            <a className="btn btn--whatsapp btn--small" target="_blank" rel="noopener noreferrer"
              href={`https://wa.me/?text=${encodeURIComponent(`Please pay your Ticktit share for ${group.item.title} in the next ${Math.max(1, Math.ceil((left ?? 0) / 60))} min, or the seats are released: ${group.shareLink}`)}`}>
              Remind on WhatsApp
            </a>
          )}
        </div>
      )}
      {pending && (
        <p className="muted small">
          If the time runs out before everything is paid, the seats are released and everyone who paid is refunded in full automatically.
        </p>
      )}
    </section>
  );
}

/** Seconds left until `deadline`, ticking every second; calls onDone once when it reaches zero. */
function useCountdown(deadline: string | null, onDone: () => void) {
  const [left, setLeft] = useState<number | null>(null);
  useEffect(() => {
    if (!deadline) {
      setLeft(null);
      return;
    }
    const tick = () => {
      const s = Math.max(0, Math.round((new Date(deadline).getTime() - Date.now()) / 1000));
      setLeft(s);
      if (s === 0) onDone();
      return s;
    };
    if (tick() === 0) return;
    const timer = setInterval(() => tick() === 0 && clearInterval(timer), 1000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deadline]);
  return left;
}
