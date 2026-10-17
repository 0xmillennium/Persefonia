import { readFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { hash, verifyDelivery, webpDimensions } from './delivery.mjs';

export async function posterPreflight(
  sourceIdentity,
  sourceManifestHash,
  directory = fileURLToPath(new URL('../../public/home-posters/', import.meta.url)),
) {
  const raw = await readFile(`${directory}/manifest.json`, 'utf8');
  const { identity, entries, ...contract } = JSON.parse(raw);
  const receipt = JSON.parse(await readFile(`${directory}/qualification.json`, 'utf8'));
  if (
    !/^[a-f0-9]{64}$/.test(identity) ||
    hash(JSON.stringify(contract)) !== identity ||
    contract.version !== 1 ||
    contract.sourceIdentity !== sourceIdentity ||
    contract.sourceManifestHash !== sourceManifestHash ||
    JSON.stringify(contract.tiers) !== '[960,1600]' ||
    JSON.stringify(contract.films) !== '["robotic-arm","tree"]' ||
    JSON.stringify(contract.poses) !== '{"opening":0,"final":191}' ||
    contract.publication !== 'Unchanged qualified WebP poses' ||
    receipt.identity !== identity ||
    receipt.manifestHash !== hash(raw) ||
    receipt.sourceIdentity !== sourceIdentity ||
    receipt.sourceManifestHash !== sourceManifestHash ||
    (await readFile(`${directory}/${identity}/manifest.json`, 'utf8')) !== raw
  )
    throw new Error('Unqualified homepage poster publication');
  const expected = [960, 1600].flatMap((tier) =>
    ['robotic-arm', 'tree'].flatMap((film) =>
      ['opening', 'final'].map((pose) => `${tier}/${film}/${pose}.webp`),
    ),
  );
  if (
    !Array.isArray(entries) ||
    entries.length !== 8 ||
    new Set(entries.map((entry) => entry.name)).size !== 8
  )
    throw new Error('Incomplete poster publication');
  for (const name of expected) {
    const entry = entries.find((entry) => entry.name === name);
    const tier = Number(name.split('/')[0]);
    if (
      !entry ||
      entry.width !== tier ||
      entry.height !== (tier * 9) / 16 ||
      !Number.isInteger(entry.bytes) ||
      entry.bytes <= 0 ||
      !/^[a-f0-9]{64}$/.test(entry.sha256)
    )
      throw new Error(`Invalid poster record: ${name}`);
    const path = `${directory}/${identity}/${name}`;
    await verifyDelivery(path, entry);
    if (webpDimensions(await readFile(path)).join(',') !== `${entry.width},${entry.height}`)
      throw new Error(`Invalid poster dimensions: ${name}`);
  }
  for (const tier of [960, 1600])
    for (const film of ['robotic-arm', 'tree'])
      if (
        (await readdir(`${directory}/${identity}/${tier}/${film}`)).sort().join(',') !==
        'final.webp,opening.webp'
      )
        throw new Error('Unexpected poster inventory');
  return identity;
}
