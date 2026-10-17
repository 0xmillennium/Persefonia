import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { consumeMedia } from './frames-preflight.mjs';
import { output } from './frames-source.mjs';
import { hash, webpDimensions } from './delivery.mjs';

// Explicit publication copies qualified opening/final poses, never a runtime sequence.
const prepared = `${output}media/`;
const destination = fileURLToPath(new URL('../../public/home-posters/', import.meta.url));
const source = await consumeMedia();
const contract = {
  version: 1,
  sourceIdentity: source.identity,
  sourceManifestHash: hash(await readFile(`${prepared}manifest.json`)),
  tiers: [960, 1600],
  films: ['robotic-arm', 'tree'],
  poses: { opening: 0, final: 191 },
  publication: 'Unchanged qualified WebP poses',
};
const identity = hash(JSON.stringify(contract));
const entries = [];
for (const tier of contract.tiers)
  for (const film of contract.films)
    for (const [pose, index] of Object.entries(contract.poses)) {
      const record = source.entries[`${tier}/${film}`][index];
      const bytes = await readFile(`${prepared}${source.identity}/${tier}/${film}/${record.name}`);
      if (
        hash(bytes) !== record.sha256 ||
        webpDimensions(bytes).join(',') !== `${tier},${(tier * 9) / 16}`
      )
        throw new Error(`Poster source failed qualification: ${tier}/${film}/${pose}`);
      const name = `${tier}/${film}/${pose}.webp`;
      const previous = await readFile(`${destination}${identity}/${name}`).catch(() => null);
      if (previous && hash(previous) !== record.sha256)
        throw new Error('Immutable poster publication differs');
      await mkdir(`${destination}${identity}/${tier}/${film}`, { recursive: true });
      await writeFile(`${destination}${identity}/${name}`, bytes);
      entries.push({
        name,
        bytes: bytes.length,
        sha256: record.sha256,
        width: tier,
        height: (tier * 9) / 16,
      });
    }
const raw = JSON.stringify({ ...contract, identity, entries }, null, 2) + '\n';
await writeFile(`${destination}${identity}/manifest.json`, raw);
await writeFile(`${destination}manifest.json`, raw);
await writeFile(
  `${destination}qualification.json`,
  JSON.stringify(
    {
      identity,
      manifestHash: hash(raw),
      sourceIdentity: source.identity,
      sourceManifestHash: contract.sourceManifestHash,
    },
    null,
    2,
  ) + '\n',
);
console.log(
  `Published eight opening/final posters: ${entries.reduce((sum, entry) => sum + entry.bytes, 0)} bytes.`,
);
