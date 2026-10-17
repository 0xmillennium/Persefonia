import { describe, it, expect } from 'vitest';
import { Hold, HOLD } from '../hold/state';
import { HoldMaterial } from '../hold/material';
const advance = (h: Hold, seconds: number, ready = true, cover = 1, reduced = false) => {
  let commits = 0;
  for (let t = 0; t < seconds; t += 1 / 120) if (h.step(1 / 120, ready, cover, reduced)) commits++;
  return commits;
};
describe('Physical hold and outward reveal', () => {
  it('explicit media retry renews readiness without restarting the aperture or duplicating a reveal', () => {
    const h = new Hold();
    h.select({ x: 100, y: 100 });
    advance(h, HOLD.readinessTimeout - 0.2, false);
    const aperture = h.expansion;
    h.retry();
    expect(h.expansion).toBe(aperture);
    expect(advance(h, 1, false)).toBe(0);
    expect(h.phase).toBe('COVERED_WAIT_READY');
    expect(advance(h, 0.5)).toBe(1);
    h.retry();
    expect(advance(h, 1)).toBe(0);
    expect(h.cycles).toBe(1);
  });
  it('a retried unavailable film still has a bounded readiness deadline', () => {
    const h = new Hold();
    h.select({ x: 100, y: 100 });
    advance(h, HOLD.readinessTimeout - 0.2, false);
    h.retry();
    advance(h, HOLD.readinessTimeout + 2, false);
    expect(h.phase).toBe('IDLE');
    expect(h.failure).toContain('not ready');
    expect(h.cycles).toBe(0);
  });
  it('PH-01/03 starts continuous progress and derives all countdown numbers', () => {
    const h = new Hold();
    expect(h.press({ x: 200, y: 200 }, 'mouse')).toBe(true);
    expect(h.countdown).toBe(3);
    advance(h, 1.05);
    expect(h.countdown).toBe(2);
    advance(h, 1);
    expect(h.countdown).toBe(1);
    expect(h.press({ x: 0, y: 0 }, 'mouse')).toBe(false);
  });
  it('PH-05 early release contracts without requesting completion', () => {
    const h = new Hold();
    h.press({ x: 300, y: 200 }, 'mouse');
    advance(h, 1.5);
    const before = h.expansion;
    h.release();
    expect(h.phase).toBe('CANCELING');
    expect(advance(h, 0.3)).toBe(0);
    expect(h.expansion).toBeLessThan(before);
    advance(h, 3);
    expect(h.phase).toBe('IDLE');
  });
  it('PH-06/07 residual progress and center are retained at re-press', () => {
    const h = new Hold();
    h.press({ x: 100, y: 100 }, 'mouse');
    advance(h, 2.4);
    h.release();
    advance(h, 0.12);
    const p = h.progress,
      e = h.expansion;
    h.press({ x: 900, y: 500 }, 'mouse');
    expect(h.progress).toBe(p);
    expect(h.expansion).toBe(e);
    expect(h.center).toEqual({ x: 100, y: 100 });
    h.step(0.016, false, 0);
    expect(h.center.x).toBeGreaterThan(100);
    expect(h.center.x).toBeLessThan(900);
  });
  it('PH-04/08 countdown, coverage and latest pair readiness independently guard reveal', () => {
    const h = new Hold();
    h.press({ x: 100, y: 100 }, 'mouse');
    expect(advance(h, 3.6, false, 1)).toBe(0);
    expect(h.phase).toBe('COVERED_WAIT_READY');
    expect(advance(h, 0.2, true, 0.9)).toBe(0);
    h.step(0.016, true, 1);
    expect(h.phase).toBe('REVEALING');
    expect(h.cycles).toBe(0);
    expect(advance(h, 0.2)).toBe(0);
    expect(advance(h, 0.2)).toBe(1);
  });
  it('PH-14 a 15-second physical hold commits once and stops animation until release', () => {
    const h = new Hold();
    h.press({ x: 100, y: 100 }, 'mouse');
    expect(advance(h, 15)).toBe(1);
    expect(h.phase).toBe('SUCCESS_WAIT_RELEASE');
    expect(h.animating).toBe(false);
    expect(h.pressed).toBe(true);
    expect(h.press({ x: 200, y: 200 }, 'mouse')).toBe(false);
  });
  it('PH-15 a distinct released and pressed gesture permits one second switch', () => {
    const h = new Hold();
    h.press({ x: 100, y: 100 }, 'mouse');
    advance(h, 5);
    h.release();
    expect(h.phase).toBe('IDLE');
    h.press({ x: 200, y: 200 }, 'mouse');
    expect(advance(h, 5)).toBe(1);
    expect(h.cycles).toBe(2);
  });
  it('PH-18 release during readiness wait retains earned completion; reveal release cannot undo it', () => {
    const h = new Hold();
    h.press({ x: 100, y: 100 }, 'mouse');
    advance(h, 3.6, false);
    h.release();
    expect(h.phase).toBe('COVERED_WAIT_READY');
    h.step(0.01, true, 1);
    expect(h.phase).toBe('REVEALING');
    h.release();
    expect(advance(h, 0.5)).toBe(1);
    expect(h.phase).toBe('IDLE');
  });
  it('PH-17 media readiness failure cancels to a recoverable idle', () => {
    const h = new Hold();
    h.press({ x: 0, y: 0 }, 'mouse');
    expect(advance(h, 20, false)).toBe(0);
    expect(h.phase).toBe('IDLE');
    expect(h.failure).toContain('not ready');
    expect(h.press({ x: 100, y: 100 }, 'mouse')).toBe(true);
  });
  it('PH-08/09 reveal captures origin and pauses while the matching pair is unavailable', () => {
    const h = new Hold();
    h.press({ x: 100, y: 100 }, 'mouse');
    advance(h, 3.6);
    const origin = { ...h.revealCenter },
      p = h.revealProgress;
    h.move({ x: 900, y: 900 });
    advance(h, 0.1, false);
    expect(h.revealCenter).toEqual(origin);
    expect(h.revealProgress).toBe(p);
    expect(h.press({ x: 800, y: 200 }, 'mouse')).toBe(false);
  });
  it('PH-19 touch movement cancels incomplete growth but never generates retarget movement', () => {
    const h = new Hold();
    h.press({ x: 100, y: 100 }, 'touch');
    h.move({ x: 105, y: 105 });
    expect(h.pressed).toBe(true);
    expect(h.target).toEqual({ x: 100, y: 100 });
    h.move({ x: 100 + HOLD.touchSlop + 1, y: 100 });
    expect(h.phase).toBe('CANCELING');
    expect(h.pressed).toBe(false);
  });
  it('PH-20 touch re-press preserves residue', () => {
    const h = new Hold();
    h.press({ x: 10, y: 10 }, 'touch');
    advance(h, 1);
    h.release();
    advance(h, 0.1);
    const p = h.progress;
    h.press({ x: 300, y: 100 }, 'touch');
    expect(h.progress).toBe(p);
    expect(h.center).toEqual({ x: 10, y: 10 });
  });
  it('PH-21 fine-pointer center can follow offscreen and reset is idempotent', () => {
    const h = new Hold();
    h.press({ x: 10, y: 10 }, 'pen');
    h.move({ x: -400, y: 1200 });
    advance(h, 0.5, false, 0);
    expect(h.center.x).toBeLessThan(-300);
    expect(h.pressed).toBe(true);
    h.reset();
    h.reset();
    expect(h.phase).toBe('IDLE');
  });
  it('Reduced-motion selection retains readiness and one-commit gates', () => {
    const h = new Hold();
    h.select({ x: 10, y: 10 });
    expect(h.step(0.1, false, 0, true)).toBe(false);
    h.step(0.1, true, 0, true);
    expect(h.step(0.01, true, 0, true)).toBe(true);
    expect(h.cycles).toBe(1);
  });
});
describe('Persistent spatial material', () => {
  it('keeps growth light localized at the stored liquid boundary', () => {
    const h = new Hold(),
      m = new HoldMaterial(1200, 800, 96);
    h.press({ x: 400, y: 350 }, 'mouse');
    for (let n = 0; n < 72; n++) {
      h.step(1 / 60, false, m.coverage);
      m.step(h);
    }
    let rim = 0,
      interior = 0,
      outside = 0;
    for (let i = 0; i < m.pixels.length; i += 4) {
      if (m.pixels[i + 3]! > 100) {
        rim++;
        expect(Math.abs(m.distances[i / 4]!)).toBeLessThan(20);
      }
      if (m.pixels[i] === 255 && m.pixels[i + 3] === 0) interior++;
      if (m.pixels[i] === 0 && m.pixels[i + 3] === 0) outside++;
    }
    expect(rim).toBeGreaterThan(0);
    expect(interior).toBeGreaterThan(rim);
    expect(outside).toBeGreaterThan(rim);
  });

  const step = (h: Hold, m: HoldMaterial, t: number, ready = false) => {
    for (let n = 0; n < Math.ceil(t * 60); n++) {
      h.step(1 / 60, ready, m.coverage);
      m.step(h);
    }
  };
  it('PH-04 full coverage includes portrait and offscreen origins', () => {
    for (const [w, height] of [
      [1200, 800],
      [390, 844],
      [800, 1200],
    ])
      for (const center of [
        { x: 10, y: 10 },
        { x: -400, y: 1000 },
        { x: w! / 2, y: height! / 2 },
      ]) {
        const h = new Hold(),
          m = new HoldMaterial(w!, height!, 48);
        h.press(center, 'mouse');
        step(h, m, 3.6);
        expect(m.coverage).toBe(1);
        expect(h.phase).toBe('COVERED_WAIT_READY');
      }
  });
  it('PH-06 actual lattice identity and morphology survive contraction/re-press', () => {
    const h = new Hold(),
      m = new HoldMaterial(1200, 800, 48);
    h.press({ x: 250, y: 300 }, 'mouse');
    step(h, m, 1.5);
    const area = m.area;
    h.release();
    step(h, m, 0.1);
    expect(m.area).toBeLessThan(area);
    const before = m.distances.slice(),
      generation = m.generations;
    h.press({ x: 800, y: 400 }, 'mouse');
    m.step(h);
    expect(m.distances).toEqual(before);
    expect(m.generations).toBe(generation);
    step(h, m, 0.2);
    expect(m.generations).toBe(1);
    expect(m.area).toBeGreaterThan(0);
  });
  it('PH-09/10 reveal aperture expands monotonically from captured origin', () => {
    const h = new Hold(),
      m = new HoldMaterial(1200, 800, 48);
    h.press({ x: 300, y: 300 }, 'mouse');
    step(h, m, 3.5);
    h.step(0.01, true, m.coverage);
    m.step(h);
    let area = 0;
    for (let n = 0; n < 19; n++) {
      const previous = m.pixels.slice();
      h.step(1 / 60, true, 1);
      m.step(h);
      let current = 0;
      for (let i = 1; i < m.pixels.length; i += 4) {
        expect(m.pixels[i]).toBeGreaterThanOrEqual(previous[i]!);
        if (m.pixels[i]! > 127) current++;
      }
      expect(current).toBeGreaterThanOrEqual(area);
      area = current;
    }
    expect(area).toBeGreaterThan(m.width * m.height * 0.85);
  });
});
