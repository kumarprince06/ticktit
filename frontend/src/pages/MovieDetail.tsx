import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { catalogApi, istDate, rupees, showTime, type ShowSummary } from "../api/catalog";
import { Poster, posterColors } from "../components/Poster";
import { useApi } from "../hooks/useApi";
import { useCities, useCity } from "../hooks/useCity";
import { NotFound } from "./NotFound";

const DAYS = 5;
const FILLING_FAST = 0.3;   // under 30% of seats left

export function MovieDetail() {
  const slug = useParams().slug!;
  const [city] = useCity();
  const cityName = useCities().find((c) => c.slug === city)?.name ?? "";
  const [day, setDay] = useState(0);
  const movie = useApi(() => catalogApi.movie(slug), [slug]);

  const dates = Array.from({ length: DAYS }, (_, i) => new Date(Date.now() + i * 86_400_000));
  const shows = useApi(
    () => (movie.data?.status === "NOW_SHOWING" ? catalogApi.shows(slug, city, istDate(dates[day])) : Promise.resolve([])),
    [slug, city, day, movie.data?.status],
  );

  if (movie.error?.code === "NOT_FOUND") return <NotFound />;
  if (movie.error) return <div className="container section"><p className="form-error" role="alert">{movie.error.message}</p></div>;
  if (!movie.data) return <div className="page-loading" aria-busy="true">Loading…</div>;

  const m = movie.data;
  const [a, b] = posterColors(m.slug);
  const hours = `${Math.floor(m.durationMin / 60)}h ${m.durationMin % 60}m`;
  const director = m.cast.find((c) => c.role === "DIRECTOR");
  const actors = m.cast.filter((c) => c.role === "ACTOR");

  return (
    <>
      <section className="movie-hero" style={{ ["--hero-a" as string]: a, ["--hero-b" as string]: b }}>
        <div className="container movie-hero__inner">
          <Poster movie={m} large />
          <div className="movie-hero__info">
            <h1>{m.title}</h1>
            {m.tagline && <p className="movie-hero__tagline">{m.tagline}</p>}
            <ul className="chips">
              {m.languages.map((l) => <li key={l}>{l}</li>)}
              {m.formats.map((f) => <li key={f} className="chip--format">{f.replace("_", " ")}</li>)}
            </ul>
            <p className="movie-hero__meta">
              {hours} · {m.genres.join(", ")} · {m.certification} ·{" "}
              {new Date(m.releaseDate).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })}
            </p>
            <p className="movie-hero__synopsis">{m.synopsis}</p>
            {actors.length > 0 && <p className="muted">Starring {actors.map((c) => c.name).join(", ")}</p>}
            {director && <p className="muted">Directed by {director.name}</p>}
          </div>
        </div>
      </section>

      <div className="container section">
        {m.status === "COMING_SOON" ? (
          <p className="empty">Bookings for {m.title} open closer to release.</p>
        ) : (
          <>
            <h2>Showtimes{cityName && ` in ${cityName}`}</h2>
            <div className="date-tabs" role="tablist" aria-label="Choose a date">
              {dates.map((d, i) => (
                <button key={i} role="tab" aria-selected={day === i}
                  className={`date-tab${day === i ? " date-tab--active" : ""}`} onClick={() => setDay(i)}>
                  <span>{d.toLocaleDateString("en-IN", { weekday: "short", timeZone: "Asia/Kolkata" })}</span>
                  <strong>{d.toLocaleDateString("en-IN", { day: "numeric", timeZone: "Asia/Kolkata" })}</strong>
                  <span>{d.toLocaleDateString("en-IN", { month: "short", timeZone: "Asia/Kolkata" })}</span>
                </button>
              ))}
            </div>

            {shows.error ? (
              <p className="form-error" role="alert">{shows.error.message}</p>
            ) : shows.loading ? (
              <p className="muted" aria-busy="true">Finding shows…</p>
            ) : shows.data?.length ? (
              <>
                <ul className="theatres">
                  {byTheatre(shows.data).map(([theatre, list]) => (
                    <li key={theatre.slug} className="theatre">
                      <div>
                        <h3>{theatre.name}</h3>
                        <p className="muted">{theatre.area}</p>
                      </div>
                      <div className="showtimes">
                        {list.map((s) => {
                          const fast = s.availableSeats / s.totalSeats < FILLING_FAST;
                          const from = Math.min(...Object.values(s.prices));
                          return (
                            <Link key={s.id} to={`/shows/${s.id}/seats`}
                              className={`showtime${fast ? " showtime--fast" : ""}`}
                              title={`${s.screen} · ${s.language} · from ${rupees(from)}${fast ? " · filling fast" : ""}`}>
                              <span>{showTime(s.startsAt)}</span>
                              <small>{s.format}</small>
                            </Link>
                          );
                        })}
                      </div>
                    </li>
                  ))}
                </ul>
                <p className="legend muted"><span className="legend__fast" /> Filling fast</p>
              </>
            ) : (
              <p className="empty">No more shows on this day{cityName && ` in ${cityName}`}. Try another date.</p>
            )}
          </>
        )}
      </div>
    </>
  );
}

function byTheatre(shows: ShowSummary[]) {
  const groups = new Map<string, [ShowSummary["theatre"], ShowSummary[]]>();
  for (const s of shows) {
    const entry = groups.get(s.theatre.slug) ?? [s.theatre, []];
    entry[1].push(s);
    groups.set(s.theatre.slug, entry);
  }
  return [...groups.values()].map(([t, list]) => [t, list.sort((x, y) => x.startsAt.localeCompare(y.startsAt))] as const);
}
