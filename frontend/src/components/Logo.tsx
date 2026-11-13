/**
 * Ticktit mark (same drawing as the favicon): an amber ticket stub with a tick, as in "booked, paid, done".
 * `onDark` for the dark header, default for white cards. `size` is the height; the ticket is 1.5x as wide.
 */
export function Logo({ size = 28, onDark = false }: { size?: number; onDark?: boolean }) {
  return (
    <span className={`logo-mark${onDark ? " logo-mark--on-dark" : ""}`}>
      <svg viewBox="3 13 58 38" width={Math.round(size * 1.5)} height={size} aria-hidden="true">
        <path
          d="M10 14h44a6 6 0 0 1 6 6v6a6 6 0 0 0 0 12v6a6 6 0 0 1-6 6H10a6 6 0 0 1-6-6v-6a6 6 0 0 0 0-12v-6a6 6 0 0 1 6-6z"
          fill="#ffb020"
        />
        <path d="M21 19v26" stroke="#16122b" strokeWidth="2.5" strokeLinecap="round" strokeDasharray="1 5" />
        <path d="M29 33l7 7 13-15" fill="none" stroke="#16122b" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span className="logo-mark__word">Ticktit</span>
    </span>
  );
}
