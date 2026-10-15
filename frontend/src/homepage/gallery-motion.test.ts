import { describe, expect, it } from 'vitest';
import { galleryIndex } from './scenes';
import { galleryDepth, galleryProgress, galleryRelease, gallerySettle } from './gallery-motion';

describe('gallery direct manipulation', () => {
    it('maps continuous drag positions through scroll plateaus without sticking or escaping either boundary', () => {
        for (const count of [2, 3, 12]) {
            for (let n = 0; n <= (count - 1) * 100; n++) {
                const index = n / 100;
                expect(galleryIndex(galleryProgress(index, count), count)).toBeCloseTo(index, 8);
            }
            expect(galleryProgress(-5, count)).toBe(0);
            expect(galleryProgress(count + 5, count)).toBe(1);
        }
        expect(galleryProgress(1, 0)).toBe(0);
        expect(galleryProgress(1, 1)).toBe(0);
    });
    it('settles a resting release to the nearest card and projects a bounded fling in either direction', () => {
        expect(galleryRelease(.7, 0, 5)).toBe(1);
        expect(galleryRelease(.3, 5, 5)).toBe(1);
        expect(galleryRelease(1.7, -5, 5)).toBe(1);
        expect(galleryRelease(0, -100, 5)).toBe(0);
        expect(galleryRelease(4, 100, 5)).toBe(4);
        for (const count of [0, 1]) expect(galleryRelease(0, 5, count)).toBe(0);
    });
    it('has frame-rate independent settling and reverses toward a new target without jumping', () => {
        const at30 = gallerySettle(0, 1, 1 / 30);
        expect(gallerySettle(gallerySettle(0, 1, 1 / 60), 1, 1 / 60)).toBeCloseTo(at30, 8);
        const reversed = gallerySettle(at30, 0, 1 / 60);
        expect(reversed).toBeGreaterThan(0);
        expect(reversed).toBeLessThan(at30);
        let value = 0;
        for (let i = 0; i < 30; i++) value = gallerySettle(value, 1, 1 / 60);
        expect(value).toBe(1);
    });
    it('keeps the centered cover sharp and bounds depth blur and fading', () => {
        expect(galleryDepth(0)).toEqual({ blur: 0, opacity: 1 });
        expect(galleryDepth(1).blur).toBeGreaterThan(0);
        expect(galleryDepth(2).opacity).toBeLessThan(galleryDepth(1).opacity);
        expect(galleryDepth(100).blur).toBe(2.4);
        expect(galleryDepth(100).opacity).toBeCloseTo(.35);
    });
});
