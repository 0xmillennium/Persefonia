import { readFile, writeFile, mkdir, mkdtemp, cp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { hash, webpDimensions } from '../experiments/scroll-film-navigator/scripts/delivery.mjs';

const source = fileURLToPath(new URL('../../.local/media/home/loop-video/loop video.mp4', import.meta.url));
const destination = fileURLToPath(new URL('../public/home-loops/', import.meta.url));
const probe = path => JSON.parse(execFileSync('ffprobe', ['-v', 'error', '-show_streams', '-of', 'json', path], { encoding: 'utf8' }));
const input = probe(source).streams.find(stream => stream.codec_type === 'video');
if (input.width !== 3840 || input.height !== 2160 || input.avg_frame_rate !== '24/1' || input.nb_frames !== '192')
    throw new Error('Waterfall source differs from the approved 4K/24fps/8s film; review before publishing');
const contract = { version: 1, sourceHash: hash(await readFile(source)),
    encoder: execFileSync('ffmpeg', ['-version'], { encoding: 'utf8' }).split('\n')[0],
    codec: 'libx264', preset: 'slow', crf: 18, pixelFormat: 'yuv420p', fps: 24,
    frames: 192, duration: 8, gop: 48, audio: false, tiers: [1280, 1920], posterQuality: 88 };
const identity = hash(JSON.stringify(contract));
const temporary = await mkdtemp(`${tmpdir()}/persefonia-home-loop-`);
try {
    const entries = [];
    for (const width of contract.tiers) {
        const name = `${width}/waterfall.mp4`, poster = `${width}/waterfall.webp`;
        await mkdir(`${temporary}/${width}`, { recursive: true });
        execFileSync('ffmpeg', ['-v', 'error', '-y', '-threads', '2', '-i', source, '-map', '0:v:0', '-an',
            '-vf', `scale=${width}:${width * 9 / 16}:flags=lanczos`, '-c:v', 'libx264', '-threads', '2',
            '-preset', contract.preset, '-crf', String(contract.crf), '-pix_fmt', contract.pixelFormat,
            '-g', '48', '-movflags', '+faststart', `${temporary}/${name}`]);
        const streams = probe(`${temporary}/${name}`).streams, video = streams[0];
        if (streams.length !== 1 || video.codec_name !== 'h264' || video.width !== width || video.height !== width * 9 / 16
            || video.avg_frame_rate !== '24/1' || video.nb_frames !== '192' || Number(video.duration) !== 8)
            throw new Error(`Loop delivery contract failed: ${name}`);
        execFileSync('ffmpeg', ['-v', 'error', '-xerror', '-threads', '2', '-i', `${temporary}/${name}`, '-f', 'null', '-']);
        execFileSync('ffmpeg', ['-v', 'error', '-y', '-threads', '2', '-i', `${temporary}/${name}`, '-frames:v', '1',
            '-c:v', 'libwebp', '-quality', '88', `${temporary}/${poster}`]);
        const posterBytes = await readFile(`${temporary}/${poster}`);
        if (webpDimensions(posterBytes).join(',') !== `${width},${width * 9 / 16}`) throw new Error('Invalid loop poster');
        for (const file of [name, poster]) {
            const bytes = await readFile(`${temporary}/${file}`);
            entries.push({ name: file, bytes: bytes.length, sha256: hash(bytes) });
        }
    }
    const raw = JSON.stringify({ ...contract, identity, entries }, null, 2) + '\n';
    await mkdir(`${destination}${identity}`, { recursive: true });
    for (const entry of entries) {
        const previous = await readFile(`${destination}${identity}/${entry.name}`).catch(() => null);
        if (previous && hash(previous) !== entry.sha256) throw new Error('Immutable loop publication differs; revise the encoding contract');
    }
    await cp(temporary, `${destination}${identity}`, { recursive: true });
    await writeFile(`${destination}${identity}/manifest.json`, raw);
    await writeFile(`${destination}manifest.json`, raw);
    await writeFile(`${destination}qualification.json`, JSON.stringify({ identity, manifestHash: hash(raw),
        sourceHash: contract.sourceHash, fullDecode: true, silent: true, frames: 192, fps: 24 }, null, 2) + '\n');
    console.log(`Published waterfall ${identity}: ${entries.reduce((n, entry) => n + entry.bytes, 0)} bytes across two video/poster tiers.`);
} finally { await rm(temporary, { recursive: true, force: true }); }
