import { describe, expect, it } from 'vitest';
import { galleryIndex, galleryWheelProgress, openingState, regionProgress, staircase, filmRange, writingPacing, writingBeat, footerPhase, footerRowProgress, FILM_SCROLL_STEP } from './scenes';
import { filmCoverBox } from './hero-window';
import { frameAt } from '../../experiments/scroll-film-navigator/src/transport';
import { galleryCurtain } from './later-writing';
describe('native scene geometry', () => {
    it('covers wide, tall and intermediate viewports with an undistorted 16:9 film', () => {
        for (const [width, height] of [[1440, 900], [390, 850], [1920, 800], [834, 900]]) {
            const [left, top, right, bottom] = filmCoverBox(width!, height!);
            expect(left).toBeLessThanOrEqual(0);
            expect(top).toBeLessThanOrEqual(0);
            expect(right).toBeGreaterThanOrEqual(width!);
            expect(bottom).toBeGreaterThanOrEqual(height!);
            expect((right - left) / (bottom - top)).toBeCloseTo(16 / 9);
            expect(Math.min((right - left) / width!, (bottom - top) / height!)).toBeCloseTo(1);
        }
    });
    it('starts film and first-writing movement with the opening, without waiting for full screen', () => {
        const pacing = writingPacing();
        const playback = filmRange(0, pacing.hero, pacing.hero + pacing.statement + pacing.body + pacing.gap, pacing.body);
        expect(frameAt(regionProgress(0, playback.start, playback.end - playback.start))).toBe(0);
        expect(frameAt(regionProgress(FILM_SCROLL_STEP, playback.start, playback.end - playback.start))).toBe(1);
        expect(openingState(FILM_SCROLL_STEP / pacing.hero).expansion).toBeGreaterThan(0);
        expect(openingState(FILM_SCROLL_STEP / pacing.hero).expansion).toBeLessThan(1);
        expect(writingBeat(FILM_SCROLL_STEP, 0, true).exit).toBeGreaterThan(0);
        expect(openingState(.42).expansion).toBe(1);
        expect(frameAt(regionProgress(pacing.hero * .42, playback.start, playback.end - playback.start))).toBeGreaterThan(0);
    });
    it('spreads 192 frames across the measured writing sequence at one frame per scroll step', () => {
        const pacing = writingPacing();
        for (const top of [0, 100, 320]) {
            const markTop = top + pacing.hero + pacing.statement + pacing.body + pacing.gap;
            const playback = filmRange(top, pacing.hero, markTop, pacing.body);
            expect(playback.step).toBeCloseTo(FILM_SCROLL_STEP);
            const distance = playback.end - playback.start;
            for (let frame = 0; frame < 192; frame++) {
                const y = playback.start + frame * FILM_SCROLL_STEP;
                expect(frameAt(regionProgress(y, playback.start, distance))).toBe(frame);
            }
            expect(frameAt(regionProgress(playback.end - FILM_SCROLL_STEP, playback.start, distance))).toBe(190);
            expect(frameAt(regionProgress(playback.end, playback.start, distance))).toBe(191);
        }
    });
    it('keeps later writings equally readable and gives all four the same exit distance', () => {
        const pacing = writingPacing();
        expect(pacing.transition).toBe(420);
        expect(pacing.reading).toBe(720);
        expect(pacing.body).toBe(1560);
        expect(FILM_SCROLL_STEP).toBe(30);
        const tops = [0, pacing.hero, pacing.hero + pacing.statement, pacing.hero + pacing.statement + pacing.body + pacing.gap];
        tops.forEach((top, index) => {
            const initiallyOpen = index === 0;
            const exitStart = top + (initiallyOpen ? 0 : pacing.transition + pacing.reading);
            if (!initiallyOpen) for (const offset of [0, 600, pacing.reading]) {
                expect(writingBeat(top + pacing.transition + offset, top)).toEqual({ entry: 1, exit: 0 });
            }
            expect(writingBeat(exitStart + pacing.transition / 2, top, initiallyOpen).exit).toBeCloseTo(.5);
            expect(writingBeat(exitStart + pacing.transition, top, initiallyOpen).exit).toBe(1);
        });
        expect(pacing.transition / FILM_SCROLL_STEP).toBe(14);
        expect(pacing.reading / FILM_SCROLL_STEP).toBe(24);
        expect(pacing.gap).toBeGreaterThan(0);
    });
    it('clamps independently measured regions and reverses exactly', () => {
        const forward = [0, 100, 350, 800, 1200].map(y => regionProgress(y, 100, 700));
        expect(forward).toEqual([0, 0, 250 / 700, 1, 1]);
        expect([1200, 800, 350, 100, 0].map(y => regionProgress(y, 100, 700))).toEqual([...forward].reverse());
    });
    it('keeps the selected gallery item still during a readable plateau', () => {
        expect(galleryIndex(.4, 3)).toBe(0);
        expect(galleryIndex(.46, 3)).toBe(0);
        expect(galleryIndex(0, 3)).toBe(-4);
        expect(galleryIndex(1, 3)).toBe(6);
        expect(galleryIndex(.5, 3)).toBe(1);
        expect(galleryIndex(.75, 0)).toBe(0);
    });
    it('reverses the same staggered wipe columns', () => {
        expect(staircase(0)).toEqual(Array(8).fill(0));
        expect(staircase(1)).toEqual(Array(8).fill(1));
        const intermediate = staircase(.5);
        expect(intermediate[0]).toBeGreaterThan(intermediate[7]!);
    });
    it('staggers footer link gates before contact details and reverses with scroll', () => {
        const rows = (phase: number) => [0, 1, 2].map(i => footerRowProgress(phase, i));
        expect(rows(0)).toEqual([0, 0, 0]);
        const entering = rows(.2);
        expect(entering[0]).toBeGreaterThan(entering[1]!);
        expect(entering[1]).toBeGreaterThan(entering[2]!);
        expect(footerRowProgress(.4, 0, true)).toBe(0);
        expect(rows(.62)).toEqual([1, 1, 1]);
        expect(footerRowProgress(.85, 1, true)).toBe(1);
        const phases = [0, .1, .2, .5, 1];
        expect([...phases].reverse().map(rows)).toEqual(phases.map(rows).reverse());
    });

    it('completes the footer at the reachable page end with fractional section geometry', () => {
        for (const [top, height, viewport] of [[15655.125, 1688, 844], [15856.734375, 1800, 900]]) {
            const end = Math.floor(top! + height! - viewport!);
            expect(footerPhase(end, top!, height!, viewport!)).toBeGreaterThanOrEqual(1);
            expect(footerPhase(Math.floor(top!) - viewport!, top!, height!, viewport!)).toBeLessThan(0);
        }
    });

    it('takes three notches to center the first card and four to clear either end before releasing', () => {
        for (const count of [1, 3, 8, 12]) {
            let p = 1 / (count + 7);
            expect(galleryIndex(p, count)).toBe(-3);
            for (let step = 0; step < 3; step++) p = galleryWheelProgress(p, count, 1)!;
            expect(galleryIndex(p, count)).toBe(0);
            for (let step = 0; step < 4; step++) p = galleryWheelProgress(p, count, -1)!;
            expect(galleryIndex(p, count)).toBe(-4);
            expect(galleryWheelProgress(p, count, -1)).toBeNull();
            p = (count + 3) / (count + 7);
            expect(galleryIndex(p, count)).toBe(count - 1);
            for (let step = 0; step < 4; step++) p = galleryWheelProgress(p, count, 1)!;
            expect(galleryIndex(p, count)).toBe(count + 3);
            expect(galleryWheelProgress(p, count, 1)).toBeNull();
        }
        expect(galleryWheelProgress(.5, 0, 1)).toBeNull();
        expect(galleryWheelProgress(.5, 0, -1)).toBeNull();
        expect(galleryWheelProgress(.5, 5, 0)).toBeNull();
    });

    it('gives each curtain column three tenths of a viewport with staggered starts', () => {
        expect(galleryCurtain(.98, 12)).toEqual(Array(12).fill(0));
        const halfway = galleryCurtain(.98 + .15, 12);
        expect(halfway[0]).toBeCloseTo(.96875);
        expect(halfway.at(-1)).toBe(0);
        expect(galleryCurtain(.98 + .3, 12)[0]).toBe(1);
        expect(galleryCurtain(.98 + .3, 12).at(-1)).toBeLessThan(1);
        expect(galleryCurtain(.98 + .575, 12)).toEqual(Array(12).fill(1));
        expect(galleryCurtain(.98 + .425, 6)).toEqual(Array(6).fill(1));
    });

});
