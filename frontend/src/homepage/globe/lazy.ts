export type GlobeLifecycleState = 'idle' | 'loading' | 'ready' | 'failed' | 'disposed';

export interface GlobeInstance {
  active(value: boolean): void;
  dispose(): void;
}

export interface LazyGlobeOptions {
  stage: HTMLElement;
  isActive?: () => boolean;
  loadController?: () => Promise<{
    GlobeController: new (
      stage: HTMLElement,
      mask: Uint8Array,
      diagnostics?: boolean,
      controlsRoot?: ParentNode,
    ) => GlobeInstance;
  }>;
  loadMaskUrl?: () => Promise<{ default: string }>;
  fetchMask?: (url: string, init: RequestInit) => Promise<Response>;
}

/**
 * Owns lazy globe loading, concurrency deduplication, failure recovery, and disposal.
 * A temporary import, geography-fetch, or WebGL error transitions to 'failed' so a
 * subsequent scene re-entry or explicit prepare() can retry without a rapid scroll loop.
 */
export function lazyGlobe(options: LazyGlobeOptions) {
  const {
    stage,
    isActive,
    loadController = () => import('./controller'),
    loadMaskUrl = () => import('./assets/land-mask.bin?url'),
    fetchMask = (url, init) => fetch(url, init),
  } = options;

  let status: GlobeLifecycleState = 'idle';
  let instance: GlobeInstance | null = null;
  let pending: Promise<boolean> | null = null;
  let active = false;
  let retryEligible = true;
  const lifetime = new AbortController();
  const isDisposed = () => status === 'disposed';

  const prepare = (): Promise<boolean> => {
    if (isDisposed()) return Promise.resolve(false);
    if (status === 'ready') return Promise.resolve(true);
    if (pending) return pending;

    status = 'loading';
    retryEligible = false;

    pending = (async () => {
      let created: GlobeInstance | null = null;
      try {
        const [{ GlobeController }, { default: maskUrl }] = await Promise.all([
          loadController(),
          loadMaskUrl(),
        ]);
        if (isDisposed()) return false;
        const response = await fetchMask(maskUrl, { signal: lifetime.signal });
        if (!response.ok) throw new Error('Geography unavailable');
        const mask = new Uint8Array(await response.arrayBuffer());
        if (isDisposed()) return false;
        created = new GlobeController(stage, mask, false, stage);
        if (isDisposed()) {
          created.dispose();
          return false;
        }
        instance = created;
        status = 'ready';
        if (isActive) active = isActive();
        instance.active(active);
        return true;
      } catch {
        created?.dispose();
        if (instance === created) instance = null;
        if (!isDisposed()) {
          status = 'failed';
          stage.dataset.ready = 'false';
        }
        return false;
      } finally {
        pending = null;
      }
    })();

    return pending;
  };

  return {
    prepare,
    approach(near: boolean) {
      if (!near) {
        if (status === 'failed') retryEligible = true;
        return;
      }
      if (status === 'idle' || (status === 'failed' && retryEligible)) {
        void prepare();
      }
    },
    active(value: boolean) {
      const wasActive = active;
      active = value;
      if (value !== wasActive && status === 'failed') {
        retryEligible = true;
      }
      if (value && (status === 'idle' || (status === 'failed' && retryEligible))) {
        void prepare();
      }
      instance?.active(value);
    },
    dispose() {
      if (status === 'disposed') return;
      status = 'disposed';
      active = false;
      lifetime.abort();
      instance?.dispose();
      instance = null;
    },
    get state(): GlobeLifecycleState {
      return status;
    },
  };
}
