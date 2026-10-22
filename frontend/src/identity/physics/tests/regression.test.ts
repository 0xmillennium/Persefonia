import { it, expect } from 'vitest';
import { applyPath, invariantFailure } from './scenarios';
import { PhysicsCore } from '../core';
import { legacyGeometry } from './legacy-geometry';
import seed26 from './regression-seed-26.json';
import seed27 from './regression-seed-27.json';
import seed30 from './regression-seed-30.json';
import seed45 from './regression-seed-45.json';
import seed58 from './regression-seed-58.json';
import seed74 from './regression-seed-74.json';

it.each(
  [seed26, seed27, seed30, seed45, seed58, seed74].flatMap((seed) =>
    ['scalar', 'live'].map((mode) => ({ ...seed, mode })),
  ),
)('preserved $mode seed $seed reaches REST', (seed) => {
  const core = new PhysicsCore(legacyGeometry);
  core.loadFixture(seed.fixture.fragments);
  if (seed.mode === 'scalar') applyPath(core, seed.fixture.path);
  else {
    core.stepStroke([seed.fixture.path[0]!]);
    for (let i = 1; i < seed.fixture.path.length - 1; i += 3)
      core.stepStroke(seed.fixture.path.slice(i, Math.min(i + 3, seed.fixture.path.length - 1)));
    core.stepStroke([seed.fixture.path.at(-1)!]);
  }
  let failure: string | null = null;
  for (let step = 0; step < 5400; step++) {
    core.stepFixed();
    failure = core.runtimeFailure();
    if (failure || core.isRest()) break;
  }
  const snapshot = core.getSnapshot();
  failure ??= invariantFailure(snapshot);
  expect(failure, `seed ${seed.seed}`).toBeNull();
  expect(snapshot.rest, `seed ${seed.seed}`).toBe(true);
});
