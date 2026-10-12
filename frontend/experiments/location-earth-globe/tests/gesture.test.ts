import { describe, expect, it } from "vitest";
import { GlobeGesture } from "../src/gesture";
import { GlobeOrientation } from "../src/orientation";

const sample = (x: number, y: number, time: number, touch = false, id = 1) => ({ x, y, time, touch, id });
describe("single input owner and recent momentum", () => {
  it("ignores extra pointers and presses during a fling without jumping", () => {
    const o = new GlobeOrientation(), g = new GlobeGesture(o); o.release(5);
    const spin = o.spin; expect(g.press(sample(0, 0, 0), 300)).toBe(true);
    expect(o.spin).toBe(spin); expect(o.angularSpeed).toBe(0);
    expect(g.press(sample(40, 0, 20, false, 2), 300)).toBe(false);
    expect(g.move(sample(200, 0, 30, false, 2))).toBe("ignored");
    expect(g.release(2, 40).handled).toBe(false); expect(g.owner).toBe(1);
  });
  it("distinguishes fast, slow, stationary, held and vertical-only release", () => {
    for (const [dx, dy, releaseAt, fling] of [[100, 0, 40, true], [-100, 0, 40, true],
      [6, 0, 1000, false], [100, 0, 300, false], [0, 100, 40, false], [0, 0, 40, false]] as const) {
      const o = new GlobeOrientation(), g = new GlobeGesture(o);
      g.press(sample(0, 0, 0), 300); g.move(sample(dx, dy, 30)); g.release(1, releaseAt);
      expect(Math.abs(o.excessSpeed) > 0).toBe(fling);
      if (fling) expect(Math.sign(o.angularSpeed)).toBe(Math.sign(dx));
      expect(o.pressed).toBe(false);
    }
  });
  it("clears cancellation without fling and retains angles", () => {
    const o = new GlobeOrientation(), g = new GlobeGesture(o);
    g.press(sample(0, 0, 0), 300); g.move(sample(200, 100, 40));
    const { spin, pitch } = o; g.cancel(); g.cancel();
    expect(o.spin).toBe(spin); expect(o.pitch).toBe(pitch); expect(o.excessSpeed).toBe(0);
    expect(g.release(1, 50).handled).toBe(false); expect(g.owner).toBeNull();
  });
  it("estimates recent movement independently of a long hold before the drag", () => {
    const speeds = [0, 1000].map(delay => {
      const o = new GlobeOrientation(), g = new GlobeGesture(o);
      g.press(sample(0, 0, 0), 300);
      g.move(sample(6, 0, delay + 20)); g.move(sample(12, 0, delay + 40));
      g.release(1, delay + 50); return o.angularSpeed;
    });
    expect(speeds[0]).toBeGreaterThan(1);
    expect(speeds[1]).toBeGreaterThan(1);
    expect(Math.abs(speeds[0]! - speeds[1]!)).toBeLessThan(1);
    const o = new GlobeOrientation(), g = new GlobeGesture(o);
    g.press(sample(0, 0, 0), 300); g.move(sample(50, 0, 10020)); g.release(1, 10030);
    expect(o.angularSpeed).toBeGreaterThan(3);
  });
  it("yields vertical touch movement to scrolling, with no tilt or fling", () => {
    const o = new GlobeOrientation(), g = new GlobeGesture(o);
    g.press(sample(0, 0, 0, true), 300);
    expect(g.move(sample(2, 40, 30, true))).toBe("scroll");
    expect(o.spin).toBe(-0.5); expect(o.pitch).toBe(0.3); expect(o.excessSpeed).toBe(0);
    expect(g.owner).toBeNull();
  });
  it("claims horizontal touch while retaining pitch and avoids drag-as-tap", () => {
    const o = new GlobeOrientation(), g = new GlobeGesture(o);
    g.press(sample(0, 0, 0, true), 300);
    expect(g.move(sample(40, 2, 30, true))).toBe("dragging");
    expect(o.pitch).toBe(0.3); expect(g.release(1, 40).tap).toBe(false);
  });
});
