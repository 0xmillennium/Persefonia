import { defineConfig } from "@playwright/test";
import { fileURLToPath } from "node:url";

export default defineConfig({
  testDir: "./tests", testMatch: ["acceptance.spec.ts", "hardening.spec.ts", "stress.spec.ts"], workers: 1, retries: 0,
  timeout: 180_000,
  reporter: [["list"], ["json", { outputFile: fileURLToPath(new URL("../../test-results/segmented-identity/browser-summary.json", import.meta.url)) }]], outputDir: "../../test-results/segmented-identity/browser",
  use: { baseURL: "http://127.0.0.1:4178", viewport: { width: 1200, height: 900 }, trace: "off", screenshot: "only-on-failure" },
  projects: [
    { name: "chromium", use: { browserName: "chromium", channel: "chromium" } },
    { name: "firefox", testIgnore: "stress.spec.ts", use: { browserName: "firefox" } },
    { name: "webkit", testIgnore: "stress.spec.ts", use: { browserName: "webkit" } }
  ],
  webServer: { cwd: fileURLToPath(new URL("../..", import.meta.url)), command: "npx vite --config experiments/segmented-identity/vite.config.ts",
    url: "http://127.0.0.1:4178", reuseExistingServer: process.env.IDENTITY_REUSE_SERVER === "1" }
});
