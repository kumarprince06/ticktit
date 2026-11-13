import { Link, useSearchParams } from "react-router-dom";
import { catalogApi, type MovieCard as Movie } from "../api/catalog";
import { MovieCard, MovieCardSkeleton } from "../components/MovieCard";
import { useApi } from "../hooks/useApi";
import { useCities, useCity } from "../hooks/useCity";

export function Home() {
  const [city] = useCity();
  const cityName = useCities().find((c) => c.slug === city)?.name ?? "";
  const q = useSearchParams()[0].get("q") ?? "";

  const nowShowing = useApi(() => catalogApi.movies({ city, status: "NOW_SHOWING", q, size: 24 }), [city, q]);
  const comingSoon = useApi(() => catalogApi.movies({ status: "COMING_SOON", q, size: 12 }), [q]);

  return (
    <>
      {!q && (
        <section className="hero">
          <div className="container hero__inner">
            <div className="hero__text">
              <p className="eyebrow">Movies with your people</p>
              <h1>Pick the seats together. Everyone pays their own share.</h1>
              <p className="hero__lead">
                Start a group, share one link, and friends join and pay for their own seat. No chasing anyone for
                money after the show.
              </p>
              <div className="hero__actions">
                <a href="#now-showing" className="btn btn--accent">Browse movies{cityName && ` in ${cityName}`}</a>
                <a href="#how-it-works" className="btn btn--ghost">How group booking works</a>
              </div>
            </div>
            <div className="hero__ticket" aria-hidden="true">
              <div className="ticket">
                <span className="ticket__label">Group of 4 · Paper Kites</span>
                <span className="ticket__seats">F7 F8 F9 F10</span>
                <ul className="ticket__people">
                  <li><span className="dot dot--paid" />Asha · paid ₹180</li>
                  <li><span className="dot dot--paid" />Rahul · paid ₹180</li>
                  <li><span className="dot dot--paid" />Sneha · paid ₹180</li>
                  <li><span className="dot" />Kabir · paying…</li>
                </ul>
              </div>
            </div>
          </div>
        </section>
      )}

      <div className="container">
        {q && (
          <p className="search-summary">
            Results for <strong>“{q}”</strong> · <Link to="/">clear</Link>
          </p>
        )}

        <MovieSection id="now-showing" title={`Now showing${cityName ? ` in ${cityName}` : ""}`} state={nowShowing}
          empty={q ? "No movies match your search." : "No shows in this city right now. Try another city."} />

        {(comingSoon.loading || (comingSoon.data?.content.length ?? 0) > 0) && (
          <MovieSection title="Coming soon" state={comingSoon} empty="" />
        )}

        {!q && (
          <section id="how-it-works" className="section steps">
            <h2>How group booking works</h2>
            <ol className="steps__list">
              <li>
                <span className="steps__num">1</span>
                <h3>Choose a show</h3>
                <p>Pick the movie, cinema and time, then hold seats for your whole group.</p>
              </li>
              <li>
                <span className="steps__num">2</span>
                <h3>Share one link</h3>
                <p>Send it on WhatsApp. Friends open it, see the seats, and join in a tap.</p>
              </li>
              <li>
                <span className="steps__num">3</span>
                <h3>Everyone pays their share</h3>
                <p>Each person pays for their own seat. Tickets are confirmed once the group is paid.</p>
              </li>
            </ol>
          </section>
        )}
      </div>
    </>
  );
}

function MovieSection({ id, title, state, empty }: {
  id?: string;
  title: string;
  state: ReturnType<typeof useApi<{ content: Movie[] }>>;
  empty: string;
}) {
  return (
    <section id={id} className="section">
      <div className="section__head">
        <h2>{title}</h2>
      </div>
      {state.error ? (
        <p className="form-error" role="alert">{state.error.message}</p>
      ) : state.loading ? (
        <div className="movie-grid">{Array.from({ length: 5 }, (_, i) => <MovieCardSkeleton key={i} />)}</div>
      ) : state.data?.content.length ? (
        <div className="movie-grid">{state.data.content.map((m) => <MovieCard key={m.slug} movie={m} />)}</div>
      ) : (
        <p className="empty">{empty}</p>
      )}
    </section>
  );
}
