import { describe, expect, it, vi } from 'vitest';
import { lazyGlobe, type GlobeInstance } from '../lazy';

function createStage(): HTMLElement {
  return { dataset: {} } as unknown as HTMLElement;
}

function createMaskResponse(bytes = [1, 2, 3, 4], ok = true, status = 200): Response {
  const buffer = new Uint8Array(bytes).buffer;
  return {
    ok,
    status,
    arrayBuffer: async () => buffer,
  } as unknown as Response;
}

describe('lazyGlobe lifecycle and failure recovery (F-05)', () => {
  it('1. performs normal lazy initialization and mount when approaching the scene', async () => {
    const stage = createStage();
    const activeCalls: boolean[] = [];
    const constructed: Uint8Array[] = [];

    class FakeGlobe implements GlobeInstance {
      constructor(target: HTMLElement, mask: Uint8Array) {
        constructed.push(mask);
        target.dataset.ready = 'true';
      }
      active(value: boolean) {
        activeCalls.push(value);
      }
      dispose() {}
    }

    const globe = lazyGlobe({
      stage,
      isActive: () => true,
      loadController: async () => ({ GlobeController: FakeGlobe }),
      loadMaskUrl: async () => ({ default: '/assets/land-mask.bin' }),
      fetchMask: async () => createMaskResponse([10, 20, 30]),
    });

    expect(globe.state).toBe('idle');
    expect(stage.dataset.ready).toBeUndefined();

    globe.approach(false);
    expect(globe.state).toBe('idle');

    const ready = await globe.prepare();
    expect(ready).toBe(true);
    expect(globe.state).toBe('ready');
    expect(stage.dataset.ready).toBe('true');
    expect(constructed).toHaveLength(1);
    expect([...constructed[0]!]).toEqual([10, 20, 30]);
    expect(activeCalls).toEqual([true]);
  });

  it('2. recovers from a dynamic import failure on subsequent retry', async () => {
    const stage = createStage();
    let importAttempts = 0;

    class FakeGlobe implements GlobeInstance {
      constructor(target: HTMLElement) {
        target.dataset.ready = 'true';
      }
      active() {}
      dispose() {}
    }

    const globe = lazyGlobe({
      stage,
      loadController: async () => {
        importAttempts++;
        if (importAttempts === 1) throw new Error('ChunkLoadError: network timeout');
        return { GlobeController: FakeGlobe };
      },
      loadMaskUrl: async () => ({ default: '/assets/land-mask.bin' }),
      fetchMask: async () => createMaskResponse(),
    });

    expect(await globe.prepare()).toBe(false);
    expect(globe.state).toBe('failed');
    expect(stage.dataset.ready).toBe('false');

    expect(await globe.prepare()).toBe(true);
    expect(globe.state).toBe('ready');
    expect(stage.dataset.ready).toBe('true');
    expect(importAttempts).toBe(2);
  });

  it('3. recovers from a geography fetch network failure on subsequent retry', async () => {
    const stage = createStage();
    let fetchAttempts = 0;

    class FakeGlobe implements GlobeInstance {
      constructor(target: HTMLElement) {
        target.dataset.ready = 'true';
      }
      active() {}
      dispose() {}
    }

    const globe = lazyGlobe({
      stage,
      loadController: async () => ({ GlobeController: FakeGlobe }),
      loadMaskUrl: async () => ({ default: '/assets/land-mask.bin' }),
      fetchMask: async () => {
        fetchAttempts++;
        if (fetchAttempts === 1) throw new TypeError('Failed to fetch');
        return createMaskResponse();
      },
    });

    expect(await globe.prepare()).toBe(false);
    expect(globe.state).toBe('failed');
    expect(stage.dataset.ready).toBe('false');

    expect(await globe.prepare()).toBe(true);
    expect(globe.state).toBe('ready');
    expect(stage.dataset.ready).toBe('true');
    expect(fetchAttempts).toBe(2);
  });

  it('4. recovers from a non-OK HTTP response on subsequent retry', async () => {
    const stage = createStage();
    let fetchAttempts = 0;

    class FakeGlobe implements GlobeInstance {
      constructor(target: HTMLElement) {
        target.dataset.ready = 'true';
      }
      active() {}
      dispose() {}
    }

    const globe = lazyGlobe({
      stage,
      loadController: async () => ({ GlobeController: FakeGlobe }),
      loadMaskUrl: async () => ({ default: '/assets/land-mask.bin' }),
      fetchMask: async () => {
        fetchAttempts++;
        if (fetchAttempts === 1) return createMaskResponse([], false, 503);
        return createMaskResponse();
      },
    });

    expect(await globe.prepare()).toBe(false);
    expect(globe.state).toBe('failed');
    expect(stage.dataset.ready).toBe('false');

    expect(await globe.prepare()).toBe(true);
    expect(globe.state).toBe('ready');
    expect(stage.dataset.ready).toBe('true');
    expect(fetchAttempts).toBe(2);
  });

  it('5. handles WebGL initialization or partial activation failure with fallback readiness and cleanup', async () => {
    const stage = createStage();
    let attempts = 0;
    let partialDisposed = 0;

    class FlakyWebGLGlobe implements GlobeInstance {
      constructor(target: HTMLElement) {
        attempts++;
        if (attempts === 1) {
          throw new Error('WebGL2 unavailable');
        }
        target.dataset.ready = 'true';
      }
      active() {
        if (attempts === 2) {
          throw new Error('Activation failure after construction');
        }
      }
      dispose() {
        partialDisposed++;
      }
    }

    const globe = lazyGlobe({
      stage,
      loadController: async () => ({ GlobeController: FlakyWebGLGlobe }),
      loadMaskUrl: async () => ({ default: '/assets/land-mask.bin' }),
      fetchMask: async () => createMaskResponse(),
    });

    // Attempt 1: constructor throws WebGL2 unavailable
    expect(await globe.prepare()).toBe(false);
    expect(globe.state).toBe('failed');
    expect(stage.dataset.ready).toBe('false');
    expect(partialDisposed).toBe(0);

    // Attempt 2: constructor succeeds, active() throws -> created instance is disposed
    expect(await globe.prepare()).toBe(false);
    expect(globe.state).toBe('failed');
    expect(stage.dataset.ready).toBe('false');
    expect(partialDisposed).toBe(1);

    // Attempt 3: succeeds cleanly
    expect(await globe.prepare()).toBe(true);
    expect(globe.state).toBe('ready');
    expect(stage.dataset.ready).toBe('true');
  });

  it('6. deduplicates concurrent initialization triggers without duplicate instances or fetches', async () => {
    const stage = createStage();
    let controllerLoads = 0;
    let fetches = 0;
    let instances = 0;

    let resolveFetch!: (response: Response) => void;

    class FakeGlobe implements GlobeInstance {
      constructor(target: HTMLElement) {
        instances++;
        target.dataset.ready = 'true';
      }
      active() {}
      dispose() {}
    }

    const globe = lazyGlobe({
      stage,
      loadController: async () => {
        controllerLoads++;
        return { GlobeController: FakeGlobe };
      },
      loadMaskUrl: async () => ({ default: '/assets/land-mask.bin' }),
      fetchMask: () =>
        new Promise<Response>((resolve) => {
          fetches++;
          resolveFetch = resolve;
        }),
    });

    globe.approach(true);
    globe.approach(true);
    globe.active(true);
    const p1 = globe.prepare();
    const p2 = globe.prepare();

    expect(globe.state).toBe('loading');
    expect(p1).toBe(p2);
    await vi.waitFor(() => expect(fetches).toBe(1));

    resolveFetch(createMaskResponse());
    const [r1, r2] = await Promise.all([p1, p2]);

    expect(r1).toBe(true);
    expect(r2).toBe(true);
    expect(globe.state).toBe('ready');
    expect(controllerLoads).toBe(1);
    expect(fetches).toBe(1);
    expect(instances).toBe(1);
  });

  it('7. aborts pending fetch and prevents mount when disposed while loading', async () => {
    const stage = createStage();
    let fetchStarted = false;
    let aborted = false;
    let instances = 0;

    class FakeGlobe implements GlobeInstance {
      constructor() {
        instances++;
      }
      active() {}
      dispose() {}
    }

    const globe = lazyGlobe({
      stage,
      loadController: async () => ({ GlobeController: FakeGlobe }),
      loadMaskUrl: async () => ({ default: '/assets/land-mask.bin' }),
      fetchMask: (_url, init) =>
        new Promise<Response>((_resolve, reject) => {
          fetchStarted = true;
          init.signal?.addEventListener('abort', () => {
            aborted = true;
            reject(new DOMException('Aborted', 'AbortError'));
          });
        }),
    });

    const pending = globe.prepare();
    expect(globe.state).toBe('loading');
    await vi.waitFor(() => expect(fetchStarted).toBe(true));

    globe.dispose();
    expect(globe.state).toBe('disposed');

    const result = await pending;
    expect(result).toBe(false);
    expect(aborted).toBe(true);
    expect(instances).toBe(0);
    expect(globe.state).toBe('disposed');
  });

  it('8. ignores late import or fetch resolution after owner disposal', async () => {
    const stage = createStage();
    let resolveImport!: (value: { GlobeController: new () => GlobeInstance }) => void;
    let instances = 0;

    class FakeGlobe implements GlobeInstance {
      constructor(target?: HTMLElement) {
        instances++;
        if (target) target.dataset.ready = 'true';
      }
      active() {}
      dispose() {}
    }

    const globe = lazyGlobe({
      stage,
      loadController: () =>
        new Promise((resolve) => {
          resolveImport = resolve;
        }),
      loadMaskUrl: async () => ({ default: '/assets/land-mask.bin' }),
      fetchMask: async () => createMaskResponse(),
    });

    const pending = globe.prepare();
    globe.dispose();
    expect(globe.state).toBe('disposed');

    resolveImport({ GlobeController: FakeGlobe });
    expect(await pending).toBe(false);
    expect(instances).toBe(0);
    expect(stage.dataset.ready).toBeUndefined();
    expect(await globe.prepare()).toBe(false);
  });

  it('9. supports repeated scene entry and exit without duplicating resources', async () => {
    const stage = createStage();
    let instances = 0;
    const activeHistory: boolean[] = [];

    class FakeGlobe implements GlobeInstance {
      constructor(target: HTMLElement) {
        instances++;
        target.dataset.ready = 'true';
      }
      active(value: boolean) {
        activeHistory.push(value);
      }
      dispose() {}
    }

    const globe = lazyGlobe({
      stage,
      loadController: async () => ({ GlobeController: FakeGlobe }),
      loadMaskUrl: async () => ({ default: '/assets/land-mask.bin' }),
      fetchMask: async () => createMaskResponse(),
    });

    globe.approach(true);
    await globe.prepare();
    expect(instances).toBe(1);

    for (const state of [true, false, true, false, true]) {
      globe.approach(state);
      globe.active(state);
    }

    expect(instances).toBe(1);
    expect(activeHistory).toEqual([false, true, false, true, false, true]);
  });

  it('10. recovers on scene re-entry without creating a rapid scroll retry loop', async () => {
    const stage = createStage();
    const fetchSpy = vi
      .fn<() => Promise<Response>>()
      .mockRejectedValueOnce(new Error('Offline'))
      .mockResolvedValueOnce(createMaskResponse());

    class FakeGlobe implements GlobeInstance {
      constructor(target: HTMLElement) {
        target.dataset.ready = 'true';
      }
      active() {}
      dispose() {}
    }

    const globe = lazyGlobe({
      stage,
      loadController: async () => ({ GlobeController: FakeGlobe }),
      loadMaskUrl: async () => ({ default: '/assets/land-mask.bin' }),
      fetchMask: () => fetchSpy(),
    });

    // Enter approach zone -> triggers initial attempt, which fails
    globe.approach(true);
    await vi.waitFor(() => expect(globe.state).toBe('failed'));
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(stage.dataset.ready).toBe('false');

    // Continuous scroll frames inside the same approach zone must NOT spin retries
    globe.approach(true);
    globe.approach(true);
    globe.approach(true);
    expect(fetchSpy).toHaveBeenCalledTimes(1);

    // Leaving the approach zone and re-entering triggers a single recovery attempt
    globe.approach(false);
    globe.approach(true);
    await vi.waitFor(() => expect(globe.state).toBe('ready'));
    expect(fetchSpy).toHaveBeenCalledTimes(2);
    expect(stage.dataset.ready).toBe('true');
  });
});
