import { defineConfig } from "@playwright/test";
import { fileURLToPath } from "node:url";

const built = process.env.EFFECTS_BUILT === "1";
export default defineConfig({
  testDir: "./tests", testMatch: "*.spec.ts", workers: 1, retries: 0, timeout: 30_000,
  outputDir: "../../test-results/identity-effects/browser",
  reporter: [["list"]],
  use: { baseURL: `http://127.0.0.1:${built ? 4285 : 4284}`,
    viewport: { width: 1280, height: 800 }, screenshot: "only-on-failure" },
  projects: [
    { name: "chromium", use: { browserName: "chromium", channel: "chromium" } },
    { name: "firefox", use: { browserName: "firefox" } },
    { name: "webkit", use: { browserName: "webkit" } }
  ],
  webServer: {
    cwd: fileURLToPath(new URL("../..", import.meta.url)),
    command: built ? "npm run effects:preview" : "npm run effects:dev",
    url: `http://127.0.0.1:${built ? 4285 : 4284}`, reuseExistingServer: process.env.EFFECTS_REUSE_SERVER === "1"
  }
});
