import { defineConfig } from 'vite';
import { homeMedia } from './scripts/media/home-media-plugin';
import { assetBudgets } from './scripts/checks/budgets';

export default defineConfig({
  base: '/assets/',
  plugins: [homeMedia(), assetBudgets()],
  server: {
    host: '127.0.0.1',
  },
  build: {
    manifest: true,
    outDir: '../app/build/generated/vite/static/assets',
    emptyOutDir: true,
    assetsDir: '.',
    // The diagram renderer (mermaid) is only fetched on the dedicated
    // mermaid-loader entry, so its weight never reaches the main page bundle.
    // Keep this budget visible. Some optional diagram engines (notably ELK)
    // still exceed it; they are fetched only by the dedicated diagram entry.
    // Keep Mermaid's cyclic module graph under automatic chunking: forcing
    // package/size groups can read its bindings before initialization.
    chunkSizeWarningLimit: 650,
    rollupOptions: {
      // Preserve module initialization across chunks without wrapping every
      // module or pulling diagram vendors into the eager homepage closure.
      experimental: { onDemandWrapping: true },
      input: {
        main: 'src/main.ts',
        home: 'src/home.ts',
        'mermaid-loader': 'src/mermaid-loader.ts',
      },
      output: {
        strictExecutionOrder: true,
        codeSplitting: {
          groups: [
            {
              // Keep the shared Vite dynamic-import helper independent of
              // diagram vendors; otherwise homepage globe imports pull Mermaid in.
              name(id: string) {
                return id.includes('vite/preload-helper') ? 'runtime-preload' : null;
              },
              priority: 100,
              minSize: 0,
            },
          ],
        },
      },
    },
  },
});
