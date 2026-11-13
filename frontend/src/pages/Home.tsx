import { Link, useSearchParams } from "react-router-dom";
import { MovieCard } from "../components/MovieCard";
import { MOVIES } from "../data/catalog";
import { useCity } from "../hooks/useCity";

export function Home() {
  const [city] = useCity();
  const [params] = useSearchParams();
  const q = params.get("q")?.toLowerCase() ?? "";
  const matches = (title: string, genres: string[]) =>
    !q || title.toLowerCase().includes(q) || genres.some((g) => g.toLowerCase().includes(q));

  const nowShowing = MOVIES.filter((m) => !m.comingSoon && matches(m.title, m.genres));
  const comingSoon = MOVIES.filter((m) => m.comingSoon && matches(m.title, m.genres));

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
                <a href="#now-showing" className="btn btn--accent">Browse movies in {city}</a>
                <a href="#how-it-works" className="btn btn--ghost">How group booking works</a>
              </div>
            </div>
            <div className="hero__ticket" aria-hidden="true">
              <div className="ticket">
                <span className="ticket__label">Group of 4 · Paper Kites</span>
                <span className="ticket__seats">F7 F8 F9 F10</span>
                <ul className="ticket__people">
                  <li><span className="dot dot--paid" />Asha · paid ₹240</li>
                  <li><span className="dot dot--paid" />Rahul · paid ₹240</li>
                  <li><span className="dot dot--paid" />Sneha · paid ₹240</li>
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
            Results for <strong>“{params.get("q")}”</strong> · <Link to="/">clear</Link>
          </p>
        )}

        <section id="now-showing" className="section">
          <div className="section__head">
            <h2>Now showing in {city}</h2>
          </div>
          {nowShowing.length ? (
            <div className="movie-grid">
              {nowShowing.map((m) => <MovieCard key={m.slug} movie={m} />)}
            </div>
          ) : (
            <p className="empty">No movies match your search.</p>
          )}
        </section>

        {comingSoon.length > 0 && (
          <section className="section">
            <div className="section__head">
              <h2>Coming soon</h2>
            </div>
            <div className="movie-grid">
              {comingSoon.map((m) => <MovieCard key={m.slug} movie={m} />)}
            </div>
          </section>
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
