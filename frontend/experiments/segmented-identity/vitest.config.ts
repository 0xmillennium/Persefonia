import { defineConfig } from "vitest/config";

export default defineConfig({
  test: { include: ["experiments/segmented-identity/tests/*.test.ts"],
    environment: "node", maxWorkers: 2, testTimeout: 180_000, reporters: ["verbose"] }
});
