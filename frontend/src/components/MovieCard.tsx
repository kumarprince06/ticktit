import { Link } from "react-router-dom";
import type { Movie } from "../data/catalog";
import { Poster } from "./Poster";

export function MovieCard({ movie }: { movie: Movie }) {
  return (
    <Link to={`/movies/${movie.slug}`} className="movie-card">
      <Poster movie={movie} />
      <div className="movie-card__body">
        <h3 className="movie-card__title">{movie.title}</h3>
        <p className="movie-card__meta">{movie.genres.join(" · ")}</p>
        {movie.comingSoon ? (
          <p className="movie-card__soon">
            In cinemas {new Date(movie.releaseDate).toLocaleDateString("en-IN", { day: "numeric", month: "short" })}
          </p>
        ) : (
          <p className="movie-card__rating">
            <span aria-hidden="true">★</span> {movie.rating.toFixed(1)}
            <span className="muted"> · {Intl.NumberFormat("en-IN", { notation: "compact" }).format(movie.votes)} votes</span>
          </p>
        )}
      </div>
    </Link>
  );
}
