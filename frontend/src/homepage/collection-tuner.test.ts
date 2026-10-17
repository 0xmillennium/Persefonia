import { describe, expect, it } from 'vitest';
import { collectionStop, nearestCollectionStop } from './collection-tuner';

describe('looping collection tuner', () => {
    it('cycles through complete turns in both directions without clamping or negative indices', () => {
        expect(Array.from({ length: 13 }, (_, i) => collectionStop(i, 4))).toEqual([0,1,2,3,0,1,2,3,0,1,2,3,0]);
        expect(Array.from({ length: 9 }, (_, i) => collectionStop(-i, 4))).toEqual([0,3,2,1,0,3,2,1,0]);
        expect(collectionStop(10001, 4)).toBe(1);
        expect(collectionStop(-10001, 4)).toBe(3);
    });
    it('takes the adjacent visual stop across the menu seam instead of rewinding the scale', () => {
        expect(nearestCollectionStop(3, 0, 4)).toBe(4);
        expect(nearestCollectionStop(0, 3, 4)).toBe(-1);
        expect(nearestCollectionStop(11, 0, 4)).toBe(12);
        expect(nearestCollectionStop(-8, 3, 4)).toBe(-9);
    });
    it('chooses a nearby equivalent stop for every collection after many full turns', () => {
        for (const count of [1, 3, 4, 6]) for (let current = -30; current <= 30; current++) for (let index = 0; index < count; index++) {
            const next = nearestCollectionStop(current, index, count);
            expect(collectionStop(next, count)).toBe(index);
            expect(Math.abs(next - current)).toBeLessThanOrEqual(count / 2);
        }
    });
});
