import { useSyncExternalStore } from "react";
import { CITIES } from "../data/catalog";

/** The chosen city, remembered in this browser. A convenience only, so failures are ignored. */
const KEY = "ticktit.city";
const listeners = new Set<() => void>();

function read(): string {
  try {
    return localStorage.getItem(KEY) ?? CITIES[0];
  } catch {
    return CITIES[0];
  }
}

export function useCity(): [string, (city: string) => void] {
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
