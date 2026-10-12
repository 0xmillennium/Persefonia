import { describe, expect, it } from "vitest";
import { GlobeEntrance } from "../src/entrance";

describe("finite first-visible entrance", () => {
  it("reveals the surface, scans the marker, then emits one fading pulse", () => {
    const entrance = new GlobeEntrance();
    expect(entrance.started).toBe(false);
    entrance.step(0.575, false);
    expect(entrance.reveal).toBeCloseTo(0.5);
    expect(entrance.phase).toBe("scan");
    entrance.step(1.225, false);
    expect(entrance.reveal).toBe(1);
    expect(entrance.phase).toBe("pulse");
    entrance.step(2, false);
    expect(entrance.phase).toBe("settled");
    expect(entrance.moving).toBe(false);
    entrance.step(30, false);
    expect(entrance.elapsed).toBe(GlobeEntrance.duration);
  });
  it("uses active time consistently, including irregular frames", () => {
    const regular = new GlobeEntrance(), irregular = new GlobeEntrance();
    for (let i = 0; i < 120; i++) regular.step(1 / 60, false);
    irregular.step(0.5, false); irregular.step(1.5, false);
    expect(regular.elapsed).toBeCloseTo(irregular.elapsed);
    expect(regular.phase).toBe(irregular.phase);
  });
  it("settles immediately on interaction or reduced motion and never replays", () => {
    for (const reduced of [false, true]) {
      const entrance = new GlobeEntrance();
      entrance.step(0.2, reduced);
      if (!reduced) entrance.finish();
      entrance.step(0.1, false);
      expect(entrance.reveal).toBe(1);
      expect(entrance.phase).toBe("settled");
      expect(entrance.moving).toBe(false);
    }
  });
});
