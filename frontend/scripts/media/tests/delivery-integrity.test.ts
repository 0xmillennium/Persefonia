import { it, expect } from 'vitest';
import { mkdtemp, readdir, mkdir, symlink, readFile, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { videoPreflight } from '../home-video-preflight.mjs';
import { posterPreflight } from '../home-poster-preflight.mjs';
import { loopPreflight } from '../home-loop-preflight.mjs';

// Alter only disposable copies; the immutable publication is never written.
async function corruptCopy(group: string, suffix: string) {
  const source = fileURLToPath(new URL(`../../../public/${group}/`, import.meta.url));
  const directory = await mkdtemp(`${tmpdir()}/delivery-integrity-`);
  let altered = false;
  async function copy(relative = '') {
    await mkdir(`${directory}/${relative}`, { recursive: true });
    for (const entry of await readdir(`${source}/${relative}`, { withFileTypes: true })) {
      const name = `${relative}${entry.name}`;
      if (entry.isDirectory()) await copy(`${name}/`);
      else if (!altered && name.endsWith(suffix)) {
        const bytes = await readFile(`${source}/${name}`);
        bytes[bytes.length - 1] = bytes[bytes.length - 1]! ^ 1;
        await writeFile(`${directory}/${name}`, bytes);
        altered = true;
      } else await symlink(`${source}/${name}`, `${directory}/${name}`);
    }
  }
  await copy();
  expect(altered).toBe(true);
  return directory;
}
it('video preflight rejects same-size corruption with an otherwise valid qualification receipt', async () => {
  const directory = await corruptCopy('home-videos', '.mp4');
  try {
    await expect(videoPreflight(directory)).rejects.toThrow('hash mismatch');
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
it.each(['.mp4', '.webp'])(
  'loop preflight rejects same-size corruption in %s',
  async (extension) => {
    const directory = await corruptCopy('home-loops', extension);
    try {
      await expect(loopPreflight(directory)).rejects.toThrow('hash mismatch');
    } finally {
      await rm(directory, { recursive: true, force: true });
    }
  },
);

it('poster preflight rejects same-size corruption', async () => {
  const directory = await corruptCopy('home-posters', '.webp');
  try {
    const m = JSON.parse(await readFile(`${directory}/manifest.json`, 'utf8'));
    await expect(
      posterPreflight(m.sourceIdentity, m.sourceManifestHash, directory),
    ).rejects.toThrow('hash mismatch');
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
