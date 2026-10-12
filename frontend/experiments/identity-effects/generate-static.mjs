import { createServer } from "vite";
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

// Reuse the approved contours at build time; the physics implementation never
// enters this experiment's browser bundle. Earlier experiments remain immutable.
const server = await createServer({ configFile: false,
  root: fileURLToPath(new URL("../..", import.meta.url)), server: { middlewareMode: true } });
try {
  const { canonicalContours } = await server.ssrLoadModule("/experiments/segmented-identity/geometry.ts");
  const segments = canonicalContours.map(c =>
    `            <g class="segment" data-fragment="${c.id}" data-home-x="${c.translation.x}" data-home-y="${c.translation.y}" transform="translate(${c.translation.x} ${c.translation.y})"><polygon points="${c.points.map(p => `${p.x},${p.y}`).join(" ")}" /></g>`).join("\n");
  const template = await readFile(new URL("page.html", import.meta.url), "utf8");
  const mark = await readFile(new URL("assets/mark.svg", import.meta.url), "utf8");
  const signature = mark.match(/<!-- MARK -->([\s\S]*?)<!-- \/MARK -->/u)?.[1];
  if (!signature) throw new Error("Expected the custom mark asset to contain a signature fragment");
  const html = template.replace("<!-- SEGMENTS -->", segments).replace("<!-- M-MARK -->", signature.trim());
  const target = new URL("index.html", import.meta.url);
  if (process.argv.includes("--check")) {
    if (await readFile(target, "utf8") !== html) throw new Error("Static geometry/mark is stale; run npm run effects:geometry");
  } else await writeFile(target, html);
} finally { await server.close(); }
