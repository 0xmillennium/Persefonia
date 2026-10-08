import { defineConfig } from "@playwright/test";
import { fileURLToPath } from "node:url";

export default defineConfig({
  testDir: "./tests", testMatch: ["built.spec.ts", "history.spec.ts"], workers: 1, retries: 0,
  reporter: [["list"]], outputDir: "../../test-results/segmented-identity/built",
  use: { baseURL: "http://127.0.0.1:4179", browserName: "chromium", channel: "chromium", launchOptions: { ignoreDefaultArgs: ["--disable-back-forward-cache"] } },
  webServer: { cwd: fileURLToPath(new URL("../..", import.meta.url)),
    command: "npm run identity:build && npx vite preview --config experiments/segmented-identity/vite.config.ts --port 4179",
    url: "http://127.0.0.1:4179", reuseExistingServer: false }
});
