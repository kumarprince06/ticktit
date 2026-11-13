import { Link } from "react-router-dom";

export function NotFound() {
  return (
    <div className="container narrow center section">
      <h1>We couldn't find that page</h1>
      <p className="muted">The link may be old, or the movie is no longer showing.</p>
      <Link to="/" className="btn btn--accent">Back to movies</Link>
    </div>
  );
}
