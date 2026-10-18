import { describe, expect, it } from 'vitest';
import { PhysicsCore, PHYSICS, navigatorTransfer } from './physics/core';
import { canonicalGeometry } from './physics/geometry';
import { identityStage, IDENTITY_RESPONSE } from './stage';

describe('homepage identity viewport physics', () => {
  it('keeps the original assembled mark while moving the walls to each viewport', () => {
    for (const bounds of [
      { x: -90, y: -160, width: 1536, height: 960 },
      { x: 0, y: -720, width: 980, height: 2136 },
      { x: -130, y: -200, width: 1900, height: 900 },
    ]) {
      const stage = identityStage(canonicalGeometry, bounds);
      const core = new PhysicsCore(stage.geometry, stage);
      for (const body of core.bodies) {
        const original = canonicalGeometry.find((g) => g.id === body.geometry.id)!;
        expect(body.position.x + bounds.x).toBeCloseTo(original.home.x + 480, 9);
        expect(body.position.y + bounds.y).toBeCloseTo(original.home.y + 310, 9);
        expect(body.geometry.vertices).toBe(original.vertices);
        expect(body.geometry.parts).toBe(original.parts);
      }
      expect(core.isRest()).toBe(true);
    }
  });

  it('lets a fast actual pointer strike reach a viewport wall and return safely', () => {
    const bounds = { x: -90, y: -160, width: 1536, height: 960 };
    const source = canonicalGeometry.find((g) => g.id === 'corner-top-left')!;
    const run = (elapsedSeconds: number) => {
      const stage = identityStage([source], bounds);
      const core = new PhysicsCore(stage.geometry, stage);
      const body = core.bodies[0]!,
        home = { ...body.position };
      core.stepStroke([{ x: home.x + 180, y: home.y, elapsedSeconds: 0.01 }]);
      core.stepStroke([{ x: home.x - 180, y: home.y, elapsedSeconds }]);
      core.stepStroke([{ x: -100, y: -100, active: false }]);
      let nearestWall = Infinity,
        maxTravel = 0;
      for (let i = 0; i < 1800; i++) {
        core.stepFixed(1, undefined, 0);
        for (const p of core.vertices(body))
          nearestWall = Math.min(nearestWall, p.x, core.width - p.x, p.y, core.height - p.y);
        maxTravel = Math.max(
          maxTravel,
          Math.hypot(body.position.x - home.x, body.position.y - home.y),
        );
        expect(
          core.runtimeFailure(),
          JSON.stringify({ step: core.step, energy: core.energy(), ledger: core.ledger }),
        ).toBeNull();
      }
      return { nearestWall, maxTravel, rest: core.isRest() };
    };
    const slow = run(1),
      fast = run(0.015);
    expect(fast.maxTravel).toBeGreaterThan(slow.maxTravel * 3);
    expect(fast.nearestWall).toBeLessThan(1);
    expect(fast.rest).toBe(true);
    expect(navigatorTransfer(5000, IDENTITY_RESPONSE.navigatorSpeedLimit)).toBeGreaterThan(
      navigatorTransfer(5000) * 3,
    );
  });

  it('keeps the complete mark finite, contained and collision-safe through repeated fast strikes', () => {
    const bounds = { x: -90, y: -160, width: 1536, height: 960 };
    const stage = identityStage(canonicalGeometry, bounds);
    const core = new PhysicsCore(stage.geometry, stage);
    const center = { x: 480 - bounds.x, y: 310 - bounds.y };
    let travel = 0;
    for (const direction of [-1, 1, -1]) {
      core.stepStroke([{ x: center.x - direction * 220, y: center.y - 40, elapsedSeconds: 0.1 }]);
      core.stepStroke([{ x: center.x + direction * 220, y: center.y - 40, elapsedSeconds: 0.015 }]);
      core.stepStroke([{ x: -100, y: -100, active: false }]);
      for (let tick = 0; tick < 180; tick++) {
        core.stepFixed(1, undefined, 4);
        travel = Math.max(
          travel,
          ...core.bodies.map((b) =>
            Math.hypot(b.position.x - core.home(b).x, b.position.y - core.home(b).y),
          ),
        );
        expect(
          core.runtimeFailure(),
          JSON.stringify({ step: core.step, energy: core.energy(), ledger: core.ledger }),
        ).toBeNull();
      }
    }
    expect(travel).toBeGreaterThan(350);
    for (let tick = 0; tick < 3600 && !core.isRest(); tick++) {
      core.stepFixed(1, undefined, 4);
      expect(
        core.runtimeFailure(),
        JSON.stringify({ step: core.step, ledger: core.ledger }),
      ).toBeNull();
    }
    expect(core.isRest()).toBe(true);
  });

  it('leaves other physics users on their original response and rejects invalid overrides', () => {
    const core = new PhysicsCore(canonicalGeometry);
    expect(core.response.homeStiffness).toBe(PHYSICS.homeStiffness);
    expect(core.response.navigatorSpeedLimit).toBe(PHYSICS.navigatorSpeedLimit);
    for (const response of [
      { homeStiffness: 0 },
      { linearDamping: -1 },
      { restitution: 1.1 },
      { navigatorSpeedLimit: NaN },
    ])
      expect(() => new PhysicsCore(canonicalGeometry, { response })).toThrow(
        'Invalid physics response',
      );
  });
});
