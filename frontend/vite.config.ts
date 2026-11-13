import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

// Dev server on 5173. Calls to /api are forwarded to the Spring Boot backend, so the browser sees one origin:
// no CORS setup needed and the refresh-token cookie just works.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        "/api": {
          target: env.VITE_API_TARGET || "http://localhost:8081",
          changeOrigin: true,
          // Through the proxy the call is same-origin, so drop the browser's Origin header. Otherwise the backend
          // runs its CORS check and refuses any address except http://localhost:5173 (e.g. 127.0.0.1 or another port).
          configure: (proxy) => proxy.on("proxyReq", (req) => req.removeHeader("origin")),
        },
      },
    },
  };
});
