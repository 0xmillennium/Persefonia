import { describe, expect, it } from 'vitest';
import { secondWordState } from './second-writing';

describe('second-writing reference motion', () => {
    it('starts as low-opacity word blobs and finishes as sharp lettering', () => {
        for (let index = 0; index < 6; index++) {
            expect(secondWordState(0, 0, index, 6, 60)).toEqual({ blur: 9, opacity: .3, y: -0, visible: true });
            expect(secondWordState(1, 0, index, 6, 60)).toEqual({ blur: 0, opacity: 1, y: -0, visible: true });
        }
    });
    it('resolves and retracts earlier words before later words', () => {
        const states = Array.from({ length: 6 }, (_, index) => secondWordState(.5, .6, index, 6, 60));
        for (let index = 1; index < states.length; index++) {
            expect(states[index - 1]!.blur).toBeLessThanOrEqual(states[index]!.blur);
            expect(states[index - 1]!.y).toBeLessThan(states[index]!.y);
        }
        expect(states[0]!.visible).toBe(false);
        expect(states[5]!.visible).toBe(true);
    });
    it('uses the inspected quintic resolution and quartic pull rather than a block slide', () => {
        const state = secondWordState(.55 * .5, .6 * .5, 0, 6, 60);
        expect(state.blur).toBeCloseTo(9 / 32);
        expect(state.y).toBeCloseTo(-6.25);
        expect(state.opacity).toBe(1);
    });
    it('is reversible, clamps endpoints and handles a single word', () => {
        const progresses = [0, .2, .65, 1];
        const forward = progresses.map(p => secondWordState(p, p, 0, 1, 28));
        expect([...progresses].reverse().map(p => secondWordState(p, p, 0, 1, 28))).toEqual([...forward].reverse());
        expect(secondWordState(-1, -1, 0, 1, 28)).toEqual(forward[0]);
        expect(secondWordState(2, 2, 0, 1, 28)).toEqual(forward[3]);
    });
});
