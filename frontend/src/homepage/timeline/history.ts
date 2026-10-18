export interface HomepagePosition {
  y: number;
  width: number;
  height: number;
}

export interface HomepageHistorySnapshot {
  film?: string;
  category?: string;
  pathCount?: number;
  tuningShift?: number;
}

export interface HomepageHistoryOptions {
  historyVisit?: boolean;
  pageShown?: boolean;
  readState?: () => unknown;
  writeState?: (state: Record<string, unknown>) => void;
  viewport?: () => { y: number; width: number; height: number };
  now?: () => number;
  requestFrame?: (callback: FrameRequestCallback) => number;
  cancelFrame?: (handle: number) => void;
}

/** Restore only positions measured for the same viewport; preserve native fallback. */
export function storedPosition(state: unknown, width: number, height: number): number | null {
  if (!state || typeof state !== 'object') return null;
  const value = state as Partial<HomepagePosition>;
  return typeof value.y === 'number' &&
    Number.isFinite(value.y) &&
    value.y >= 0 &&
    value.width === width &&
    value.height === height
    ? value.y
    : null;
}

/** Owns history-visit detection, throttled state persistence, and deferred scroll restoration. */
export function homepageHistory(options: HomepageHistoryOptions = {}) {
  const {
    historyVisit = (
      performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined
    )?.type === 'back_forward',
    readState = () => history.state,
    writeState = (state) => history.replaceState(state, ''),
    viewport = () => ({ y: scrollY, width: innerWidth, height: innerHeight }),
    now = () => performance.now(),
    requestFrame = (cb) => requestAnimationFrame(cb),
    cancelFrame = (handle) => cancelAnimationFrame(handle),
  } = options;

  const readStoredY = (): number | null => {
    const raw = readState() as { homepageV2?: unknown } | null | undefined;
    const { width, height } = viewport();
    return storedPosition(raw?.homepageV2, width, height);
  };

  let restoreY = historyVisit ? readStoredY() : null;
  let restoreFrame = 0;
  let pageShown = options.pageShown ?? document.readyState === 'complete';
  let lastSave = 0;
  let disposed = false;

  const cancel = () => {
    restoreY = null;
    if (restoreFrame) {
      cancelFrame(restoreFrame);
      restoreFrame = 0;
    }
  };

  return {
    get historyVisit() {
      return historyVisit;
    },
    get restoring() {
      return restoreY !== null;
    },
    cancel,
    save(snapshot: HomepageHistorySnapshot, force = false) {
      const timestamp = now();
      if (restoreY !== null || (!force && timestamp - lastSave < 250)) return;
      lastSave = timestamp;
      try {
        const current = readState();
        const base =
          current && typeof current === 'object' ? (current as Record<string, unknown>) : {};
        const { y, width, height } = viewport();
        writeState({
          ...base,
          homepageV2: {
            ...snapshot,
            y,
            width,
            height,
          },
        });
      } catch {
        /* Browsers can deny state writes; native scroll restoration still applies. */
      }
    },
    restore(ready: boolean, apply: (y: number) => void) {
      if (restoreY === null || !pageShown || !ready || restoreFrame || disposed) return;
      restoreFrame = requestFrame(() => {
        restoreFrame = 0;
        if (disposed || restoreY === null) return;
        const y = restoreY;
        restoreY = null;
        apply(y);
      });
    },
    pageshow(persisted: boolean) {
      pageShown = true;
      if (persisted) {
        restoreY = readStoredY();
      }
    },
    dispose() {
      disposed = true;
      cancel();
    },
  };
}
