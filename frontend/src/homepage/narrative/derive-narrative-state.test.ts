import { expect, test } from "vitest";
import { deriveNarrativeState } from "./derive-narrative-state";
import { PROVISIONAL_REGION_INTERVALS } from "./regions";

test("each exact boundary belongs to the following region", () => {
  for (const interval of PROVISIONAL_REGION_INTERVALS) {
    const state = deriveNarrativeState(interval.start);
    expect(state.activeRegion).toBe(interval.id);
    expect(state.activeRegionProgress).toBe(0);
  }
});

test("representative interior progress selects its region and linear local progress", () => {
  for (const interval of PROVISIONAL_REGION_INTERVALS) {
    const midpoint = (interval.start + interval.end) / 2;
    const state = deriveNarrativeState(midpoint);
    expect(state.activeRegion).toBe(interval.id);
    expect(state.activeRegionProgress).toBe(0.5);
  }
});

test("the final endpoint completes the final mental-world region", () => {
  const state = deriveNarrativeState(1);
  expect(state.activeRegion).toBe("final");
  expect(state.activeRegionProgress).toBe(1);
  expect(state.regions.final).toBe("ACTIVE");
});

test("outside finite input is normalized before deriving state", () => {
  expect(deriveNarrativeState(-1)).toEqual(deriveNarrativeState(0));
  expect(deriveNarrativeState(2)).toEqual(deriveNarrativeState(1));
});

test.each([NaN, Infinity, -Infinity])("non-finite input %s cannot produce a state", progress => {
  expect(() => deriveNarrativeState(progress)).toThrow(RangeError);
});

test("region lifecycle follows current progress rather than visit history", () => {
  expect(deriveNarrativeState(0.437).regions).toEqual({
    threshold: "MEMORY",
    work: "MEMORY",
    writing: "MEMORY",
    research: "ACTIVE",
    architecture: "UNVISITED",
    return: "UNVISITED",
    person: "UNVISITED",
    final: "UNVISITED"
  });
});

test("exactly one region is active at endpoints, boundaries, interiors, and across a sweep", () => {
  const samples = [
    0,
    1,
    ...PROVISIONAL_REGION_INTERVALS.flatMap(({ start, end }) => [start, (start + end) / 2]),
    ...Array.from({ length: 1001 }, (_, index) => index / 1000)
  ];

  for (const progress of samples) {
    const state = deriveNarrativeState(progress);
    expect(Object.values(state.regions).filter(lifecycle => lifecycle === "ACTIVE")).toHaveLength(1);
    expect(state.regions[state.activeRegion]).toBe("ACTIVE");
    expect(state.activeRegionProgress).toBeGreaterThanOrEqual(0);
    expect(state.activeRegionProgress).toBeLessThanOrEqual(1);
  }
});

test("repeated derivation produces deeply equivalent semantic state", () => {
  expect(deriveNarrativeState(0.437)).toEqual(deriveNarrativeState(0.437));
});

test("different traversal paths produce the same state at the same progress", () => {
  const paths = [
    [0, 0.1, 0.2, 0.437],
    [0, 0.437],
    [1, 0.8, 0.6, 0.437]
  ];
  const endpoints = paths.map(path => path.map(deriveNarrativeState).at(-1));

  expect(endpoints[0]).toEqual(endpoints[1]);
  expect(endpoints[1]).toEqual(endpoints[2]);
  expect(endpoints[0]).toEqual(deriveNarrativeState(0.437));
});

test("backscroll restores future regions to unvisited", () => {
  const path = [0, 0.75, 0.25].map(deriveNarrativeState);
  const state = path.at(-1);

  expect(state).toEqual(deriveNarrativeState(0.25));
  expect(state?.regions).toEqual({
    threshold: "MEMORY",
    work: "MEMORY",
    writing: "ACTIVE",
    research: "UNVISITED",
    architecture: "UNVISITED",
    return: "UNVISITED",
    person: "UNVISITED",
    final: "UNVISITED"
  });
});
