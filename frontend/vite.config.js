import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    proxy: {
      "/api":
        loadEnv(mode, process.cwd()).VITE_BACKEND_URL ||
        "http://127.0.0.1:18080",
    },
  },
}));
