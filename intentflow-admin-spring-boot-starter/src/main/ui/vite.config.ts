import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "./",
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      "/api": {
        target: process.env.INTENTFLOW_API_URL ?? "http://localhost:8080",
        rewrite: (path) => `${process.env.INTENTFLOW_ADMIN_PATH ?? "/intentflow-admin"}${path}`,
      },
    },
  },
});
