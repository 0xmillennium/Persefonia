// Preparation identity is shared with cheap consumers; changing it requires publication.
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
export const preparation = {tiers:[960,1600],quality:82,encoder:'ffmpeg/libwebp',filter:'lanczos',frames:192,version:4,
  roboticArm:{quality:96,encoder:'Pillow/libwebp',filter:'ffmpeg-lanczos1600/Pillow-lanczos960',background:[10,12,13],operatorHash:createHash('sha256').update(readFileSync(new URL('./background-transform.py',import.meta.url))).digest('hex')}};
