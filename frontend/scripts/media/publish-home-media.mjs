import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import {
  sourceArguments,
  externalSource,
  withMediaWorkspace,
  requireMediaTools,
} from './source-input.mjs';
import { sources } from './contracts.mjs';
import { hash, runFfmpeg } from './delivery.mjs';

const arguments_ = sourceArguments(['arm', 'tree']);
requireMediaTools();
const originals = {
  'robotic-arm': await externalSource(arguments_.arm),
  tree: await externalSource(arguments_.tree),
};

await withMediaWorkspace(async (workspace) => {
  const origins = {};
  for (const [film, source] of Object.entries(originals)) {
    const before = hash(await readFile(source));
    const probe = JSON.parse(
      execFileSync(
        'ffprobe',
        [
          '-v',
          'error',
          '-select_streams',
          'v:0',
          '-count_frames',
          '-show_entries',
          'stream=width,height,nb_read_frames',
          '-of',
          'json',
          source,
        ],
        { encoding: 'utf8' },
      ),
    );
    const stream = probe.streams?.[0];
    if (stream?.width !== 3840 || stream?.height !== 2160 || stream?.nb_read_frames !== '192')
      throw new Error(
        `${film}: expected an original 3840x2160 video with exactly 192 frames; no resampling is implicit`,
      );
    const { directory, digits } = sources[film];
    const folder = `${workspace}/source-frames/${directory}`;
    await mkdir(folder, { recursive: true });
    await runFfmpeg([
      '-threads',
      '2',
      '-i',
      source,
      '-map',
      '0:v:0',
      '-vsync',
      '0',
      '-start_number',
      '1',
      '-threads',
      '2',
      `${folder}/frame_%0${digits}d.png`,
    ]);
    if (hash(await readFile(source)) !== before)
      throw new Error(`${film}: original changed during extraction`);
    origins[film] = { sha256: before, width: 3840, height: 2160, frames: 192 };
  }
  await writeFile(`${workspace}/original-videos.json`, JSON.stringify(origins));
  for (const script of [
    'prepare-frames.mjs',
    'publish-home-videos.mjs',
    'publish-home-posters.mjs',
  ])
    execFileSync(process.execPath, [fileURLToPath(new URL(script, import.meta.url))], {
      stdio: 'inherit',
      env: { ...process.env, PERSEFONIA_MEDIA_WORKSPACE: workspace },
    });
});
