import type { MovieCard } from "../api/catalog";

/** Colour pairs for drawn posters. A movie always gets the same pair (picked from its slug). */
const PALETTE: [string, string][] = [
  ["#0f4c5c", "#5f0f40"],
  ["#f9844a", "#277da1"],
  ["#3a0ca3", "#f72585"],
  ["#2d6a4f", "#d4a017"],
  ["#ff7b00", "#9d0208"],
  ["#1b4332", "#74c69d"],
  ["#e85d04", "#1d3557"],
  ["#22223b", "#9a8c98"],
  ["#5a189a", "#ff9e00"],
  ["#003049", "#d62828"],
];

export function posterColors(slug: string): [string, string] {
  let hash = 0;
  for (const ch of slug) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return PALETTE[hash % PALETTE.length];
}

/** The real poster when the movie has one; otherwise a drawn one: gradient, film-strip edge and the title. */
export function Poster({ movie, large = false }: { movie: MovieCard; large?: boolean }) {
  const className = `poster${large ? " poster--large" : ""}`;
  if (movie.posterUrl) {
    return <img className={className} src={movie.posterUrl} alt={`${movie.title} poster`} loading="lazy" />;
  }
  const [a, b] = posterColors(movie.slug);
  return (
    <div className={className} style={{ background: `linear-gradient(160deg, ${a} 0%, ${b} 100%)` }}
      role="img" aria-label={`${movie.title} poster`}>
      <span className="poster__strip" aria-hidden="true" />
      <span className="poster__cert">{movie.certification}</span>
      <span className="poster__title">{movie.title}</span>
      {movie.tagline && <span className="poster__tagline">{movie.tagline}</span>}
    </div>
  );
}
