import { describe, expect, it } from 'vitest';
import {
  BACKGROUNDS,
  BackgroundTransport,
  backgroundPlayback,
  isBackground,
  isLoopBackground,
  isScrollFilm,
  nextBackground,
  type BackgroundDefinition,
} from './backgrounds';

describe('mixed playback selection and metadata-driven classification', () => {
  it('classifies every registered source from explicit playback metadata', () => {
    expect(BACKGROUNDS).toEqual([
      { id: 'waterfall', playback: 'loop' },
      { id: 'robotic-arm', playback: 'scroll' },
      { id: 'tree', playback: 'scroll' },
    ]);
    for (const entry of BACKGROUNDS) {
      expect(isBackground(entry.id)).toBe(true);
      expect(backgroundPlayback(entry.id)).toBe(entry.playback);
      expect(isScrollFilm(entry.id)).toBe(entry.playback === 'scroll');
      expect(isLoopBackground(entry.id)).toBe(entry.playback === 'loop');
    }
    const filteredScroll = BACKGROUNDS.map((entry) => entry.id).filter(isScrollFilm);
    const filteredLoop = BACKGROUNDS.map((entry) => entry.id).filter(isLoopBackground);
    expect(filteredScroll).toEqual(['robotic-arm', 'tree']);
    expect(filteredLoop).toEqual(['waterfall']);
  });

  it('cycles through all three scenes without losing the page position', () => {
    const transport = new BackgroundTransport();
    transport.setProgress(0.7);
    const frame = transport.desired;
    const seen = [];
    for (let i = 0; i < 4; i++) {
      seen.push(transport.film);
      expect(transport.desired).toBe(frame);
      transport.switchFilm();
    }
    expect(seen).toEqual(['waterfall', 'robotic-arm', 'tree', 'waterfall']);
    expect(nextBackground('waterfall')).toBe('robotic-arm');
  });

  it('rejects stale scroll frames while allowing successive decoded loop revisions', () => {
    const transport = new BackgroundTransport();
    transport.restore('robotic-arm');
    transport.setProgress(1);
    expect(transport.present('robotic-arm:0')).toBe(false);
    expect(transport.present('robotic-arm:191')).toBe(true);
    transport.restore('waterfall');
    expect(transport.present('robotic-arm:191')).toBe(false);
    expect(transport.present('waterfall:205')).toBe(true);
    transport.setProgress(0);
    expect(transport.present('waterfall:206')).toBe(true);
    expect(transport.presented).toBe('waterfall:206');
  });

  it('handles a second loop source and a new scroll source without hardcoded media names', () => {
    const customRegistry = [
      { id: 'glacier-scan', playback: 'scroll' },
      { id: 'aurora-canopy', playback: 'loop' },
      { id: 'tidal-basin', playback: 'loop' },
      { id: 'foundry-press', playback: 'scroll' },
    ] as const satisfies readonly BackgroundDefinition[];

    expect(isScrollFilm('glacier-scan', customRegistry)).toBe(true);
    expect(isLoopBackground('glacier-scan', customRegistry)).toBe(false);
    expect(isScrollFilm('foundry-press', customRegistry)).toBe(true);
    expect(isLoopBackground('aurora-canopy', customRegistry)).toBe(true);
    expect(isScrollFilm('aurora-canopy', customRegistry)).toBe(false);
    expect(isLoopBackground('tidal-basin', customRegistry)).toBe(true);
    expect(isScrollFilm('tidal-basin', customRegistry)).toBe(false);

    const transport = new BackgroundTransport(customRegistry);
    transport.setProgress(0.5);
    const desired = transport.desired;

    // First scroll source requires matching frame index (or opening/final poster)
    expect(transport.present('glacier-scan:0')).toBe(false);
    expect(transport.present(`glacier-scan:${desired}`)).toBe(true);

    // Switch to first loop source: accepts arbitrary revision keys
    transport.switchFilm();
    expect(transport.film).toBe('aurora-canopy');
    expect(transport.present('aurora-canopy:1')).toBe(true);
    expect(transport.present('aurora-canopy:2')).toBe(true);

    // Switch to second loop source: also classified as loop without 'waterfall' name
    transport.switchFilm();
    expect(transport.film).toBe('tidal-basin');
    expect(transport.present('tidal-basin:42')).toBe(true);
    expect(transport.present('tidal-basin:43')).toBe(true);

    // Switch to second scroll source: enforces scroll frame matching again
    transport.switchFilm();
    expect(transport.film).toBe('foundry-press');
    expect(transport.present('foundry-press:0')).toBe(false);
    expect(transport.present('foundry-press:opening')).toBe(true);
    expect(transport.present(`foundry-press:${desired}`)).toBe(true);
  });

  it('rejects invalid or unsupported identifiers across predicates and transport state', () => {
    for (const invalid of ['missing', '', 'WATERFALL', null, undefined, 42, {}]) {
      expect(isBackground(invalid)).toBe(false);
      expect(backgroundPlayback(invalid)).toBeUndefined();
      expect(isScrollFilm(invalid)).toBe(false);
      expect(isLoopBackground(invalid)).toBe(false);
    }
    const transport = new BackgroundTransport();
    transport.restore('tree');
    transport.present('tree:0');
    transport.restore('invalid-source' as never);
    expect(transport.film).toBe('tree');
    expect(transport.presented).toBe('tree:0');
    expect(nextBackground('invalid-source' as never)).toBe('waterfall');
  });
});
