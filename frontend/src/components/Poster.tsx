import type { Movie } from "../data/catalog";

/** A drawn poster: gradient, film-strip edge and the title. Stands in for real artwork. */
export function Poster({ movie, large = false }: { movie: Movie; large?: boolean }) {
  const [a, b] = movie.colors;
  return (
    <div
      className={`poster${large ? " poster--large" : ""}`}
      style={{ background: `linear-gradient(160deg, ${a} 0%, ${b} 100%)` }}
      role="img"
      aria-label={`${movie.title} poster`}
    >
      <span className="poster__strip" aria-hidden="true" />
      <span className="poster__cert">{movie.certification}</span>
      <span className="poster__title">{movie.title}</span>
      <span className="poster__tagline">{movie.tagline}</span>
    </div>
  );
}
