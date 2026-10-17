import { gzipSync } from 'node:zlib';
import type { Plugin } from 'vite';

const budgets: Readonly<Record<string, { raw: number; gzip: number }>> = {
  home: { raw: 185_000, gzip: 60_000 },
  main: { raw: 10_000, gzip: 5_000 },
};

/** Deterministic release budgets; optional diagram/physics chunks stay lazy. */
export function assetBudgets(): Plugin {
  return {
    name: 'entry-asset-budgets',
    generateBundle(_options, bundle) {
      for (const entry of Object.values(bundle)) {
        if (entry.type !== 'chunk' || !entry.isEntry) continue;
        const budget = budgets[entry.name];
        if (!budget) continue;
        const visited = new Set<string>();
        let raw = 0,
          gzip = 0;
        const visit = (name: string) => {
          if (visited.has(name)) return;
          visited.add(name);
          const chunk = bundle[name];
          if (chunk?.type !== 'chunk') throw new Error(`Missing eager chunk: ${name}`);
          raw += Buffer.byteLength(chunk.code);
          gzip += gzipSync(chunk.code).length;
          for (const imported of chunk.imports) visit(imported);
        };
        visit(entry.fileName);
        if (raw > budget.raw || gzip > budget.gzip)
          this.error(
            `${entry.name} eager JS budget exceeded: ${raw} raw / ${gzip} gzip bytes (${budget.raw} / ${budget.gzip} maximum)`,
          );
        this.info(`${entry.name} eager JS: ${raw} raw / ${gzip} gzip bytes`);
      }
      for (const asset of Object.values(bundle)) {
        if (
          asset.type === 'asset' &&
          asset.fileName.endsWith('.css') &&
          Buffer.byteLength(asset.source) > 65_000
        )
          this.error(`Stylesheet budget exceeded: ${asset.fileName} (65000 bytes maximum)`);
      }
    },
  };
}
