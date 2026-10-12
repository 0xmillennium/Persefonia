import { defineConfig } from "@playwright/test";
import functional from "./playwright.config";
export default defineConfig({
  ...functional,testMatch:process.env.FILM_PERF_SOAK==="1"?"performance.spec.ts":"performance-smoke.spec.ts",
  outputDir:"../../test-results/scroll-film-navigator/performance-browser",
  projects:functional.projects?.filter(project=>["chromium","chromium-touch"].includes(project.name!))
});
