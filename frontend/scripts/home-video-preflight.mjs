import { readFile, readdir, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { hash } from '../experiments/scroll-film-navigator/scripts/delivery.mjs';

export async function videoPreflight(sourceIdentity, sourceManifestHash, directory = fileURLToPath(new URL('../public/home-videos/', import.meta.url))) {
    const raw = await readFile(`${directory}/manifest.json`, 'utf8');
    const manifest = JSON.parse(raw);
    const { identity, entries, ...contract } = manifest;
    const receipt = JSON.parse(await readFile(`${directory}/qualification.json`, 'utf8'));
    if (!/^[a-f0-9]{64}$/.test(identity) || hash(JSON.stringify(contract)) !== identity
        || contract.sourceIdentity !== sourceIdentity || contract.sourceManifestHash !== sourceManifestHash
        || contract.version !== 1 || contract.codec !== 'libx264' || contract.pixelFormat !== 'yuv420p'
        || contract.crf !== 16 || contract.preset !== 'fast' || contract.frames !== 192 || contract.fps !== 30
        || contract.gop !== 3 || contract.bFrames !== 0 || receipt.identity !== identity || receipt.manifestHash !== hash(raw)
        || receipt.sourceIdentity !== sourceIdentity || receipt.sourceManifestHash !== sourceManifestHash
        || receipt.fullDecode !== true || receipt.exactFrameCount !== 192 || receipt.keyframeInterval !== 3
        || await readFile(`${directory}/${identity}/manifest.json`, 'utf8') !== raw) throw new Error('Unqualified homepage video publication');
    const expected = [960, 1600].flatMap(tier => ['robotic-arm', 'tree'].map(film => `${tier}/${film}.mp4`));
    if (!Array.isArray(entries) || entries.length !== expected.length || new Set(entries.map(entry => entry.name)).size !== expected.length)
        throw new Error('Incomplete homepage video publication');
    for (const name of expected) {
        const entry = entries.find(entry => entry.name === name);
        if (!entry || !Number.isInteger(entry.bytes) || entry.bytes <= 0 || !/^[a-f0-9]{64}$/.test(entry.sha256) || entry.keyframes !== 64)
            throw new Error(`Invalid video delivery record: ${name}`);
        const info = await stat(`${directory}/${identity}/${name}`);
        if (!info.isFile() || info.size !== entry.bytes) throw new Error(`Missing/changed video: ${name}`);
    }
    for (const tier of [960, 1600]) {
        if ((await readdir(`${directory}/${identity}/${tier}`)).sort().join(',') !== 'robotic-arm.mp4,tree.mp4')
            throw new Error(`Unexpected video inventory: ${tier}`);
    }
    return identity;
}
