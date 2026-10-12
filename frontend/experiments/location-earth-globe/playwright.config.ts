import { defineConfig } from "@playwright/test";
import { fileURLToPath } from "node:url";

const built = process.env.GLOBE_BUILT === "1";
const software = process.env.GLOBE_SOFTWARE_GPU === "1";
const launchOptions = software ? { args: ["--use-angle=swiftshader"] } : {};
export default defineConfig({
  testDir: "./tests", testMatch: "*.spec.ts", workers: 1, retries: 0, timeout: 30_000,
  outputDir: `../../test-results/location-earth-globe/${built ? "built" : "browser"}`,
  reporter: [["list"], ["json", { outputFile: fileURLToPath(new URL(`../../test-results/location-earth-globe/${built ? "built" : "browser"}-summary.json`, import.meta.url)) }]],
  use: { baseURL: built ? "http://127.0.0.1:4281" : "http://127.0.0.1:4280", viewport: { width: 1000, height: 1050 }, trace: "retain-on-failure", screenshot: "only-on-failure" },
  projects: [
    { name: "chromium", use: { browserName: "chromium", channel: "chromium", launchOptions } },
    { name: "firefox", use: { browserName: "firefox" } },
    { name: "chromium-touch", use: { browserName: "chromium", channel: "chromium", launchOptions, viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true } },
    { name: "webkit", use: { browserName: "webkit" } }
  ],
  webServer: {
    cwd: fileURLToPath(new URL("../..", import.meta.url)),
    command: built ? "npm run globe:preview" : "npm run globe:dev",
    url: built ? "http://127.0.0.1:4281" : "http://127.0.0.1:4280",
    reuseExistingServer: process.env.GLOBE_REUSE_SERVER === "1", timeout: 30_000
  }
});
