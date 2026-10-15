import { cp, mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { consumeMedia } from '../experiments/scroll-film-navigator/scripts/preflight.mjs';
import { hash } from '../experiments/scroll-film-navigator/scripts/delivery.mjs';

// An explicit publication step, never an implicit media preparation/build step.
// Consume the approved full-integrity receipt; copy qualified WebP bytes unchanged.
const prepared = fileURLToPath(new URL('../test-results/scroll-film-navigator/media/', import.meta.url));
const published = fileURLToPath(new URL('../public/home-films/', import.meta.url));
const manifest = await consumeMedia();
const raw = await readFile(`${prepared}manifest.json`);
const receipt = JSON.parse(await readFile(`${prepared}validation-${manifest.identity}.json`, 'utf8'));
await mkdir(published, { recursive: true });
await cp(`${prepared}${manifest.identity}`, `${published}${manifest.identity}`, { recursive: true });
await writeFile(`${published}manifest.json`, raw);
await writeFile(`${published}qualification.json`, JSON.stringify({
  identity: manifest.identity,
  manifestHash: hash(raw),
  sourceHash: manifest.sourceHash,
  sourceQualificationManifestHash: receipt.manifestHash,
  publication: 'Unchanged derivatives from the approved full-integrity preparation receipt'
}, null, 2) + '\n');
console.log(`Published ${manifest.identity}: two films, two tiers, 768 unchanged frames. No encoding or source mutation.`);
