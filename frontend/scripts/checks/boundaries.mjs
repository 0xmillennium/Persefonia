import ts from 'typescript';
import { featureEdgeAllowed, importCycles } from './boundary-rules.mjs';
import { readdir, readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../../', import.meta.url));
const source = path.join(root, 'src');
async function files(directory) {
  return (
    await Promise.all(
      (await readdir(directory, { withFileTypes: true })).map((entry) => {
        const name = path.join(directory, entry.name);
        return entry.isDirectory() ? files(name) : name;
      }),
    )
  ).flat();
}
const production = (await files(source)).filter(
  (p) =>
    /\.(ts|mjs)$/.test(p) &&
    !p.endsWith('.d.ts') &&
    !p.endsWith('.test.ts') &&
    !p.includes('/tests/'),
);
const options = {
  moduleResolution: ts.ModuleResolutionKind.Bundler,
  allowJs: true,
  resolveJsonModule: true,
};
const graph = new Map();
const errors = [];
for (const file of production) {
  const text = await readFile(file, 'utf8');
  const imports = ts.preProcessFile(text, true, true).importedFiles.map((item) => item.fileName);
  const targets = [];
  for (const specifier of imports) {
    if (specifier.startsWith('virtual:') || /\.(css|svg|bin)(\?|$)/.test(specifier)) continue;
    const resolved = ts.resolveModuleName(specifier, file, options, ts.sys).resolvedModule;
    if (!resolved) {
      errors.push(`${path.relative(root, file)}: unresolved ${specifier}`);
      continue;
    }
    const target = path.resolve(resolved.resolvedFileName);
    if (target.includes('/node_modules/')) continue;
    if (
      !target.startsWith(source + path.sep) ||
      target.includes('/tests/') ||
      target.endsWith('.test.ts')
    ) {
      errors.push(`${path.relative(root, file)} imports development-only code: ${specifier}`);
    }
    if (
      file.startsWith(path.join(source, 'shared') + path.sep) &&
      !target.startsWith(path.join(source, 'shared') + path.sep)
    ) {
      errors.push(`${path.relative(root, file)}: shared code depends on a feature`);
    }
    if (
      file.startsWith(path.join(source, 'identity') + path.sep) &&
      target.startsWith(path.join(source, 'homepage') + path.sep)
    ) {
      errors.push(`${path.relative(root, file)}: identity depends on homepage composition`);
    }
    if (
      !featureEdgeAllowed(
        path.relative(source, file).split(path.sep).join('/'),
        path.relative(source, target).split(path.sep).join('/'),
      )
    )
      errors.push(
        `${path.relative(root, file)} imports another feature implementation: ${specifier}`,
      );
    targets.push(target);
  }
  graph.set(file, targets);
}
for (const cycle of importCycles(graph))
  errors.push(
    `Production import cycle: ${cycle.map((file) => path.relative(source, file)).join(' -> ')}`,
  );
const reachable = new Set();
function visit(file) {
  if (reachable.has(file)) return;
  reachable.add(file);
  for (const target of graph.get(file) ?? []) visit(target);
}
for (const entry of ['main.ts', 'home.ts', 'mermaid-loader.ts']) visit(path.join(source, entry));
for (const file of production)
  if (!reachable.has(file))
    errors.push(`Unreachable production module: ${path.relative(root, file)}`);
try {
  await readdir(path.join(root, 'experiments'));
  errors.push('Retired experiments directory is still present');
} catch (error) {
  if (error.code !== 'ENOENT') throw error;
}
if (errors.length) throw new Error(errors.join('\n'));
console.log(
  `Production boundaries: ${production.length} modules reachable from the three application entries; no development dependencies.`,
);
