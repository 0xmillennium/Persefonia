import { createServer } from "vite";
import { fileURLToPath } from "node:url";
import { readFile, writeFile } from "node:fs/promises";

const root = fileURLToPath(new URL(".", import.meta.url));
const server = await createServer({ configFile: false, root, server: { middlewareMode: true } });
try {
  const { canonicalGeometry } = await server.ssrLoadModule("/geometry.ts");
  const polygons = canonicalGeometry.map(g =>
    `      <polygon data-fragment="${g.id}" points="${g.vertices.map(p => `${p.x},${p.y}`).join(" ")}" transform="translate(${g.home.x + 480} ${g.home.y + 310})" />`).join("\n");
  const points = canonicalGeometry.flatMap(g => g.vertices.map(p => ({ x: p.x + g.home.x + 480, y: p.y + g.home.y + 310 })));
  const left = Math.min(...points.map(p => p.x)) - 4, top = Math.min(...points.map(p => p.y)) - 4;
  const width = Math.max(...points.map(p => p.x)) - left + 4, height = Math.max(...points.map(p => p.y)) - top + 4;
  const identity = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${left} ${top} ${width} ${height}" fill="#eeeae1" role="img" aria-label="Segmented 0x identity">\n${polygons}\n</svg>\n`;
  const html = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex, nofollow">
  <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self' ws://127.0.0.1:4178; img-src 'self'; object-src 'none'; base-uri 'self'">
  <title>Experiment 01 — Segmented 0x Identity Physics</title>
  <link rel="stylesheet" href="./style.css">
  <link rel="icon" type="image/svg+xml" href="./identity.svg">
</head>
<body>
  <main>
    <header><h1>Experiment 01 — Segmented 0x Identity Physics</h1><p>0xmillennium · isolated engineering study</p></header>
    <svg class="stage" viewBox="0 0 960 620" role="img" aria-labelledby="mark-title mark-description">
      <title id="mark-title">Segmented 0x identity</title>
      <desc id="mark-description">Two neighboring abstract forms, made of five and four separate geometric fragments.</desc>
${polygons}
    </svg>
    <p class="caption">Move a mouse through the mark. Momentum disperses the fragments; distributed home gravity draws them back. Touch and reduced-motion retain the static identity.</p>
    <div class="controls">
      <button id="reset" type="button">Reset</button>
      <button id="pause" type="button" aria-pressed="false">Pause</button>
      <label><input id="anchors" type="checkbox"> Show home anchors</label>
      <span id="status" role="status">Static identity</span>
    </div>
    <noscript><p>The canonical identity is shown. Physics needs JavaScript; this experiment is separate from the public homepage.</p></noscript>
    <details><summary>Debug telemetry</summary><pre id="telemetry"></pre></details>
  </main>
  <script type="module" src="./main.ts"></script>
</body>
</html>
`;
  for (const [name, content] of [["index.html", html], ["identity.svg", identity]]) {
    const target = new URL(name, import.meta.url);
    if (process.argv.includes("--check")) {
      if (await readFile(target, "utf8") !== content) throw new Error(`${name} is stale; regenerate from canonical geometry`);
    } else await writeFile(target, content);
  }
} finally { await server.close(); }
