import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Poster } from "../components/Poster";
import { findMovie, theatresFor } from "../data/catalog";
import { useCity } from "../hooks/useCity";
import { NotFound } from "./NotFound";

const DAYS = 5;

export function MovieDetail() {
  const movie = findMovie(useParams().slug);
  const [city] = useCity();
  const [day, setDay] = useState(0);
  if (!movie) return <NotFound />;

  const hours = `${Math.floor(movie.durationMin / 60)}h ${movie.durationMin % 60}m`;
  const dates = Array.from({ length: DAYS }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() + i);
    return d;
  });

  return (
    <>
      <section className="movie-hero" style={{ ["--hero-a" as string]: movie.colors[0], ["--hero-b" as string]: movie.colors[1] }}>
        <div className="container movie-hero__inner">
          <Poster movie={movie} large />
          <div className="movie-hero__info">
            <h1>{movie.title}</h1>
            {!movie.comingSoon && (
              <p className="movie-hero__rating">
                <span aria-hidden="true">★</span> {movie.rating.toFixed(1)}/10
                <span> · {movie.votes.toLocaleString("en-IN")} votes</span>
              </p>
            )}
            <ul className="chips">
              {movie.languages.map((l) => <li key={l}>{l}</li>)}
            </ul>
            <p className="movie-hero__meta">
              {hours} · {movie.genres.join(", ")} · {movie.certification} ·{" "}
              {new Date(movie.releaseDate).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })}
            </p>
            <p className="movie-hero__synopsis">{movie.synopsis}</p>
            <p className="muted">Cast: {movie.cast.join(", ")}</p>
          </div>
        </div>
      </section>

      <div className="container section">
        {movie.comingSoon ? (
          <p className="empty">Bookings for {movie.title} open closer to release.</p>
        ) : (
          <>
            <h2>Showtimes in {city}</h2>
            <div className="date-tabs" role="tablist" aria-label="Choose a date">
              {dates.map((d, i) => (
                <button
                  key={i}
                  role="tab"
                  aria-selected={day === i}
                  className={`date-tab${day === i ? " date-tab--active" : ""}`}
                  onClick={() => setDay(i)}
                >
                  <span>{d.toLocaleDateString("en-IN", { weekday: "short" })}</span>
                  <strong>{d.getDate()}</strong>
                  <span>{d.toLocaleDateString("en-IN", { month: "short" })}</span>
                </button>
              ))}
            </div>

            <ul className="theatres">
              {theatresFor(movie.slug, day).map((t) => (
                <li key={t.id} className="theatre">
                  <div>
                    <h3>{t.name}</h3>
                    <p className="muted">{t.area}, {city}</p>
                  </div>
                  <div className="showtimes">
                    {t.shows.map((s) => (
                      <Link
                        key={s.id}
                        to={`/movies/${movie.slug}/seats?show=${s.id}&time=${encodeURIComponent(s.time)}&theatre=${encodeURIComponent(t.name)}`}
                        className={`showtime${s.fillingFast ? " showtime--fast" : ""}`}
                        title={s.fillingFast ? "Filling fast" : undefined}
                      >
                        <span>{s.time}</span>
                        <small>{s.format}</small>
                      </Link>
                    ))}
                  </div>
                </li>
              ))}
            </ul>
            <p className="legend muted"><span className="legend__fast" /> Filling fast</p>
          </>
        )}
      </div>
    </>
  );
}
