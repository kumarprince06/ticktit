import { api } from "./client";

/** Mirrors the backend's CatalogDtos. Prices are in paise. */
export type City = { slug: string; name: string; state: string };

export type MovieCard = {
  slug: string;
  title: string;
  tagline: string | null;
  certification: string;
  genres: string[];
  languages: string[];
  durationMin: number;
  releaseDate: string;
  posterUrl: string | null;
  status: "NOW_SHOWING" | "COMING_SOON" | "ENDED";
};

export type CastMember = { name: string; role: "ACTOR" | "DIRECTOR" | "MUSIC"; character: string | null };

export type MovieDetail = MovieCard & {
  synopsis: string;
  formats: string[];
  cast: CastMember[];
  trailerUrl: string | null;
};

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number };

export type ShowSummary = {
  id: number;
  theatre: { slug: string; name: string; area: string };
  screen: string;
  language: string;
  format: string;
  startsAt: string;
  prices: Record<string, number>;
  availableSeats: number;
  totalSeats: number;
};

export type Seat = {
  seatNo: string;
  row: string;
  col: number;
  gridRow: number;
  gridCol: number;
  category: string;
  pricePaise: number;
  status: "AVAILABLE" | "LOCKED" | "BOOKED" | "BLOCKED";
  /** your group holds this seat right now */
  heldByYou: boolean;
};

export type SeatMap = {
  show: {
    id: number;
    movieSlug: string;
    movieTitle: string;
    theatre: string;
    area: string;
    city: string;
    screen: string;
    language: string;
    format: string;
    startsAt: string;
  };
  prices: Record<string, number>;
  seats: Seat[];
};

const qs = (params: Record<string, string | number | undefined>) =>
  new URLSearchParams(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== "") as [string, string][],
  ).toString();

export const catalogApi = {
  cities: () => api<City[]>("/cities"),
  movies: (p: { city?: string; status?: "NOW_SHOWING" | "COMING_SOON"; q?: string; size?: number }) =>
    api<Page<MovieCard>>(`/movies?${qs(p)}`),
  movie: (slug: string) => api<MovieDetail>(`/movies/${encodeURIComponent(slug)}`),
  shows: (movie: string, city: string, date: string) => api<ShowSummary[]>(`/shows?${qs({ movie, city, date })}`),
  seats: (showId: number | string, group?: string) =>
    api<SeatMap>(`/shows/${showId}/seats${group ? `?group=${encodeURIComponent(group)}` : ""}`),
};

/** 18000 paise -> "₹180"; 68720 -> "₹687.20" (paisa always shown with two digits) */
export const rupees = (paise: number) =>
  `₹${(paise / 100).toLocaleString("en-IN", paise % 100 ? { minimumFractionDigits: 2, maximumFractionDigits: 2 } : {})}`;

/** Show times are always shown in IST, whatever the viewer's computer clock is set to. */
export const showTime = (iso: string) =>
  new Date(iso).toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit", timeZone: "Asia/Kolkata" });

/** YYYY-MM-DD of a date in IST, for the shows API. */
export const istDate = (d: Date) => d.toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" });
