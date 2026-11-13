import { useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { findMovie } from "../data/catalog";
import { NotFound } from "./NotFound";

/** Static seat map. Locking seats, groups and split payment come with the booking module. */
const LAYOUT = [
  { category: "Recliner", price: 450, rows: ["A"], seats: 10 },
  { category: "Prime", price: 280, rows: ["B", "C", "D", "E"], seats: 14 },
  { category: "Classic", price: 180, rows: ["F", "G", "H", "J", "K"], seats: 16 },
];
const MAX_SEATS = 10;

export function SeatPicker() {
  const movie = findMovie(useParams().slug);
  const [params] = useSearchParams();
  const [selected, setSelected] = useState<string[]>([]);
  const [showNote, setShowNote] = useState(false);

  // a stable pretend "already booked" pattern per show
  const booked = useMemo(() => {
    const seed = (params.get("show") ?? "").length;
    const set = new Set<string>();
    LAYOUT.forEach(({ rows, seats }) =>
      rows.forEach((r, ri) => {
        for (let n = 1; n <= seats; n++) if ((n * 7 + ri * 3 + seed) % 9 === 0) set.add(`${r}${n}`);
      }),
    );
    return set;
  }, [params]);

  if (!movie) return <NotFound />;

  const priceOf = (seat: string) => LAYOUT.find((l) => l.rows.includes(seat[0]))!.price;
  const total = selected.reduce((sum, s) => sum + priceOf(s), 0);
  const toggle = (seat: string) =>
    setSelected((cur) =>
      cur.includes(seat) ? cur.filter((s) => s !== seat) : cur.length < MAX_SEATS ? [...cur, seat] : cur,
    );

  return (
    <div className="container seat-page">
      <div className="seat-page__head">
        <Link to={`/movies/${movie.slug}`} className="back">← {movie.title}</Link>
        <p className="muted">{params.get("theatre")} · {params.get("time")}</p>
      </div>

      <div className="seat-map" role="group" aria-label="Seat map">
        {LAYOUT.map(({ category, price, rows, seats }) => (
          <section key={category} className="seat-block">
            <h2 className="seat-block__title">{category} · ₹{price}</h2>
            {rows.map((row) => (
              <div key={row} className="seat-row">
                <span className="seat-row__label" aria-hidden="true">{row}</span>
                {Array.from({ length: seats }, (_, i) => {
                  const id = `${row}${i + 1}`;
                  const taken = booked.has(id);
                  const isSelected = selected.includes(id);
                  return (
                    <button
                      key={id}
                      className={`seat${taken ? " seat--taken" : ""}${isSelected ? " seat--selected" : ""}${
                        i === Math.floor(seats / 2) - 1 ? " seat--aisle" : ""
                      }`}
                      disabled={taken}
                      aria-pressed={isSelected}
                      aria-label={`Seat ${id}, ${category}, ₹${price}${taken ? ", booked" : ""}`}
                      onClick={() => toggle(id)}
                    >
                      {i + 1}
                    </button>
                  );
                })}
              </div>
            ))}
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
              <p className="booking-bar__total">₹{total.toLocaleString("en-IN")}</p>
            </div>
            <button className="btn btn--accent" onClick={() => setShowNote(true)}>
              Book together
            </button>
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
