import { defineConfig } from "vitest/config";
export default defineConfig({ test: { include: ["experiments/scroll-film-navigator/tests/*.test.ts"], environment: "node", maxWorkers: 2, reporters: ["verbose"] } });
