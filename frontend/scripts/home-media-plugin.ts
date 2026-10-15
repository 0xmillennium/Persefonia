import type { Plugin } from 'vite';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { preflight } from '../experiments/scroll-film-navigator/scripts/preflight.mjs';
import { hash } from '../experiments/scroll-film-navigator/scripts/delivery.mjs';

const published = fileURLToPath(new URL('../public/home-films/', import.meta.url));

/** Production builds consume versioned delivery assets, never ignored experiment caches. */
export function homeMedia(): Plugin {
  let identity = '';
  return {
    name: 'home-film-publication',
    async configResolved() {
      const manifest = await preflight(published);
      const receipt = JSON.parse(await readFile(`${published}qualification.json`, 'utf8'));
      const manifestHash = hash(await readFile(`${published}manifest.json`));
      if (receipt.identity !== manifest.identity || receipt.manifestHash !== manifestHash
          || receipt.sourceQualificationManifestHash !== manifestHash || receipt.sourceHash !== manifest.sourceHash) {
        throw new Error('Published homepage films lack a matching qualification receipt. Run home:films:publish explicitly after film:prepare.');
      }
      identity = manifest.identity;
    },
    resolveId(id) { if (id === 'virtual:home-media') return '\0virtual:home-media'; },
    load(id) { if (id === '\0virtual:home-media') return `export const mediaVersion=${JSON.stringify(identity)};`; }
  };
}
