import { it, expect } from 'vitest';
import { PhysicsCore, PHYSICS } from '../core';
import { canonicalGeometry } from '../geometry';
import { homeResponse } from '../home-response';

it('the aggregated forecast spring matches the existing distributed-anchor force and torque', () => {
  const core = new PhysicsCore(canonicalGeometry);
  core.loadFixture(
    canonicalGeometry.map((g, i) => ({
      id: g.id,
      position: { x: 410 + i * 12, y: 280 + i * 5 },
      angle: i * 0.7 - 2.4,
    })),
  );
  for (const body of core.bodies) {
    const exact = core.homeForce(body),
      aggregated = homeResponse(body, core.home(body), PHYSICS.homeStiffness);
    expect(aggregated.acceleration.x).toBeCloseTo(exact.acceleration.x, 8);
    expect(aggregated.acceleration.y).toBeCloseTo(exact.acceleration.y, 8);
    expect(aggregated.torque).toBeCloseTo(exact.torque, 8);
  }
});
