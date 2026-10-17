import { describe, expect, it } from 'vitest';
import { filmCoverBox, heroOpening } from './hero-window';

describe('film-specific foreground openings', () => {
    it('keeps the original waterfall window and aligns progressively smaller windows to its right', () => {
        for (const [width, height] of [[1440, 900], [1024, 1100], [1920, 800]]) {
            const opening = heroOpening(width!, height!, 0, 'waterfall');
            expect(opening.capsules).toHaveLength(4);
            const [middle, right, farRight, last] = opening.capsules!;
            expect(middle).toEqual(opening.box);
            expect(middle![2] - middle![0]).toBeCloseTo(Math.min(width! * .56, height! * .36) * .85);
            expect(middle![1]).toBeCloseTo(height! * .16);
            expect(middle![3]).toBeCloseTo(height! * .9);
            for (const side of [right!, farRight!, last!]) {
                expect((side[1] + side[3]) / 2).toBeCloseTo((middle![1] + middle![3]) / 2);
                expect(side[2] - side[0]).toBeLessThan(middle![2] - middle![0]);
                expect(side[3] - side[1]).toBeLessThan(middle![3] - middle![1]);
                expect(side[0]).toBeGreaterThanOrEqual(24);
                expect(side[2]).toBeLessThanOrEqual(width! - 24 + .000001);
                expect(side[4]).toBeCloseTo((side[2] - side[0]) / 2);
            }
            expect(right![0]).toBeGreaterThan(middle![2]);
            expect(farRight![0]).toBeGreaterThan(right![2]);
            expect(farRight![2] - farRight![0]).toBeLessThan(right![2] - right![0]);
            expect(farRight![3] - farRight![1]).toBeLessThan(right![3] - right![1]);
            expect(last![0]).toBeGreaterThan(farRight![2]);
            expect(last![2] - last![0]).toBeLessThan(farRight![2] - farRight![0]);
            expect(heroOpening(width!, height!, 1, 'waterfall').capsules).toBeUndefined();
        }
        const phone = heroOpening(390, 850, 0, 'waterfall');
        expect(phone.capsules).toEqual([phone.box]);
    });
    it('gives the desktop three columns and two rows of equal arches, scales the gaps with the panes', () => {
        const layout = { top: 300, bottom: 480, ceiling: 116, floor: 789 };
        const initial = heroOpening(1440, 900, 0, 'tree', layout);
        expect(initial.box[1]).toBe(layout.ceiling);
        expect(initial.box[3]).toBe(layout.floor);
        const [x, y, thickness] = initial.dividers!;
        expect(x).toBe((initial.box[0] + initial.box[2]) / 2);
        expect(y - thickness / 2 - initial.box[1]).toBeCloseTo(initial.box[3] - y - thickness / 2);
        expect((initial.box[2] - initial.box[0] - thickness * 2) / 6).toBeCloseTo(initial.box[4]);
        expect(initial.box[3] - y - thickness / 2).toBeGreaterThan(initial.box[4] * 2);
        expect(thickness).toBeGreaterThanOrEqual(14);
        expect(thickness).toBeLessThanOrEqual(22);
        const halfway = heroOpening(1440, 900, .5, 'tree', layout);
        const scale = halfway.box[4] / initial.box[4];
        expect(halfway.dividers![2]).toBeCloseTo(thickness * scale);
        expect(halfway.dividers![2]).toBeGreaterThan(thickness);
        expect(heroOpening(1440, 900, 1, 'tree')).toEqual({ box: [0, 0, 1440, 900, 0], circles: [] });
        expect(heroOpening(834, 900, 0, 'tree').dividers).toBeUndefined();
        expect(heroOpening(1440, 900, 0, 'robotic-arm').dividers).toBeUndefined();
    });
    it('centers equal phone arches and preserves their separation while moving through the lower one', () => {
        const layout = { top: 382, bottom: 480, ceiling: 175, floor: 755 };
        const initial = heroOpening(390, 850, 0, 'tree', layout);
        const lower = initial.box, upper = initial.extraBox!;
        expect(upper).toBeDefined();
        expect((lower[0] + lower[2]) / 2).toBe(195);
        expect((upper[0] + upper[2]) / 2).toBe(195);
        expect(lower[2] - lower[0]).toBe(upper[2] - upper[0]);
        expect(lower[3] - lower[1]).toBe(upper[3] - upper[1]);
        expect(upper[3]).toBe(layout.top - 16);
        expect(lower[1]).toBe(layout.bottom + 16);
        expect(upper[1]).toBeGreaterThanOrEqual(layout.ceiling);
        expect(lower[3]).toBeLessThanOrEqual(layout.floor);
        const middle = heroOpening(390, 850, .5, 'tree', layout);
        expect(middle.box[0]).toBeLessThan(lower[0]);
        const scale = middle.box[4] / lower[4];
        expect(middle.box[1] - middle.extraBox![3]).toBeCloseTo((lower[1] - upper[3]) * scale);
        expect(middle.box[3] - middle.box[1]).toBeCloseTo((lower[3] - lower[1]) * scale);
        expect(heroOpening(390, 850, 1, 'tree', layout)).toEqual({ box: [0, 0, 390, 850, 0], circles: [] });
        expect(heroOpening(1440, 900, 0, 'tree', layout).extraBox).toBeUndefined();
    });
    for (const [width, height] of [[1440, 900], [834, 900], [390, 850], [850, 390]] as const) {
        it(`keeps the waterfall and its arch centered at ${width}×${height}`, () => {
            const [left, top, right, bottom] = filmCoverBox(width, height, 'waterfall');
            expect(right - left).toBeCloseTo((bottom - top) * 16 / 9);
            expect((left + right) / 2).toBeCloseTo(width / 2);
            const initial = heroOpening(width, height, 0, 'waterfall');
            expect(initial.circles).toEqual([]);
            expect((initial.box[0] + initial.box[2]) / 2).toBeCloseTo(width / 2);
            const waterfall = left + (right - left) * .51;
            expect(waterfall).toBeGreaterThan(initial.box[0]);
            expect(waterfall).toBeLessThan(initial.box[2]);
            expect(heroOpening(width, height, 1, 'waterfall').box).toEqual([0, 0, width, height, 0]);
        });
    }
    for (const [width, height] of [[1440, 900], [834, 900], [390, 850], [320, 900], [1920, 800]] as const) {
        it(`keeps constellation anchors on the fixed cover frame at ${width}×${height}`, () => {
            const [left, top, right, bottom] = filmCoverBox(width, height);
            const scale = (right - left) / 1600;
            const initial = heroOpening(width, height, 0, 'robotic-arm');
            expect(initial.circles).toHaveLength(7);
            expect(initial.circles[0]![0]).toBeCloseTo(left + 1011 * scale);
            expect(initial.circles[0]![1]).toBeCloseTo(top + 102 * scale);
            expect(new Set(initial.circles.map(circle => circle[2])).size).toBeGreaterThan(3);
            const phases = [0, .15, .5, .9];
            for (const phase of phases) {
                heroOpening(width, height, phase, 'robotic-arm').circles.forEach(([x, y, radius], index) => {
                    const scale = radius / initial.circles[index]![2];
                    expect(scale).toBeGreaterThanOrEqual(1);
                    const anchor = initial.circles[2]!, movedAnchor = heroOpening(width, height, phase, 'robotic-arm').circles[2]!;
                    expect(x - movedAnchor[0]).toBeCloseTo((initial.circles[index]![0] - anchor[0]) * scale);
                    expect(y - movedAnchor[1]).toBeCloseTo((initial.circles[index]![1] - anchor[1]) * scale);
                });
            }
            expect([...phases].reverse().map(p => heroOpening(width, height, p, 'robotic-arm')))
                .toEqual(phases.map(p => heroOpening(width, height, p, 'robotic-arm')).reverse());
            expect(left).toBeLessThanOrEqual(0);
            expect(top).toBeLessThanOrEqual(0);
            expect(right).toBeGreaterThanOrEqual(width);
            expect(bottom).toBeGreaterThanOrEqual(height);
            for (const film of ['tree', 'robotic-arm'] as const) {
                expect(heroOpening(width, height, 1, film)).toEqual({ box: [0, 0, width, height, 0], circles: [] });
            }
        });
    }
    it('preserves capsule proportions and increasing spacing throughout the shared zoom', () => {
        const initial = heroOpening(1440, 900, 0, 'waterfall').capsules!;
        for (const phase of [.1, .5, .9, .999]) {
            const moved = heroOpening(1440, 900, phase, 'waterfall').capsules!;
            const scale = moved[0]![4] / initial[0]![4];
            for (let i = 0; i < moved.length; i++) {
                expect(moved[i]![2] - moved[i]![0]).toBeCloseTo((initial[i]![2] - initial[i]![0]) * scale);
                expect(moved[i]![3] - moved[i]![1]).toBeCloseTo((initial[i]![3] - initial[i]![1]) * scale);
                if (i > 0) expect(moved[i]![0] - moved[i - 1]![2]).toBeCloseTo((initial[i]![0] - initial[i - 1]![2]) * scale);
            }
        }
    });
    it('crops the desktop tree into a narrower grid independently of the constellation circles', () => {
        const opening = heroOpening(1440, 900, 0, 'tree');
        expect(opening.circles).toEqual([]);
        const [left, top, right, bottom, radius] = opening.box;
        // The approved narrower crop retains the trunk, without fitting every branch.
        expect(left).toBeLessThan(970);
        expect(right).toBeGreaterThan(1200);
        expect(right - left).toBeLessThan(500);
        expect(top).toBeLessThan(140);
        expect(bottom).toBeGreaterThan(780);
        expect(radius).toBeGreaterThan(0);
        expect(right).toBeLessThan(1280);
    });
    it('centers the phone door on the trunk and retains the tablet framing', () => {
        for (const [width, height] of [[390, 850], [834, 900]]) {
            const [filmLeft, , filmRight] = filmCoverBox(width!, height!);
            const trunk = filmLeft + (filmRight - filmLeft) * .68;
            const [left, top, right] = heroOpening(width!, height!, 0, 'tree').box;
            if (width === 390) expect((left + right) / 2).toBeCloseTo(trunk);
            else expect(left).toBeGreaterThan(width! * .4);
            expect(top).toBeGreaterThanOrEqual(height! * .16);
            expect(trunk).toBeGreaterThan(left);
            expect(trunk).toBeLessThan(right);
        }
    });
});
