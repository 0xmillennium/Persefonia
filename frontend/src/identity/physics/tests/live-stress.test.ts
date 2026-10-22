import policy from '../../../../scripts/verification/selection-policy.json';
import { afterAll, expect, it } from 'vitest';
import { mkdirSync, writeFileSync } from 'node:fs';
import { PhysicsCore, FIXED_DT } from '../core';
import { canonicalGeometry } from '../geometry';
import { seedFixture, invariantFailure } from './scenarios';

const seeds = process.env.IDENTITY_SEED
  ? [Number(process.env.IDENTITY_SEED)]
  : process.env.PERSEFONIA_PHYSICS_PROFILE === 'ordinary'
    ? policy.ordinaryPhysicsSeeds
    : Array.from({ length: 100 }, (_, i) => i + 1);
const results: { seed: number; failure: string | null; seconds: number; rest: boolean }[] = [];
afterAll(() => {
  mkdirSync('test-results/segmented-identity', { recursive: true });
  writeFileSync(
    'test-results/segmented-identity/live-stress-summary.json',
    JSON.stringify(results, null, 2),
  );
});
it.each(seeds)('live seed %i: coalesced fast strokes preserve invariants and recover', (seed) => {
  const core = new PhysicsCore(canonicalGeometry),
    fixture = seedFixture(seed);
  core.loadFixture(fixture.fragments);
  core.stepStroke([fixture.path[0]!]);
  let failure = core.runtimeFailure();
  // Three actual observations per tick exercise corners/reversals inside the
  // new production input path, rather than only the scalar fixture replay API.
  for (let i = 1; i < fixture.path.length - 1 && !failure; i += 3) {
    core.stepStroke(fixture.path.slice(i, Math.min(i + 3, fixture.path.length - 1)));
    failure = core.runtimeFailure();
  }
  if (!failure) {
    core.stepStroke([fixture.path.at(-1)!]);
    failure = core.runtimeFailure();
  }
  for (let i = 0; i < 45 / FIXED_DT && !failure && !core.isRest(); i++) {
    core.stepFixed();
    failure = core.runtimeFailure();
  }
  const snapshot = core.getSnapshot();
  failure ??= invariantFailure(snapshot);
  results.push({ seed, failure, seconds: core.step * FIXED_DT, rest: snapshot.rest });
  if (failure || !snapshot.rest) {
    mkdirSync('test-results/segmented-identity/live-seeds', { recursive: true });
    writeFileSync(
      `test-results/segmented-identity/live-seeds/${seed}.json`,
      JSON.stringify({ fixture, snapshot, failure }, null, 2),
    );
  }
  expect(failure, `live seed ${seed}`).toBeNull();
  expect(snapshot.rest, `live seed ${seed}`).toBe(true);
});
