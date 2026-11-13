import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { bookingApi } from "../api/bookings";
import { catalogApi, rupees, showTime, type Seat, type SeatMap } from "../api/catalog";
import { ApiError } from "../api/client";
import { groupApi } from "../api/groups";
import { NotFound } from "./NotFound";

const MAX_SEATS = 8;
const REFRESH_MS = 2000;   // PRD F3: the seat map follows other people's locks every 2 s
const CATEGORY_NAMES: Record<string, string> = { RECLINER: "Recliner", PRIME: "Prime", CLASSIC: "Classic" };

type Conflict = { message: string; alternative: string[] | null };

/**
 * The live seat map of a show. Two ways in:
 *  - from the movie page: pick seats, then "Book for myself" (a group of one) or "Book together" (start a group);
 *  - from a group (?group=…): the organiser holds seats for the whole group.
 */
export function SeatPicker() {
  const showId = useParams().showId!;
  const [params] = useSearchParams();
  const groupId = params.get("group");
  const solo = params.get("solo") === "1";
  const navigate = useNavigate();

  const [map, setMap] = useState<SeatMap | null>(null);
  const [loadError, setLoadError] = useState<ApiError | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [busy, setBusy] = useState<"solo" | "group" | "hold" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<Conflict | null>(null);
  const preselected = useRef(false);

  useEffect(() => {
    let current = true;
    const load = () =>
      catalogApi.seats(showId, groupId ?? undefined)
        .then((m) => {
          if (!current) return;
          setMap(m);
          setLoadError(null);
          if (!preselected.current) {   // coming back to change seats: start from what the group holds
            preselected.current = true;
            setSelected(m.seats.filter((s) => s.heldByYou).map((s) => s.seatNo));
          }
        })
        .catch((e) => current && setLoadError(e instanceof ApiError ? e : new ApiError(0, "UNKNOWN", "Something went wrong.")));
    load();
    const timer = setInterval(load, REFRESH_MS);
    return () => {
      current = false;
      clearInterval(timer);
    };
  }, [showId, groupId]);

  // a seat someone else just took drops out of the selection
  useEffect(() => {
    if (!map) return;
    const free = new Set(map.seats.filter((s) => s.status === "AVAILABLE" || s.heldByYou).map((s) => s.seatNo));
    setSelected((cur) => (cur.every((s) => free.has(s)) ? cur : cur.filter((s) => free.has(s))));
  }, [map]);

  const blocks = useMemo(() => {
    if (!map) return [];
    const order: string[] = [];
    const byCategory = new Map<string, Seat[]>();
    for (const seat of map.seats) {
      if (!byCategory.has(seat.category)) order.push(seat.category);
      byCategory.set(seat.category, [...(byCategory.get(seat.category) ?? []), seat]);
    }
    return order.map((category) => {
      const seats = byCategory.get(category)!;
      const top = Math.min(...seats.map((s) => s.gridRow));
      return { category, seats, top, rows: Math.max(...seats.map((s) => s.gridRow)) - top + 1 };
    });
  }, [map]);

  if (loadError?.code === "NOT_FOUND") return <NotFound />;
  if (!map) {
    return loadError
      ? <div className="container section"><p className="form-error" role="alert">{loadError.message}</p></div>
      : <div className="page-loading" aria-busy="true">Loading seats…</div>;
  }

  const { show, prices, seats } = map;
  const columns = Math.max(...seats.map((s) => s.gridCol)) + 1;
  const priceOf = (seatNo: string) => seats.find((s) => s.seatNo === seatNo)?.pricePaise ?? 0;
  const total = selected.reduce((sum, s) => sum + priceOf(s), 0);
  const toggle = (seatNo: string) => {
    setConflict(null);
    setSelected((cur) =>
      cur.includes(seatNo) ? cur.filter((s) => s !== seatNo) : cur.length < MAX_SEATS ? [...cur, seatNo] : cur,
    );
  };

  /** Hold the seats for a group, then go to the group page. A conflict offers the nearest free block instead. */
  const hold = async (forGroup: string, seatNos: string[], kind: "solo" | "hold") => {
    setBusy(kind);
    setError(null);
    setConflict(null);
    try {
      await bookingApi.lock(forGroup, seatNos);
      navigate(`/groups/${forGroup}`);
    } catch (e) {
      if (e instanceof ApiError && e.code === "SEATS_UNAVAILABLE") {
        const taken = (e.body?.unavailable as string[] | undefined) ?? [];
        setConflict({
          message: `${taken.join(", ") || "Some seats"} ${taken.length === 1 ? "was" : "were"} just taken.`,
          alternative: (e.body?.alternative as string[] | null | undefined) ?? null,
        });
        if (kind === "solo") navigate(`/shows/${showId}/seats?group=${forGroup}&solo=1`, { replace: true });   // the booking exists now
      } else {
        setError(e instanceof ApiError ? e.message : "Something went wrong. Please try again.");
      }
      setBusy(null);
    }
  };

  /** "Book for myself": a group of one that holds the seats now and pays for all of them. */
  const bookForMyself = async () => {
    setBusy("solo");
    setError(null);
    try {
      const group = await groupApi.create(show.id, true);
      await hold(group.groupId, selected, "solo");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Something went wrong. Please try again.");
      setBusy(null);
    }
  };

  /** "Book together": group first, seats later (PRD journey 1). */
  const startGroup = async () => {
    setBusy("group");
    setError(null);
    try {
      const group = await groupApi.create(show.id);
      navigate(`/groups/${group.groupId}`);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Couldn't create the group. Please try again.");
      setBusy(null);
    }
  };

  return (
    <div className="container seat-page">
      <div className="seat-page__head">
        <Link to={groupId ? `/groups/${groupId}` : `/movies/${show.movieSlug}`} className="back">
          ← {groupId ? (solo ? "Back to your booking" : "Back to your group") : show.movieTitle}
        </Link>
        <p className="muted">
          {show.theatre}, {show.area} · {show.screen} · {show.language} {show.format} ·{" "}
          {new Date(show.startsAt).toLocaleDateString("en-IN", { weekday: "short", day: "numeric", month: "short", timeZone: "Asia/Kolkata" })}{" "}
          {showTime(show.startsAt)}
        </p>
      </div>
      {groupId && (
        <p className="form-notice">
          {solo ? "Choose your seats." : "Choose seats for your whole group."} They'll be held for 5 minutes.
        </p>
      )}

      <div className="seat-map" role="group" aria-label="Seat map">
        {blocks.map(({ category, seats: blockSeats, top, rows }) => (
          <section key={category} className="seat-block">
            <h2 className="seat-block__title">{CATEGORY_NAMES[category] ?? category} · {rupees(prices[category])}</h2>
            <div className="seat-grid"
              style={{ gridTemplateColumns: `24px repeat(${columns}, var(--seat))`, gridTemplateRows: `repeat(${rows}, var(--seat))` }}>
              {[...new Set(blockSeats.map((s) => s.row))].map((row) => {
                const r = blockSeats.find((s) => s.row === row)!.gridRow - top + 1;
                return <span key={row} className="seat-row__label" style={{ gridRow: r, gridColumn: 1 }} aria-hidden="true">{row}</span>;
              })}
              {blockSeats.map((s) => {
                const free = s.status === "AVAILABLE" || s.heldByYou;
                const isSelected = selected.includes(s.seatNo);
                const state = s.status === "LOCKED" && !s.heldByYou ? "being booked by another group" : !free ? "sold" : "";
                return (
                  <button key={s.seatNo}
                    className={`seat${!free ? (s.status === "LOCKED" ? " seat--locked" : " seat--taken") : ""}${isSelected ? " seat--selected" : ""}`}
                    style={{ gridRow: s.gridRow - top + 1, gridColumn: s.gridCol + 2 }}
                    disabled={!free} aria-pressed={isSelected}
                    aria-label={`Seat ${s.seatNo}, ${CATEGORY_NAMES[s.category] ?? s.category}, ${rupees(s.pricePaise)}${state && `, ${state}`}`}
                    onClick={() => toggle(s.seatNo)}>
                    {s.col}
                  </button>
                );
              })}
            </div>
          </section>
        ))}
        <div className="screen" aria-hidden="true">Screen this way</div>
      </div>

      <ul className="seat-legend" aria-label="Legend">
        <li><span className="seat seat--sample" /> Available</li>
        <li><span className="seat seat--sample seat--selected" /> Selected</li>
        <li><span className="seat seat--sample seat--locked" /> Being booked</li>
        <li><span className="seat seat--sample seat--taken" /> Sold</li>
      </ul>

      {(selected.length > 0 || conflict) && (
        <div className="booking-bar">
          <div className="container booking-bar__inner">
            <div>
              <strong>{selected.length} seat{selected.length === 1 ? "" : "s"}</strong>
              {selected.length > 0 && <span className="muted"> · {[...selected].sort().join(", ")}</span>}
              <p className="booking-bar__total">{rupees(total)} <small className="muted">+ ₹23.60 fee per seat</small></p>
            </div>
            <div className="booking-bar__actions">
              {groupId ? (
                <button className="btn btn--accent" disabled={!selected.length || busy !== null} onClick={() => hold(groupId, selected, "hold")}>
                  {busy === "hold" ? "Holding…" : `Hold ${selected.length} seat${selected.length === 1 ? "" : "s"}${solo ? "" : " for the group"}`}
                </button>
              ) : (
                <>
                  <button className="btn btn--accent" disabled={!selected.length || busy !== null} onClick={bookForMyself}
                    title="Hold these seats now and pay for all of them yourself">
                    {busy === "solo" ? "Holding…" : "Book for myself"}
                  </button>
                  {selected.length > 1 && (
                    <button className="btn btn--ghost" disabled={busy !== null} onClick={startGroup}
                      title="Start a group; friends join and each pays for their own seat">
                      {busy === "group" ? "Creating group…" : "Book together"}
                    </button>
                  )}
                </>
              )}
            </div>
          </div>
          {conflict && (
            <div className="container">
              <div className="form-error conflict" role="alert">
                <span>{conflict.message} {conflict.alternative ? `${conflict.alternative.join(", ")} are free together.` : "Please pick other seats."}</span>
                {conflict.alternative && groupId && (
                  <button className="btn btn--small btn--accent" disabled={busy !== null}
                    onClick={() => { setSelected(conflict.alternative!); hold(groupId, conflict.alternative!, "hold"); }}>
                    Hold {conflict.alternative.join(", ")} instead
                  </button>
                )}
              </div>
            </div>
          )}
          {error && <div className="container"><p className="form-error" role="alert">{error}</p></div>}
        </div>
      )}
    </div>
  );
}
