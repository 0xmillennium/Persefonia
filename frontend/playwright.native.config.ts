import { defineConfig, devices } from '@playwright/test';
import base from './playwright.config';
import policy from './scripts/verification/selection-policy.json' with { type: 'json' };

const profile = process.env.PERSEFONIA_NATIVE_PROFILE ?? 'ordinary';
const deep = policy.deep[profile as keyof typeof policy.deep];
if (profile !== 'ordinary' && (!deep || deep.kind !== 'native'))
  throw new Error('Unknown native qualification profile');
const context = process.env.PERSEFONIA_NATIVE_PROJECT ?? 'all';
if (context !== 'all' && !policy.native.some((p) => p.project === context))
  throw new Error('Unsafe native output context');
const escape = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const matchOrdinary = (cases: readonly { file: string; title: string }[]) =>
  new RegExp(cases.map((c) => `(?:^| )${escape(c.file)} (?:.* )?${escape(c.title)}$`).join('|'));
const projects = policy.native.filter(
  (p) => profile === 'ordinary' || ('projects' in deep && deep.projects.includes(p.project)),
);
export default defineConfig({
  ...base,
  testMatch: '**/*.spec.ts',
  outputDir: `test-results/native-${profile}/${context}`,
  reporter: [
    ['list'],
    ['html', { outputFolder: `playwright-report/${profile}/${context}`, open: 'never' }],
    ...(process.env.PERSEFONIA_VERIFICATION_REPORT
      ? [['json', { outputFile: process.env.PERSEFONIA_VERIFICATION_REPORT }] as const]
      : []),
  ],
  projects: projects.map((p) => ({
    name: p.project,
    testMatch:
      profile === 'ordinary'
        ? p.cases.map((c) => `**/${c.file}`)
        : 'files' in deep
          ? deep.files.map((f) => `**/${f}`)
          : [],
    ...(profile === 'ordinary' || ('excludeOrdinary' in deep && deep.excludeOrdinary)
      ? { [profile === 'ordinary' ? 'grep' : 'grepInvert']: matchOrdinary(p.cases) }
      : {}),
    use: {
      ...devices[
        p.engine === 'firefox'
          ? 'Desktop Firefox'
          : p.engine === 'webkit'
            ? 'Desktop Safari'
            : p.context === 'touch'
              ? 'Pixel 5'
              : 'Desktop Chrome'
      ],
      browserName: p.engine as 'chromium' | 'firefox' | 'webkit',
      ...(p.engine === 'chromium' ? { channel: 'chromium' } : {}),
      viewport: p.context === 'touch' ? { width: 390, height: 850 } : { width: 1440, height: 900 },
    },
  })),
});
