import { useEffect, useState, useSyncExternalStore } from "react";
import { catalogApi, type City } from "../api/catalog";

/** The list of cities, fetched once per page load and shared by every component that asks. */
let citiesRequest: Promise<City[]> | null = null;

export function useCities(): City[] {
  const [cities, setCities] = useState<City[]>([]);
  useEffect(() => {
    const request = (citiesRequest ??= catalogApi.cities().catch(() => {
      citiesRequest = null;   // try again next time
      return [];
    }));
    let current = true;
    request.then((list) => current && setCities(list));
    return () => {
      current = false;
    };
  }, []);
  return cities;
}

/** The chosen city's slug, remembered in this browser. A convenience only, so failures are ignored. */
const KEY = "ticktit.city";
const DEFAULT_CITY = "siliguri";
const listeners = new Set<() => void>();

function read(): string {
  try {
    const saved = localStorage.getItem(KEY);
    // earlier versions stored the display name ("Siliguri"); slugs are lower-case
    return saved && saved === saved.toLowerCase() ? saved : DEFAULT_CITY;
  } catch {
    return DEFAULT_CITY;
  }
}

export function useCity(): [string, (citySlug: string) => void] {
  const city = useSyncExternalStore(
    (cb) => {
      listeners.add(cb);
      return () => listeners.delete(cb);
    },
    read,
  );
  const setCity = (next: string) => {
    try {
      localStorage.setItem(KEY, next);
    } catch {
      /* private mode: keep it for this page only */
    }
    listeners.forEach((l) => l());
  };
  return [city, setCity];
}
