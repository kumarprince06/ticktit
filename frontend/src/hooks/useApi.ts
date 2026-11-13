import { useEffect, useState } from "react";
import { ApiError } from "../api/client";

type State<T> = { data: T | null; error: ApiError | null; loading: boolean };

/**
 * Loads data when the page opens and again whenever a dependency changes. Ignores answers that arrive
 * after the page has moved on (e.g. the user switched city quickly), so stale data never flashes.
 */
export function useApi<T>(load: () => Promise<T>, deps: unknown[]): State<T> {
  const [state, setState] = useState<State<T>>({ data: null, error: null, loading: true });

  useEffect(() => {
    let current = true;
    setState((s) => ({ ...s, loading: true, error: null }));
    load()
      .then((data) => current && setState({ data, error: null, loading: false }))
      .catch((error) =>
        current &&
        setState({
          data: null,
          error: error instanceof ApiError ? error : new ApiError(0, "UNKNOWN", "Something went wrong."),
          loading: false,
        }),
      );
    return () => {
      current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return state;
}
