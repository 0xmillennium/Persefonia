import type { Plugin } from 'vite';
import { readFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { posterPreflight } from './home-poster-preflight.mjs';
import { videoPreflight } from './home-video-preflight.mjs';
import { loopPreflight } from './home-loop-preflight.mjs';

const videos = fileURLToPath(new URL('../../public/home-videos/', import.meta.url));

/** Production builds consume versioned delivery assets, never ignored experiment caches. */
export function homeMedia(): Plugin {
  let identity = '';
  let videoIdentity = '';
  let loopIdentity = '';
  const immutable = new Set<string>();
  return {
    name: 'home-film-publication',
    async configResolved() {
      if ((await readdir(new URL('../../public/', import.meta.url))).includes('home-films'))
        throw new Error('Retired frame sequences must not enter production delivery');
      videoIdentity = await videoPreflight();
      const manifest = JSON.parse(await readFile(`${videos}manifest.json`, 'utf8'));
      identity = await posterPreflight(manifest.sourceIdentity, manifest.sourceManifestHash);
      loopIdentity = await loopPreflight();
      for (const [group, version] of [
        ['home-posters', identity],
        ['home-videos', videoIdentity],
        ['home-loops', loopIdentity],
      ]) {
        const inventory = JSON.parse(
          await readFile(new URL(`../../public/${group}/manifest.json`, import.meta.url), 'utf8'),
        );
        for (const entry of inventory.entries) immutable.add(`${group}/${version}/${entry.name}`);
      }
      for (const name of await readdir(new URL('../../public/', import.meta.url)))
        if (/^cloudy-turquoise-(desktop|portrait)-[a-f0-9]{12}\.webp$/.test(name))
          immutable.add(name);
    },
    generateBundle(_options, bundle) {
      // Fixed public URLs and manifests are deliberately excluded.
      for (const name of Object.keys(bundle))
        if (/-[A-Za-z0-9_-]{8,}\.[a-z0-9]+$/.test(name)) immutable.add(name);
      this.emitFile({
        type: 'asset',
        fileName: '.vite/immutable-assets.json',
        source: JSON.stringify([...immutable].sort().map((name) => `/assets/${name}`)),
      });
    },
    resolveId(id) {
      if (id === 'virtual:home-media') return '\0virtual:home-media';
    },
    load(id) {
      if (id === '\0virtual:home-media')
        return `export const posterVersion=${JSON.stringify(identity)};export const videoVersion=${JSON.stringify(videoIdentity)};export const loopVersion=${JSON.stringify(loopIdentity)};`;
    },
  };
}
