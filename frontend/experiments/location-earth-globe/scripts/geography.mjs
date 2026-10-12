import { readFile, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { surfaceSamples } from "../src/sampling.mjs";

const source = new URL("../assets/ne_110m_land.geojson", import.meta.url);
const destination = new URL("../assets/land-mask.bin", import.meta.url);
const bytes = await readFile(source);
const hash = createHash("sha256").update(bytes).digest("hex");
if (hash !== "9e0729ee253ca7d7a5c4ae9395fb1902264c5377c52e224d13dd85010e2835d9") {
  throw new Error("Natural Earth source differs from the pinned provenance");
}
const data = JSON.parse(bytes.toString("utf8"));
const polygons = data.features.flatMap(({ geometry }) => {
  if (geometry.type === "Polygon") return [geometry.coordinates];
  if (geometry.type === "MultiPolygon") return geometry.coordinates;
  throw new Error(`Unsupported geography: ${geometry.type}`);
}).map(rings => {
  const outer = rings[0];
  return { rings, minX: Math.min(...outer.map(p => p[0])), maxX: Math.max(...outer.map(p => p[0])),
    minY: Math.min(...outer.map(p => p[1])), maxY: Math.max(...outer.map(p => p[1])) };
});

function insideRing(x, y, ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [ax, ay] = ring[i], [bx, by] = ring[j];
    if ((ay > y) !== (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) inside = !inside;
  }
  return inside;
}
const samples = surfaceSamples();
const mask = Buffer.alloc(Math.ceil(samples.length / 8));
let land = 0;
samples.forEach(({ latitude: y, longitude: x }, i) => {
  const isLand = polygons.some(p => x >= p.minX && x <= p.maxX && y >= p.minY && y <= p.maxY &&
    insideRing(x, y, p.rings[0]) && !p.rings.slice(1).some(r => insideRing(x, y, r)));
  if (isLand) { mask[i >> 3] |= 1 << (i & 7); land++; }
});
if (process.argv.includes("--check")) {
  if (!(await readFile(destination)).equals(mask)) throw new Error("Stale land mask; run globe:geography");
} else {
  await writeFile(destination, mask);
}
console.log(`Natural Earth: ${samples.length} surface samples, ${land} land, ${mask.length} mask bytes; ${process.argv.includes("--check") ? "verified" : "written"}`);
