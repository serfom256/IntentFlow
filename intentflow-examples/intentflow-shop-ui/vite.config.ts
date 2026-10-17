import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  build: {
    chunkSizeWarningLimit: 700,
  },
  server: {
    port: 5173,
    proxy: {
      "/api": process.env.SHOP_API_URL ?? "http://localhost:8080",
      "/intentflow-admin": process.env.SHOP_API_URL ?? "http://localhost:8080",
    },
  },
});
