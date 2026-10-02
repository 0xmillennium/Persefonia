import { vi } from "vitest";

export function mediaFixture() {
  const queries = new Map<string, EventTarget & { matches: boolean }>();
  const matchMedia = vi.fn((query: string) => {
    if (!queries.has(query)) queries.set(query, Object.assign(new EventTarget(), { matches: false }));
    return queries.get(query)! as unknown as MediaQueryList;
  });
  const change = (query: string, matches: boolean): void => {
    const current = queries.get(query)!;
    current.matches = matches;
    current.dispatchEvent(new Event("change"));
  };
  return { matchMedia, queries, change };
}

export function pointerEvent(type: string, values: Partial<PointerEvent> = {}): PointerEvent {
  return Object.assign(new Event(type, { cancelable: true }), {
    pointerId: 1, pointerType: "mouse", isPrimary: true, clientX: 200, clientY: 100, relatedTarget: null
  }, values) as PointerEvent;
}
