import { it, expect } from 'vitest';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { preflight } from '../frames-preflight.mjs';
import { preparation } from '../contracts.mjs';
import { hash } from '../delivery.mjs';
it('Cheap consumption rejects incompatible and incomplete media without full decoding', async () => {
  const directory = await mkdtemp(`${tmpdir()}/film-preflight-`);
  try {
    const contract = { sourceHash: 'a'.repeat(64), ...preparation, encoderVersion: 'fixture' };
    const identity = hash(JSON.stringify(contract));
    await mkdir(`${directory}/${identity}`);
    const raw = JSON.stringify({ ...contract, identity, entries: {} });
    await writeFile(`${directory}/manifest.json`, raw);
    await writeFile(`${directory}/${identity}/manifest.json`, raw);
    await expect(preflight(directory)).rejects.toThrow('Incomplete');
    await writeFile(
      `${directory}/manifest.json`,
      JSON.stringify({ ...contract, version: 999, identity, entries: {} }),
    );
    await expect(preflight(directory)).rejects.toThrow('identity');
    const incompatible = { ...contract, quality: 1 };
    const changed = hash(JSON.stringify(incompatible));
    await writeFile(
      `${directory}/manifest.json`,
      JSON.stringify({ ...incompatible, identity: changed, entries: {} }),
    );
    await expect(preflight(directory)).rejects.toThrow('quality');
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
it('Cheap preflight accepts a complete hash inventory but rejects missing and resized output', async () => {
  const directory = await mkdtemp(`${tmpdir()}/film-preflight-`);
  try {
    const contract = { sourceHash: 'a'.repeat(64), ...preparation, encoderVersion: 'fixture' },
      identity = hash(JSON.stringify(contract)),
      entries: Record<string, unknown[]> = {};
    for (const tier of preparation.tiers)
      for (const film of ['robotic-arm', 'tree']) {
        const folder = `${directory}/${identity}/${tier}/${film}`;
        await mkdir(folder, { recursive: true });
        entries[`${tier}/${film}`] = await Promise.all(
          Array.from({ length: 192 }, async (_, i) => {
            const name = `frame_${String(i + 1).padStart(5, '0')}.webp`;
            await writeFile(`${folder}/${name}`, 'x');
            return {
              name,
              width: tier,
              height: (tier * 9) / 16,
              bytes: 1,
              sha256: hash(Buffer.from('x')),
            };
          }),
        );
      }
    // Byte integrity is separate from full pixel/codec decoding.
    const raw = JSON.stringify({ ...contract, identity, entries });
    await writeFile(`${directory}/manifest.json`, raw);
    await writeFile(`${directory}/${identity}/manifest.json`, raw);
    expect((await preflight(directory)).identity).toBe(identity);
    const frame = `${directory}/${identity}/960/tree/frame_00001.webp`;
    await writeFile(frame, 'y');
    await expect(preflight(directory)).rejects.toThrow('hash mismatch');
    await writeFile(frame, 'changed');
    await expect(preflight(directory)).rejects.toThrow('changed output');
    await rm(frame);
    await expect(preflight(directory)).rejects.toThrow('Wrong output count');
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
