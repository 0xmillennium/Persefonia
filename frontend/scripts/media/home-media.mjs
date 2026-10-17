import { readFile, mkdir, writeFile, stat } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../../../', import.meta.url));
const destination = `${root}frontend/public`;
await mkdir(destination, { recursive: true });
const receipt = {};
for (const [name, filter] of [
  ['cloudy-turquoise-desktop', 'scale=1920:-1:flags=lanczos'],
  ['cloudy-turquoise-portrait', 'scale=-1:1920:flags=lanczos,crop=1080:1920'],
]) {
  const source = `${root}.local/media/home/hero/cloudy turquoise.jpg`;
  const fingerprint = createHash('sha256')
    .update(await readFile(source))
    .digest('hex');
  const contract = { sourceSha256: fingerprint, filter, quality: 88, encoder: 'ffmpeg/libwebp' };
  const version = createHash('sha256').update(JSON.stringify(contract)).digest('hex').slice(0, 12);
  const target = `${destination}/${name}-${version}.webp`;
  let previous;
  try {
    previous = JSON.parse(await readFile(`${target}.json`, 'utf8'));
  } catch {}
  if (previous?.sourceSha256 !== fingerprint || !(await stat(target).catch(() => null))) {
    execFileSync('ffmpeg', [
      '-v',
      'error',
      '-y',
      '-i',
      source,
      '-vf',
      filter,
      '-quality',
      '88',
      target,
    ]);
    await writeFile(`${target}.json`, JSON.stringify(contract) + '\n');
  }
  receipt[name] = {
    sourceSha256: fingerprint,
    asset: `/assets/${name}-${version}.webp`,
    bytes: (await stat(target)).size,
  };
}
console.log(JSON.stringify(receipt));
