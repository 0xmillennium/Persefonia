import { expect, it } from 'vitest';
import { sceneState } from './scene-state';
import { writingPacing } from './pacing';
it('derives film and reading phases from measured sections across viewport/content lengths', () => {
  const pacing = writingPacing();
  for (const offset of [0, 1200, 3700]) {
    const scenes = {
      hero: { top: 0, height: 700 },
      statements: { top: 800, height: 1500 },
      capabilities: { top: 2400, height: 1500 },
      mark: { top: 4200 + offset, height: 1560 },
    };
    const gallery = {
      end: 18000,
      boundary: 18500,
      cue: 5900 + offset,
      first: 6300 + offset,
      authoredFirst: 6300 + offset,
      step: 200,
      count: 8,
      columns: 8,
    };
    const end = scenes.mark.top + scenes.mark.height * 0.98;
    expect(sceneState(end / 2, scenes, gallery).film).toBe(0.5);
    expect(sceneState(end, scenes, gallery).film).toBe(1);
    const third = sceneState(scenes.capabilities.top + pacing.transition, scenes, gallery).third;
    expect(third).toEqual({ entry: 1, exit: 0 });
    const positions = [0, 800, 2800, end, gallery.first, gallery.end];
    const forward = positions.map((y) => sceneState(y, scenes, gallery));
    expect(
      positions
        .slice()
        .reverse()
        .map((y) => sceneState(y, scenes, gallery)),
    ).toEqual(forward.slice().reverse());
  }
});
