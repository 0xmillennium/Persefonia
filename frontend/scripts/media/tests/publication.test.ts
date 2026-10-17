import { expect, it } from 'vitest';
import { readFile } from 'node:fs/promises';
import { posterPreflight } from '../home-poster-preflight.mjs';
import { videoPreflight } from '../home-video-preflight.mjs';

it('qualifies video and static poster delivery without consuming source frames', async () => {
  const video = JSON.parse(
    await readFile(new URL('../../../public/home-videos/manifest.json', import.meta.url), 'utf8'),
  );
  expect(await videoPreflight()).toBe(video.identity);
  expect(await posterPreflight(video.sourceIdentity, video.sourceManifestHash)).toMatch(
    /^[a-f0-9]{64}$/,
  );
});
it('rejects posters from another preparation even if their own receipt is intact', async () => {
  const video = JSON.parse(
    await readFile(new URL('../../../public/home-videos/manifest.json', import.meta.url), 'utf8'),
  );
  await expect(posterPreflight('0'.repeat(64), video.sourceManifestHash)).rejects.toThrow(
    'Unqualified',
  );
  await expect(posterPreflight(video.sourceIdentity, '0'.repeat(64))).rejects.toThrow(
    'Unqualified',
  );
});
