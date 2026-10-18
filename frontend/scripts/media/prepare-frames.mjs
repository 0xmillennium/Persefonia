import { fileURLToPath } from 'node:url';
import { readFile, mkdir, writeFile, rename, rm, mkdtemp, copyFile } from 'node:fs/promises';
import { execFileSync, spawn } from 'node:child_process';
import { validate, output, films, sourceSequence } from './frames-source.mjs';
import { hash, inventory, runFfmpeg } from './delivery.mjs';
import { preparation } from './contracts.mjs';
import { sourceMetadata } from './frames-preflight.mjs';
const metadataBefore = await sourceMetadata();
const source = await validate();
await mkdir(output, { recursive: true });
await writeFile(`${output}source-validation.json`, JSON.stringify(source, null, 2));
const encoderVersion = execFileSync('ffmpeg', ['-version'], { encoding: 'utf8' }).split('\n')[0];
// Keep field order stable so existing immutable publications retain their identity.
const operator = fileURLToPath(new URL('./background-transform.py', import.meta.url));
const pythonVersions = JSON.parse(
  execFileSync('python3', [operator, '--versions'], { encoding: 'utf8' }),
);
const contract = {
  sourceHash: hash(JSON.stringify(source)),
  ...preparation,
  encoderVersion,
  pythonVersions,
};
const identity = hash(JSON.stringify(contract)),
  media = `${output}media`,
  destination = `${media}/${identity}`,
  lock = `${output}prepare.lock`;
await mkdir(media, { recursive: true });
try {
  await mkdir(lock);
} catch {
  throw new Error(
    `Preparation locked at ${lock}; verify no preparer is running before removing a stale lock`,
  );
}
let temporary;
try {
  await writeFile(
    `${lock}/owner.json`,
    JSON.stringify({ pid: process.pid, started: new Date().toISOString() }),
  );
  let existing;
  try {
    existing = JSON.parse(await readFile(`${media}/manifest.json`, 'utf8'));
  } catch {}
  if (existing?.identity === identity) {
    await inventory(destination, contract.tiers, films, existing.entries);
    console.log(
      `PASS: reused ${identity}; all 768 hashes, dimensions, correspondence indices and complete pixel decodes verified.`,
    );
  } else {
    temporary = await mkdtemp(`${media}/.preparing-`);
    const arm = sourceSequence('robotic-arm');
    const render = spawn(
      'python3',
      [
        operator,
        '--source',
        arm.folder,
        '--digits',
        String(arm.digits),
        '--output',
        temporary,
        '--quality',
        String(preparation.roboticArm.quality),
      ],
      { stdio: 'inherit', env: { ...process.env, OPENBLAS_NUM_THREADS: '1' } },
    );
    await new Promise((resolve, reject) => {
      render.on('error', reject);
      render.on('exit', (code) =>
        code === 0 ? resolve() : reject(new Error(`Background rendering failed: ${code}`)),
      );
    });
    for (const width of contract.tiers)
      for (const film of films) {
        if (film === 'robotic-arm') continue;
        const folder = `${temporary}/${width}/${film}`;
        await mkdir(folder, { recursive: true });
        console.log(`Preparing ${film} ${width}px, 192 original-time frames…`);
        const reusable =
          existing?.sourceHash === contract.sourceHash &&
          existing.quality === contract.quality &&
          existing.encoderVersion === contract.encoderVersion &&
          existing.filter === contract.filter &&
          existing.tiers.includes(width);
        if (reusable)
          for (let index = 1; index <= 192; index++) {
            const name = `frame_${String(index).padStart(5, '0')}.webp`,
              original = `${media}/${existing.identity}/${width}/${film}/${name}`;
            if (
              hash(await readFile(original)) !==
              existing.entries[`${width}/${film}`]?.[index - 1]?.sha256
            )
              throw new Error(`Reusable tree derivative changed: ${original}`);
            await copyFile(original, `${folder}/${name}`);
          }
        else {
          const input = sourceSequence(film);
          await runFfmpeg([
            '-y',
            '-threads',
            '2',
            '-framerate',
            '24',
            '-start_number',
            '1',
            '-i',
            `${input.folder}/frame_%0${input.digits}d.png`,
            '-frames:v',
            '192',
            '-vf',
            `scale=${width}:-1:flags=lanczos`,
            '-c:v',
            'libwebp',
            '-quality',
            String(contract.quality),
            '-threads',
            '2',
            `${folder}/frame_%05d.webp`,
          ]);
        }
      }
    const entries = await inventory(temporary, contract.tiers, films);
    const after = await validate();
    if (hash(JSON.stringify(after)) !== contract.sourceHash)
      throw new Error('Canonical source identity changed during preparation');
    const manifest = { ...contract, identity, entries };
    await writeFile(`${temporary}/manifest.json`, JSON.stringify(manifest));
    try {
      await rename(temporary, destination);
      temporary = undefined;
    } catch (error) {
      if (error.code !== 'EEXIST' && error.code !== 'ENOTEMPTY') throw error;
      await inventory(destination, contract.tiers, films, entries);
    }
    const manifestTemp = `${media}/.manifest-${process.pid}.json`;
    await writeFile(manifestTemp, JSON.stringify(manifest));
    await rename(manifestTemp, `${media}/manifest.json`);
    console.log(
      `PASS: published ${identity}, 768 fully decoded delivery frames; canonical PNG hashes unchanged.`,
    );
  }
  const sources = await sourceMetadata();
  if (JSON.stringify(sources) !== JSON.stringify(metadataBefore))
    throw new Error('Source metadata changed during full validation');
  const receipt = `${media}/validation-${identity}.json`,
    pending = `${receipt}.${process.pid}.tmp`;
  await writeFile(
    pending,
    JSON.stringify({
      identity,
      manifestHash: hash(await readFile(`${media}/manifest.json`)),
      sources,
      validatedAt: new Date().toISOString(),
    }),
  );
  await rename(pending, receipt);
} finally {
  if (temporary) await rm(temporary, { recursive: true, force: true });
  await rm(lock, { recursive: true, force: true });
}
