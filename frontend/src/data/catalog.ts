/**
 * Static demo catalogue until the movie module's API exists. All titles, theatres and people are fictional.
 * Posters are drawn from these colours (see <Poster>), so there are no copyrighted images.
 */

export type Movie = {
  slug: string;
  title: string;
  tagline: string;
  genres: string[];
  languages: string[];
  certification: "U" | "UA7" | "UA13" | "UA16" | "A";
  durationMin: number;
  rating: number; // out of 10, from Ticktit users
  votes: number;
  releaseDate: string;
  synopsis: string;
  cast: string[];
  colors: [string, string];
  comingSoon?: boolean;
};

export const CITIES = ["Siliguri", "Kolkata", "Bengaluru", "Mumbai", "Delhi NCR", "Hyderabad"];

export const MOVIES: Movie[] = [
  {
    slug: "monsoon-heist",
    title: "Monsoon Heist",
    tagline: "Seven strangers. One flooded vault.",
    genres: ["Thriller", "Crime"],
    languages: ["Hindi", "English"],
    certification: "UA13",
    durationMin: 142,
    rating: 8.6,
    votes: 24800,
    releaseDate: "2026-10-31",
    synopsis:
      "When the rains shut down the city for three days, a retired locksmith gathers an unlikely crew to empty a bank vault that is slowly filling with water.",
    cast: ["Arnav Sethi", "Mira Kapoor", "Devika Rao"],
    colors: ["#0f4c5c", "#5f0f40"],
  },
  {
    slug: "paper-kites",
    title: "Paper Kites",
    tagline: "Some friendships never land.",
    genres: ["Drama", "Family"],
    languages: ["Bengali", "Hindi"],
    certification: "U",
    durationMin: 128,
    rating: 9.1,
    votes: 11200,
    releaseDate: "2026-11-07",
    synopsis:
      "Three childhood friends reunite on a Kolkata rooftop for the kite festival that once tore them apart.",
    cast: ["Ishaan Bose", "Ritika Sen", "Amol Ghosh"],
    colors: ["#f9844a", "#277da1"],
  },
  {
    slug: "neon-ghats",
    title: "Neon Ghats",
    tagline: "The river remembers everything.",
    genres: ["Mystery", "Sci-Fi"],
    languages: ["Hindi", "Tamil", "Telugu"],
    certification: "UA16",
    durationMin: 151,
    rating: 8.2,
    votes: 18300,
    releaseDate: "2026-10-24",
    synopsis:
      "In a near-future Varanasi lit by holograms, a detective follows a string of disappearances back to the river's edge.",
    cast: ["Kabir Malhotra", "Ananya Iyer"],
    colors: ["#3a0ca3", "#f72585"],
  },
  {
    slug: "the-ninth-over",
    title: "The Ninth Over",
    tagline: "One over to change a life.",
    genres: ["Sports", "Drama"],
    languages: ["Hindi", "English"],
    certification: "U",
    durationMin: 136,
    rating: 8.9,
    votes: 32100,
    releaseDate: "2026-11-01",
    synopsis: "A small-town bowler gets one over in a national final, and a lifetime of doubt to bowl through.",
    cast: ["Rohan Verma", "Sana Qureshi", "Vikram Joshi"],
    colors: ["#2d6a4f", "#d4a017"],
  },
  {
    slug: "saffron-skies",
    title: "Saffron Skies",
    tagline: "Love at thirty thousand feet.",
    genres: ["Romance", "Comedy"],
    languages: ["Hindi"],
    certification: "UA7",
    durationMin: 118,
    rating: 7.8,
    votes: 9400,
    releaseDate: "2026-11-14",
    synopsis: "A pilot and a nervous flyer keep ending up on the same delayed flight, until they stop calling it luck.",
    cast: ["Aditi Nair", "Karan Bedi"],
    colors: ["#ff7b00", "#9d0208"],
  },
  {
    slug: "echoes-of-the-hills",
    title: "Echoes of the Hills",
    tagline: "A song that waited 40 years.",
    genres: ["Musical", "Drama"],
    languages: ["Nepali", "Hindi"],
    certification: "U",
    durationMin: 124,
    rating: 8.4,
    votes: 6200,
    releaseDate: "2026-10-17",
    synopsis: "In the tea gardens near Darjeeling, a young singer finds an unfinished song written by her grandmother.",
    cast: ["Pema Lama", "Suraj Thapa"],
    colors: ["#1b4332", "#74c69d"],
  },
  {
    slug: "glass-tiger",
    title: "Glass Tiger",
    tagline: "Fragile. Fearless.",
    genres: ["Action", "Adventure"],
    languages: ["Hindi", "Tamil", "Telugu", "Kannada"],
    certification: "UA16",
    durationMin: 158,
    rating: 0,
    votes: 0,
    releaseDate: "2026-12-05",
    synopsis: "A stunt double with brittle bones takes on the role of a lifetime, on and off the set.",
    cast: ["Dev Raghavan", "Nisha Pillai"],
    colors: ["#e85d04", "#1d3557"],
    comingSoon: true,
  },
  {
    slug: "small-hours",
    title: "Small Hours",
    tagline: "Nothing good happens after 2 a.m. Except this.",
    genres: ["Comedy"],
    languages: ["English", "Hindi"],
    certification: "UA13",
    durationMin: 109,
    rating: 0,
    votes: 0,
    releaseDate: "2026-12-12",
    synopsis: "Four night-shift workers at a 24-hour pharmacy accidentally become the city's most-wanted.",
    cast: ["Tara Menon", "Yusuf Khan", "Leela D'Souza"],
    colors: ["#22223b", "#9a8c98"],
    comingSoon: true,
  },
];

export type Show = { id: string; time: string; format: string; priceFrom: number; fillingFast?: boolean };
export type Theatre = { id: string; name: string; area: string; shows: Show[] };

/** Same theatres in every city for the demo; real data will come from the API. */
export function theatresFor(movieSlug: string, dayOffset: number): Theatre[] {
  const seed = movieSlug.length + dayOffset;
  const times = ["10:15 AM", "1:30 PM", "4:45 PM", "7:30 PM", "10:40 PM"];
  return [
    { id: "aurora", name: "Aurora Cinemas", area: "City Centre Mall" },
    { id: "lumiere", name: "Lumière Screens", area: "Station Road" },
    { id: "starlight", name: "Starlight Multiplex", area: "Riverside" },
  ].map((t, i) => ({
    ...t,
    shows: times
      .filter((_, j) => (j + i + seed) % 4 !== 0)
      .map((time, j) => ({
        id: `${t.id}-${dayOffset}-${j}`,
        time,
        format: (i + j) % 3 === 0 ? "IMAX 2D" : "2D",
        priceFrom: (i + j) % 3 === 0 ? 320 : 180,
        fillingFast: (j + seed) % 5 === 0,
      })),
  }));
}

export function findMovie(slug: string | undefined) {
  return MOVIES.find((m) => m.slug === slug);
}
