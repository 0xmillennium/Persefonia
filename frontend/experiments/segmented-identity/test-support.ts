import { createSimulation, applyPath, stressRun, seedFixture } from "./scenarios";
import type { PhysicsCore, PointerSample, BodyFixture } from "./core";
import { pairKey } from "./deadlock";

function createTestApi(get: () => PhysicsCore, replace: (core: PhysicsCore) => void, render: () => void) {
  const api = Object.freeze({
    reset(options: { deadlock?: boolean } = {}) { replace(createSimulation(options.deadlock ?? true)); render(); },
    stepFixed(n = 1, pointer?: PointerSample) { get().stepFixed(n, pointer); render(); },
    stepStroke(samples: readonly PointerSample[]) { get().stepStroke(samples); render(); },
    getSnapshot: () => get().getSnapshot(),
    getFragmentState: (id: string) => get().getSnapshot().fragments.find(b => b.id === id),
    getPairState: (a: string, b: string) => get().getSnapshot().pairs.find(p => p.key === pairKey(a, b)),
    getClusterState: () => get().getSnapshot().clusters,
    loadFixture(fragments: readonly BodyFixture[]) { get().loadFixture(fragments); render(); },
    applyDeterministicPointerPath(path: readonly PointerSample[]) { applyPath(get(), path); render(); },
    seedFixture,
    runSeed(seed: number) { const result = stressRun(seed, 45, replace); render(); return result; }
  });
  return api;
}

export function installTestApi(get: () => PhysicsCore, replace: (core: PhysicsCore) => void, render: () => void) {
  const api = createTestApi(get, replace, render);
  Object.defineProperty(window, "__identityTest", { value: api, configurable: true });
  return () => { Reflect.deleteProperty(window, "__identityTest"); };
}
export type TestApi = ReturnType<typeof createTestApi>;
declare global { interface Window { __identityTest: TestApi } }
