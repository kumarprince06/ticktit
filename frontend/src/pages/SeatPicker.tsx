import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { catalogApi, rupees, showTime, type Seat } from "../api/catalog";
import { useApi } from "../hooks/useApi";
import { NotFound } from "./NotFound";

const MAX_SEATS = 10;
const CATEGORY_NAMES: Record<string, string> = { RECLINER: "Recliner", PRIME: "Prime", CLASSIC: "Classic" };

/**
 * The real seat map of a show, drawn from each seat's grid position. Selecting seats is local for now:
 * locking them for a group and paying come with the booking module.
 */
export function SeatPicker() {
  const showId = useParams().showId!;
  const map = useApi(() => catalogApi.seats(showId), [showId]);
  const [selected, setSelected] = useState<string[]>([]);
  const [showNote, setShowNote] = useState(false);

  // seats grouped by category, in drawing order, each block with its own grid size
  const blocks = useMemo(() => {
    if (!map.data) return [];
    const order: string[] = [];
    const byCategory = new Map<string, Seat[]>();
    for (const seat of map.data.seats) {
      if (!byCategory.has(seat.category)) order.push(seat.category);
      byCategory.set(seat.category, [...(byCategory.get(seat.category) ?? []), seat]);
    }
    return order.map((category) => {
      const seats = byCategory.get(category)!;
      const top = Math.min(...seats.map((s) => s.gridRow));
      return { category, seats, top, rows: Math.max(...seats.map((s) => s.gridRow)) - top + 1 };
    });
  }, [map.data]);

  if (map.error?.code === "NOT_FOUND") return <NotFound />;
  if (map.error) return <div className="container section"><p className="form-error" role="alert">{map.error.message}</p></div>;
  if (!map.data) return <div className="page-loading" aria-busy="true">Loading seats…</div>;

  const { show, prices, seats } = map.data;
  const columns = Math.max(...seats.map((s) => s.gridCol)) + 1;
  const priceOf = (seatNo: string) => seats.find((s) => s.seatNo === seatNo)!.pricePaise;
  const total = selected.reduce((sum, s) => sum + priceOf(s), 0);
  const toggle = (seatNo: string) =>
    setSelected((cur) =>
      cur.includes(seatNo) ? cur.filter((s) => s !== seatNo) : cur.length < MAX_SEATS ? [...cur, seatNo] : cur,
    );

  return (
    <div className="container seat-page">
      <div className="seat-page__head">
        <Link to={`/movies/${show.movieSlug}`} className="back">← {show.movieTitle}</Link>
        <p className="muted">
          {show.theatre}, {show.area} · {show.screen} · {show.language} {show.format} ·{" "}
          {new Date(show.startsAt).toLocaleDateString("en-IN", { weekday: "short", day: "numeric", month: "short", timeZone: "Asia/Kolkata" })}{" "}
          {showTime(show.startsAt)}
        </p>
      </div>

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
                const taken = s.status !== "AVAILABLE";
                const isSelected = selected.includes(s.seatNo);
                return (
                  <button key={s.seatNo}
                    className={`seat${taken ? " seat--taken" : ""}${isSelected ? " seat--selected" : ""}`}
                    style={{ gridRow: s.gridRow - top + 1, gridColumn: s.gridCol + 2 }}
                    disabled={taken} aria-pressed={isSelected}
                    aria-label={`Seat ${s.seatNo}, ${CATEGORY_NAMES[s.category] ?? s.category}, ${rupees(s.pricePaise)}${taken ? ", not available" : ""}`}
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
        <li><span className="seat seat--sample seat--taken" /> Booked</li>
      </ul>

      {selected.length > 0 && (
        <div className="booking-bar">
          <div className="container booking-bar__inner">
            <div>
              <strong>{selected.length} seat{selected.length > 1 ? "s" : ""}</strong>
              <span className="muted"> · {[...selected].sort().join(", ")}</span>
              <p className="booking-bar__total">{rupees(total)}</p>
            </div>
            <button className="btn btn--accent" onClick={() => setShowNote(true)}>Book together</button>
          </div>
          {showNote && (
            <div className="container">
              <p className="form-notice" role="status">
                Group booking and split payment are coming soon. Seat locking and payments are the next backend module.
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
