import { defineConfig } from "vite";
import { fileURLToPath } from "node:url";

export default defineConfig({
  root: fileURLToPath(new URL(".", import.meta.url)),
  server: { host: "127.0.0.1", port: 4178, strictPort: true },
  preview: { host: "127.0.0.1", port: 4178, strictPort: true },
  build: { outDir: "../../dist/segmented-identity", emptyOutDir: true }
});
