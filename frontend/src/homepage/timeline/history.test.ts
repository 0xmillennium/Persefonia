import { describe, expect, it } from 'vitest';
import { homepageHistory, storedPosition } from './history';

describe('homepage history persistence and restoration', () => {
  it('accepts same-viewport history and rejects corrupt or stale measurements', () => {
    expect(storedPosition({ y: 7824, width: 1440, height: 900 }, 1440, 900)).toBe(7824);
    for (const value of [
      null,
      {},
      { y: NaN },
      { y: -1, width: 1440, height: 900 },
      { y: 7824, width: 390, height: 850 },
      { y: 7824, width: 1440, height: 850 },
    ]) {
      expect(storedPosition(value, 1440, 900)).toBeNull();
    }
  });

  it('throttles unforced saves to 250ms and suppresses writes while restoration is pending', () => {
    let clock = 300;
    let state: Record<string, unknown> = { homepageV2: { y: 1200, width: 1440, height: 900 } };
    const writes: Record<string, unknown>[] = [];

    const historyState = homepageHistory({
      historyVisit: true,
      pageShown: true,
      readState: () => state,
      writeState: (next) => {
        state = next;
        writes.push(next);
      },
      viewport: () => ({ y: 400, width: 1440, height: 900 }),
      now: () => clock,
      requestFrame: () => 1,
      cancelFrame: () => {},
    });

    expect(historyState.restoring).toBe(true);
    // Saves are suppressed until restoration completes or is cancelled
    historyState.save({ film: 'tree', category: 'projects' }, true);
    expect(writes).toHaveLength(0);

    historyState.cancel();
    expect(historyState.restoring).toBe(false);

    historyState.save({ film: 'tree', category: 'projects', pathCount: 4, tuningShift: 0 });
    expect(writes).toHaveLength(1);
    expect(state.homepageV2).toEqual({
      film: 'tree',
      category: 'projects',
      pathCount: 4,
      tuningShift: 0,
      y: 400,
      width: 1440,
      height: 900,
    });

    // Within 250ms throttle window: unforced save is skipped, forced save writes immediately
    clock = 450;
    historyState.save({ film: 'waterfall', category: 'projects' });
    expect(writes).toHaveLength(1);

    historyState.save({ film: 'waterfall', category: 'articles' }, true);
    expect(writes).toHaveLength(2);
  });

  it('coordinates deferred RAF scroll restoration, BFCache pageshow, and disposal', () => {
    let state: Record<string, unknown> = { homepageV2: { y: 2400, width: 1440, height: 900 } };
    let scheduledCallback: FrameRequestCallback | null = null;
    let cancelledFrame = 0;
    const restoredPositions: number[] = [];

    const historyState = homepageHistory({
      historyVisit: true,
      pageShown: false,
      readState: () => state,
      writeState: (next) => {
        state = next;
      },
      viewport: () => ({ y: 0, width: 1440, height: 900 }),
      now: () => 500,
      requestFrame: (cb) => {
        scheduledCallback = cb;
        return 42;
      },
      cancelFrame: (id) => {
        cancelledFrame = id;
      },
    });

    // Does not schedule until both pageShown and readiness are complete
    historyState.restore(true, (y) => restoredPositions.push(y));
    expect(scheduledCallback).toBeNull();

    historyState.pageshow(false);
    historyState.restore(false, (y) => restoredPositions.push(y));
    expect(scheduledCallback).toBeNull();

    historyState.restore(true, (y) => restoredPositions.push(y));
    expect(scheduledCallback).not.toBeNull();

    scheduledCallback!(516);
    expect(restoredPositions).toEqual([2400]);
    expect(historyState.restoring).toBe(false);

    // BFCache persisted pageshow reloads storedY and disposal cancels pending frame
    state = { homepageV2: { y: 3600, width: 1440, height: 900 } };
    historyState.pageshow(true);
    expect(historyState.restoring).toBe(true);
    historyState.restore(true, (y) => restoredPositions.push(y));
    historyState.dispose();
    expect(cancelledFrame).toBe(42);
    scheduledCallback!(532);
    expect(restoredPositions).toEqual([2400]);
  });
});
