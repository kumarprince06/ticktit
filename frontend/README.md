# Ticktit Web

React 18 + TypeScript + Vite 6. Plain CSS, no UI library. Works on Node 18+.

The movie catalogue is static demo data (`src/data/catalog.ts`, all fictional). Authentication is real and talks to the Spring Boot backend.

## Run it

```bash
# 1. backend services and the API (from the repo root)
docker compose up -d mailpit            # emails land in http://localhost:8025
cd backend && ./mvnw spring-boot:run    # API on http://localhost:8081

# 2. the web app
cd frontend
cp .env.example .env.local              # optional: add VITE_GOOGLE_CLIENT_ID
npm install
npm run dev                             # http://localhost:5173
```

The dev server forwards `/api/*` to the backend, so the browser sees one origin: no CORS setup, and the refresh-token cookie works as-is. If you run the app on a port other than 5173, start the backend with `APP_BASE_URL=http://localhost:<port>` so the links in emails point to it.

**Blank page?** Open the app at **http://localhost:5173**, not by double-clicking `index.html` (a `file:///` address can't load the app) and not at `:8081` (that's the API).

After changing the favicon, browsers may keep showing the old one: hard-refresh with Ctrl+Shift+R or close and reopen the tab.

## What's where

| Path | What it does |
|---|---|
| `src/api/client.ts` | `fetch` wrapper: access token in memory, one silent refresh on 401, refresh serialised across tabs |
| `src/auth/AuthContext.tsx` | Restores the session on page load (refresh cookie, then `GET /me`), sign-in and sign-out |
| `src/auth/RequireAuth.tsx` | Sends signed-out users to sign-in, then back to where they were |
| `src/pages/auth/*` | Sign up, verify email, sign in + OTP, Google, forgot/reset password |
| `src/pages/*` | Home, movie details and showtimes, seat picker (static), account |
| `src/styles.css` | Design tokens at the top, then layout and components |

## Auth flows

- **Sign up** → "check your email" → link opens `/verify-email?token=…` → sign in
- **Sign in** → email + password → 6-digit code from the email → signed in
- **Continue with Google** (shown only when `VITE_GOOGLE_CLIENT_ID` is set) → signed in, no code
- **Forgot password** → code by email → new password → every device signed out

## Scripts

| Command | |
|---|---|
| `npm run dev` | Dev server with hot reload |
| `npm run build` | Type-check and build to `dist/` |
| `npm run typecheck` | Type-check only |
