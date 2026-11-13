/**
 * Talks to the Spring Boot API. The access token lives only in memory (never localStorage, so a malicious
 * script can't read it after the page closes). The refresh token is an HttpOnly cookie the browser sends itself.
 */

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public errors?: Record<string, string>,
    public requestId?: string,
    /** The whole error body, for extra fields like "unavailable" / "alternative" on a seat conflict. */
    public body?: Record<string, unknown>,
  ) {
    super(message);
  }
}

let accessToken: string | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

let refreshing: Promise<string | null> | null = null;

/**
 * Gets a new access token using the refresh cookie. Only one refresh runs at a time: in this tab via the
 * shared promise, and across tabs via a Web Lock. Two refreshes with the same cookie would look like a
 * stolen token to the backend, which then signs the user out everywhere.
 */
export function refreshAccessToken(): Promise<string | null> {
  if (!refreshing) {
    const doRefresh = () =>
      fetch("/api/v1/auth/refresh", { method: "POST", credentials: "include" })
        .then(async (res) => (res.ok ? ((await res.json()).accessToken as string) : null))
        .catch(() => null);
    const locked = navigator.locks
      ? navigator.locks.request("ticktit-refresh", doRefresh)
      : doRefresh();
    refreshing = locked
      .then((token) => {
        accessToken = token;
        return token;
      })
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

type Options = { method?: string; body?: unknown; auth?: boolean; headers?: Record<string, string> };

/** Calls /api/v1{path}. With auth: true it sends the access token and, on a 401, refreshes once and retries. */
export async function api<T = void>(path: string, { method = "GET", body, auth = false, headers = {} }: Options = {}): Promise<T> {
  const send = () =>
    fetch(`/api/v1${path}`, {
      method,
      credentials: "include",
      headers: {
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(auth && accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });

  let res: Response;
  try {
    res = await send();
    if (res.status === 401 && auth && (await refreshAccessToken())) {
      res = await send();
    }
  } catch {
    throw new ApiError(0, "NETWORK", "Can't reach Ticktit right now. Check your connection and try again.");
  }

  if (!res.ok) {
    const problem = await res.json().catch(() => ({}));
    throw new ApiError(
      res.status,
      problem.code ?? "UNKNOWN",
      problem.detail ?? "Something went wrong. Please try again.",
      problem.errors,
      problem.requestId,
      problem,
    );
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
