import { describe, it, expect } from "vitest";
import { canonicalGeometry, validateGeometry } from "../geometry";
import { createSimulation, stressRun } from "../scenarios";
import { readFileSync } from "node:fs";

describe("OpenDesign canonical geometry", () => {
  it("preserves 5 + 4 contours and exact decomposition", () => {
    validateGeometry();
    expect(canonicalGeometry).toHaveLength(9);
    expect(canonicalGeometry.filter(g => g.parts.length > 1)).toHaveLength(4);
    const core = createSimulation();
    expect(core.getSnapshot().rest).toBe(true);
    expect(core.getSnapshot().pairs.every(p => p.overlap === 0 && p.gap > .65)).toBe(true);
    core.stepFixed(600);
    expect(core.getSnapshot().rest).toBe(true);
    expect(core.energy()).toBe(0);
  });
  it("keeps the no-JS SVG coupled to the canonical contour", () => {
    const html = readFileSync(new URL("../index.html", import.meta.url), "utf8");
    const identity = readFileSync(new URL("../identity.svg", import.meta.url), "utf8");
    for (const g of canonicalGeometry) {
      const points = g.vertices.map(p => `${p.x},${p.y}`).join(" ");
      expect(html).toContain(`points="${points}"`);
      expect(identity).toContain(`points="${points}"`);
    }
  });
  it("replays a seed identically", () => {
    const first = stressRun(1), second = stressRun(1);
    expect(first).toEqual(second);
  });
});
