import { Component, type ErrorInfo, type ReactNode } from "react";

/** If any page crashes, show a way out instead of a blank white screen. */
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Ticktit crashed:", error, info.componentStack);
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className="container narrow center section">
        <h1>Something went wrong</h1>
        <p className="muted">Sorry about that. Reloading the page usually fixes it.</p>
        <button className="btn btn--accent" onClick={() => window.location.reload()}>
          Reload
        </button>
      </div>
    );
  }
}
