import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    include: ["experiments/location-earth-globe/tests/*.test.ts"],
    environment: "node",
    maxWorkers: 2
  }
});
