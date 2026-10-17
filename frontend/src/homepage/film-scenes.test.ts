import { describe, expect, it } from 'vitest';
import { BackgroundTransport, isBackground, nextBackground } from './film-scenes';

describe('mixed playback selection', () => {
    it('cycles through all three scenes without losing the page position', () => {
        const transport = new BackgroundTransport();
        transport.setProgress(.7);
        const frame = transport.desired;
        const seen = [];
        for (let i = 0; i < 4; i++) {
            seen.push(transport.film);
            expect(transport.desired).toBe(frame);
            transport.switchFilm();
        }
        expect(seen).toEqual(['robotic-arm', 'tree', 'waterfall', 'robotic-arm']);
        expect(nextBackground('waterfall')).toBe('robotic-arm');
    });
    it('rejects stale scroll frames while allowing successive decoded loop revisions', () => {
        const transport = new BackgroundTransport();
        transport.setProgress(1);
        expect(transport.present('robotic-arm:0')).toBe(false);
        expect(transport.present('robotic-arm:191')).toBe(true);
        transport.restore('waterfall');
        expect(transport.present('robotic-arm:191')).toBe(false);
        expect(transport.present('waterfall:205')).toBe(true);
        transport.setProgress(0);
        expect(transport.present('waterfall:206')).toBe(true);
        expect(transport.presented).toBe('waterfall:206');
        expect(isBackground('waterfall')).toBe(true);
        expect(isBackground('missing')).toBe(false);
    });
});
