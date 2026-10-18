import { readFile, readdir, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { hash, webpDimensions, verifyDelivery } from './delivery.mjs';

export async function loopPreflight(
  directory = fileURLToPath(new URL('../../public/home-loops/', import.meta.url)),
) {
  const raw = await readFile(`${directory}/manifest.json`, 'utf8');
  const { identity, entries, ...contract } = JSON.parse(raw);
  const receipt = JSON.parse(await readFile(`${directory}/qualification.json`, 'utf8'));
  if (
    !/^[a-f0-9]{64}$/.test(identity) ||
    !/^[a-f0-9]{64}$/.test(contract.sourceHash) ||
    hash(JSON.stringify(contract)) !== identity ||
    contract.version !== 1 ||
    contract.codec !== 'libx264' ||
    contract.preset !== 'slow' ||
    contract.crf !== 18 ||
    contract.pixelFormat !== 'yuv420p' ||
    contract.fps !== 24 ||
    contract.frames !== 192 ||
    contract.duration !== 8 ||
    contract.gop !== 48 ||
    contract.audio !== false ||
    contract.posterQuality !== 88 ||
    JSON.stringify(contract.tiers) !== '[1280,1920]' ||
    receipt.identity !== identity ||
    receipt.manifestHash !== hash(raw) ||
    receipt.sourceHash !== contract.sourceHash ||
    receipt.fullDecode !== true ||
    receipt.silent !== true ||
    receipt.frames !== 192 ||
    receipt.fps !== 24 ||
    (await readFile(`${directory}/${identity}/manifest.json`, 'utf8')) !== raw
  )
    throw new Error('Unqualified homepage loop publication');
  const expected = [1280, 1920].flatMap((width) => [
    `${width}/waterfall.mp4`,
    `${width}/waterfall.webp`,
  ]);
  if (
    !Array.isArray(entries) ||
    entries.length !== expected.length ||
    new Set(entries.map((entry) => entry.name)).size !== expected.length
  )
    throw new Error('Incomplete homepage loop publication');
  for (const name of expected) {
    const entry = entries.find((entry) => entry.name === name);
    if (
      !entry ||
      !Number.isInteger(entry.bytes) ||
      entry.bytes <= 0 ||
      !/^[a-f0-9]{64}$/.test(entry.sha256)
    )
      throw new Error(`Invalid loop record: ${name}`);
    const file = `${directory}/${identity}/${name}`,
      info = await stat(file);
    if (!info.isFile() || info.size !== entry.bytes)
      throw new Error(`Missing/changed loop delivery: ${name}`);
    const bytes = await verifyDelivery(file, entry);
    if (
      name.endsWith('.webp') &&
      webpDimensions(bytes).join(',') !==
        `${name.split('/')[0]},${(Number(name.split('/')[0]) * 9) / 16}`
    )
      throw new Error(`Wrong loop poster dimensions: ${name}`);
  }
  for (const width of [1280, 1920])
    if (
      (await readdir(`${directory}/${identity}/${width}`)).sort().join(',') !==
      'waterfall.mp4,waterfall.webp'
    )
      throw new Error(`Unexpected loop inventory: ${width}`);
  return identity;
}
