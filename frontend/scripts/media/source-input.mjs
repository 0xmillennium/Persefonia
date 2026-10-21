import { realpath, stat, mkdtemp, rm } from 'node:fs/promises';
import { resolve, relative, isAbsolute, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';
import { spawnSync } from 'node:child_process';
import { parseArgs } from 'node:util';

const repository = fileURLToPath(new URL('../../../', import.meta.url));

export function sourceArguments(names, args = process.argv.slice(2)) {
  const { values } = parseArgs({
    args,
    options: Object.fromEntries(names.map((name) => [name, { type: 'string' }])),
  });
  for (const name of names)
    if (!values[name])
      throw new Error(`Missing --${name}: supply an original file outside the repository`);
  return values;
}

export async function externalSource(path) {
  const source = await realpath(resolve(path));
  const inside = relative(await realpath(repository), source);
  if (!inside || (!inside.startsWith(`..${sep}`) && inside !== '..' && !isAbsolute(inside)))
    throw new Error('Original media must be outside the repository');
  if (!(await stat(source)).isFile()) throw new Error('Original media must be a regular file');
  return source;
}

export function mediaWorkspace() {
  const workspace = process.env.PERSEFONIA_MEDIA_WORKSPACE;
  if (!workspace || !isAbsolute(workspace))
    throw new Error(
      'Use npm run media:publish -- --arm /original/arm.mp4 --tree /original/tree.mp4',
    );
  return workspace;
}

export async function withMediaWorkspace(action) {
  const directory = await mkdtemp(resolve(tmpdir(), 'persefonia-media-'));
  try {
    return await action(directory);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
}

export function requireMediaTools(environment = process.env) {
  for (const tool of ['ffprobe', 'ffmpeg']) {
    const result = spawnSync(tool, ['-version'], { env: environment, encoding: 'utf8' });
    if (result.error || result.status !== 0) throw new Error(`Missing media prerequisite: ${tool}`);
  }
}
