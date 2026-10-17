import { createServer } from 'vite';
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const frontend = new URL('../../', import.meta.url);
const assets = new URL('../../src/identity/assets/', import.meta.url);
const server = await createServer({
  configFile: false,
  root: fileURLToPath(frontend),
  server: { middlewareMode: true },
});
try {
  const { canonicalContours, canonicalGeometry } = await server.ssrLoadModule(
    '/src/identity/physics/geometry.ts',
  );
  const compactTemplate = await readFile(new URL('compact-template.svg', assets), 'utf8');
  const physicalTemplate = await readFile(new URL('physical-template.svg', assets), 'utf8');
  const mark = await readFile(new URL('mark.svg', assets), 'utf8');
  const signature = mark.match(/<!-- MARK -->([\s\S]*?)<!-- \/MARK -->/)?.[1].trim();
  if (!signature) throw new Error('Canonical lettering fragment is missing');
  const compact = (instance) =>
    compactTemplate
      .replace(/<!-- FRAGMENT:([^ ]+) -->/g, (_, id) => {
        const c = canonicalContours.find((c) => c.id === id);
        if (!c) throw new Error(`Unknown identity fragment: ${id}`);
        return `<g class="segment" data-fragment="${c.id}" data-home-x="${c.translation.x}" data-home-y="${c.translation.y}" transform="translate(${c.translation.x} ${c.translation.y})"><polygon points="${c.points.map((p) => `${p.x},${p.y}`).join(' ')}" /></g>`;
      })
      .replace('<!-- LETTERING -->', signature.replaceAll('identity-', '@@INSTANCE@@-'))
      .replaceAll('@@INSTANCE@@', instance);
  const physical = physicalTemplate.replace(/<!-- FRAGMENT:([^ ]+) -->/g, (_, id) => {
    const g = canonicalGeometry.find((g) => g.id === id);
    if (!g) throw new Error(`Unknown physical fragment: ${id}`);
    return `<polygon data-fragment="${g.id}" points="${g.vertices.map((p) => `${p.x},${p.y}`).join(' ')}" transform="translate(${g.home.x + 480} ${g.home.y + 310})" />`;
  });
  const jte =
    '@param String instance\n<!-- Generated from the canonical identity; run npm run identity:generate. -->\n' +
    compact('${instance}').replace(' xmlns="http://www.w3.org/2000/svg"', '');
  const stillPoints = canonicalGeometry.flatMap((g) =>
    g.vertices.map((p) => ({ x: p.x + g.home.x, y: p.y + g.home.y })),
  );
  const xs = stillPoints.map((p) => p.x),
    ys = stillPoints.map((p) => p.y);
  const left = Math.min(...xs),
    top = Math.min(...ys),
    width = Math.max(...xs) - left,
    height = Math.max(...ys) - top;
  const still = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${left} ${top} ${width} ${height}">${canonicalGeometry.map((g) => `<polygon fill="#eeeae1" points="${g.vertices.map((p) => `${p.x + g.home.x},${p.y + g.home.y}`).join(' ')}"/>`).join('')}</svg>\n`;
  const outputs = [
    [new URL('../../public/identity-mark.svg', import.meta.url), still],
    [new URL('compact-identity.svg', assets), compact('navigation-identity')],
    [
      new URL('../../../app/src/main/jte/site/components/compactIdentity.jte', import.meta.url),
      jte,
    ],
    [
      new URL('../../../app/src/main/jte/site/components/physicalIdentity.jte', import.meta.url),
      physical,
    ],
  ];
  for (const [target, content] of outputs) {
    if (process.argv.includes('--check')) {
      if ((await readFile(target, 'utf8')) !== content)
        throw new Error(`Stale generated identity: ${target.pathname}`);
    } else await writeFile(target, content);
  }
  console.log('Canonical identity: compact SVG, static mark and both SSR components verified.');
} finally {
  await server.close();
}
