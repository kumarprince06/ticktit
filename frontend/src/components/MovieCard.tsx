import { Link } from "react-router-dom";
import type { MovieCard as Movie } from "../api/catalog";
import { Poster } from "./Poster";

export function MovieCard({ movie }: { movie: Movie }) {
  return (
    <Link to={`/movies/${movie.slug}`} className="movie-card">
      <Poster movie={movie} />
      <div className="movie-card__body">
        <h3 className="movie-card__title">{movie.title}</h3>
        <p className="movie-card__meta">{movie.genres.join(" · ")}</p>
        {movie.status === "COMING_SOON" ? (
          <p className="movie-card__soon">
            In cinemas {new Date(movie.releaseDate).toLocaleDateString("en-IN", { day: "numeric", month: "short" })}
          </p>
        ) : (
          <p className="movie-card__langs">{movie.languages.join(", ")}</p>
        )}
      </div>
    </Link>
  );
}

/** Grey placeholder cards while the list loads, so the page doesn't jump. */
export function MovieCardSkeleton() {
  return (
    <div className="movie-card movie-card--skeleton" aria-hidden="true">
      <div className="poster skeleton" />
      <div className="skeleton skeleton--line" />
      <div className="skeleton skeleton--line skeleton--short" />
    </div>
  );
}
