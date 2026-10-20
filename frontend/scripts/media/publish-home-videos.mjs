import { readFile, writeFile, mkdir, mkdtemp, cp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { hash } from './delivery.mjs';
import { consumeMedia } from './frames-preflight.mjs';
import { preparedOutput } from './frames-source.mjs';

const output = preparedOutput();
const frames = `${output}media/`;
const destination = fileURLToPath(new URL('../../public/home-videos/', import.meta.url));
const source = await consumeMedia();
const sourceManifestHash = hash(await readFile(`${frames}manifest.json`));
const encoder = execFileSync('ffmpeg', ['-version'], { encoding: 'utf8' }).split('\n')[0];
const contract = {
  version: 1,
  sourceIdentity: source.identity,
  sourceManifestHash,
  encoder,
  codec: 'libx264',
  preset: 'fast',
  crf: 16,
  pixelFormat: 'yuv420p',
  fps: 30,
  frames: 192,
  gop: 3,
  bFrames: 0,
  threads: 2,
  tiers: [960, 1600],
  films: ['robotic-arm', 'tree'],
};
const identity = hash(JSON.stringify(contract));
const temporary = await mkdtemp(`${tmpdir()}/persefonia-home-video-`);
try {
  const entries = [];
  for (const tier of contract.tiers)
    for (const film of contract.films) {
      const records = source.entries[`${tier}/${film}`];
      for (const record of records) {
        if (
          hash(await readFile(`${frames}${source.identity}/${tier}/${film}/${record.name}`)) !==
          record.sha256
        )
          throw new Error(`Source frame integrity failed: ${tier}/${film}/${record.name}`);
      }
      await mkdir(`${temporary}/${tier}`, { recursive: true });
      const target = `${temporary}/${tier}/${film}.mp4`;
      execFileSync('ffmpeg', [
        '-v',
        'error',
        '-y',
        '-threads',
        '2',
        '-framerate',
        '30',
        '-start_number',
        '1',
        '-i',
        `${frames}${source.identity}/${tier}/${film}/frame_%05d.webp`,
        '-frames:v',
        '192',
        '-c:v',
        'libx264',
        '-threads',
        '2',
        '-preset',
        'fast',
        '-crf',
        '16',
        '-pix_fmt',
        'yuv420p',
        '-g',
        '3',
        '-keyint_min',
        '3',
        '-sc_threshold',
        '0',
        '-bf',
        '0',
        '-movflags',
        '+faststart',
        target,
      ]);
      const probe = JSON.parse(
        execFileSync(
          'ffprobe',
          [
            '-v',
            'error',
            '-select_streams',
            'v:0',
            '-show_frames',
            '-show_streams',
            '-show_entries',
            'frame=key_frame:stream=codec_name,width,height,nb_frames,r_frame_rate,duration',
            '-of',
            'json',
            target,
          ],
          { encoding: 'utf8', maxBuffer: 1024 * 1024 },
        ),
      );
      const stream = probe.streams[0];
      if (
        stream.codec_name !== 'h264' ||
        stream.width !== tier ||
        stream.height !== (tier * 9) / 16 ||
        stream.nb_frames !== '192' ||
        stream.r_frame_rate !== '30/1' ||
        Math.abs(Number(stream.duration) - 6.4) > 0.001 ||
        probe.frames.length !== 192 ||
        probe.frames.some((frame, index) => frame.key_frame !== Number(index % 3 === 0))
      )
        throw new Error(`Video seeking contract failed: ${tier}/${film}`);
      execFileSync('ffmpeg', [
        '-v',
        'error',
        '-xerror',
        '-threads',
        '2',
        '-i',
        target,
        '-f',
        'null',
        '-',
      ]);
      const bytes = await readFile(target);
      entries.push({
        name: `${tier}/${film}.mp4`,
        bytes: bytes.length,
        sha256: hash(bytes),
        keyframes: 64,
      });
    }
  const manifest = { ...contract, identity, entries };
  const raw = JSON.stringify(manifest, null, 2) + '\n';
  await mkdir(`${destination}${identity}`, { recursive: true });
  for (const entry of entries) {
    const previous = await readFile(`${destination}${identity}/${entry.name}`).catch(() => null);
    if (previous && hash(previous) !== entry.sha256)
      throw new Error('Immutable video publication differs; change the preparation contract');
  }
  await cp(temporary, `${destination}${identity}`, { recursive: true });
  await writeFile(`${destination}${identity}/manifest.json`, raw);
  await writeFile(`${destination}manifest.json`, raw);
  await writeFile(
    `${destination}qualification.json`,
    JSON.stringify(
      {
        identity,
        manifestHash: hash(raw),
        sourceIdentity: source.identity,
        sourceManifestHash,
        fullDecode: true,
        exactFrameCount: 192,
        keyframeInterval: 3,
      },
      null,
      2,
    ) + '\n',
  );
  console.log(
    `Published seekable videos ${identity}: ${entries.length} files, ${entries.reduce((sum, entry) => sum + entry.bytes, 0)} bytes.`,
  );
} finally {
  await rm(temporary, { recursive: true, force: true });
}
