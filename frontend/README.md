# Persefonia frontend

The frontend enhances Spring MVC/JTE pages. Server-rendered HTML owns content,
links, routing and metadata; Vite supplies self-hosted assets under the existing CSP.
The homepage's current composition, media and interaction contracts are implemented
in TypeScript, CSS, native video, SVG and WebGL. No separate demo application is
required to build or test the production site.

## Ownership

- `src/main.ts`: ordinary public pages and returning navigation covers.
- `src/home.ts`: cinematic homepage entry.
- `src/mermaid-loader.ts`: explicitly requested diagram content; kept out of the
  homepage's eager dependency graph.
- `src/homepage`: measured timeline, film presentation, writing, gallery/tuner,
  globe, mobile navigation and footer. `home-experience.ts` composes these features.
  `timeline/scroll-navigation.ts` owns wheel/glide arbitration and cancellation;
  `gallery/handoff.ts` owns default collection re-entry and history travel;
  `film/gestures.ts` owns capture, touch, dwell and keyboard input. Film media and
  rendering policy remain in the film controller.
- `src/identity`: shared canonical geometry, compact brand choreography and the
  interactive physics engine. The physics tests retain historical regression
  fixtures because the current engine must continue to satisfy their invariants.
- `src/navigation`: cross-page transition behavior.
- `src/shared`: small utilities with concrete multiple consumers. It may not
  import feature implementations.
- `src/styles/home/index.css`: explicit cascade ordering. Base SSR presentation,
  enhanced scene composition, responsive and reduced-motion states are distinct.
  Preserve the import order when changing ownership. Feature-specific responsive
  rules can remain in their owning stylesheets.
- `scripts`: build-time validation, canonical generation and explicit offline media
  preparation. Production browser code must not import scripts or tests.
- `e2e`: real application browser tests and their deterministic fixtures/support.
  Unit tests live beside the feature they qualify or in its `tests` directory.

`npm run check:boundaries` validates application reachability and dependency
boundaries from the three Vite entries, rejects production import cycles (including
type dependencies), and limits cross-homepage-feature imports to named model/pacing
contracts. Physics value types live in `identity/physics/model.ts`; calculation
ownership is unchanged. It is a module check; exported-symbol
use, dynamic DOM selectors and publication semantics still need code review.

## Commands

Use the Node version declared by `package.json` and install with `npm ci`.

```sh
npm run typecheck          # Application, tooling and browser/unit test projects
npm run test:unit          # Unit contracts, excluding the two 100-seed stress pipelines
npm run test:unit:stress   # Expensive deterministic physics pipelines
npm run test:unit:all      # Complete unit suite; also used by the Gradle/CI gate
npm run check:boundaries
npm run check:generated    # Canonical SVG/JTE fragments and geographic mask
npm run format:check
npm run build
npm run check             # Routine non-browser checks and the Vite build
npm run check:full        # Same checks plus the complete stress pipelines
```

For a focused fix, run only its affected test file or case, for example
`npm run test:unit -- src/homepage/film/tests/model.test.ts` or
`npm run test:browser -- e2e/homepage-touch-hold.spec.ts --project=mobile-390 --grep "settled hold"`.
A focused fix does not require repeating the full application/browser matrix.
The full Gradle/CI unit gate still includes every deterministic seed.

Physics regression pipelines include many deterministic seeds and take longer
than lightweight UI calculations. Their functional timeout is not a runtime
performance acceptance threshold. Test reports are written under ignored
`test-results/`.

Browser tests need the built application and repository SQL/media fixtures:

```sh
./gradlew :app:browserCheck                       # From the repository root
PERSEFONIA_BROWSER_URL=http://127.0.0.1:18080 npm run test:browser
PERSEFONIA_BROWSER_URL=http://127.0.0.1:18080 npm run test:browser:cross
```

The repository browser runner starts an isolated application/database, loads
`e2e/fixtures/content.sql` and fixture media, and runs the production browser
checks. Main projects cover desktop/tablet/mobile Chromium; the additional
configuration exercises semantic content, fallback behavior and gallery controls
in Firefox and WebKit. Touch CDP probes belong to Chromium; browser emulation
does not replace physical-device qualification. CI runs the Chromium viewport
projects and the Firefox/WebKit checks in separate jobs against one verified JAR;
`CI / Gate` requires the whole matrix. The runner accepts `PERSEFONIA_BROWSER_SUITE`
(`all`, `chromium`, `cross`), Playwright arguments such as `--project`, and optional
`PERSEFONIA_BROWSER_PORT` / `PERSEFONIA_BROWSER_ARTIFACT_DIR` for isolated local runs.

## Media and generated identity

`public/home-posters`, `home-videos` and `home-loops` contain qualified, immutable
versioned delivery assets. Eight opening/final WebP posters provide static presentation when native video
is unavailable; reduced motion can use them without starting the scroll videos.
Full image sequences are offline source material, never runtime delivery. Ordinary builds validate published
receipts and inventory without source footage, FFmpeg or `.local/`.

Explicit preparation consumes immutable local inputs and writes its working
cache outside frontend source and disposable test reports:

- Scroll source frames: `.local/media/home/scroll-frames/`.
- Prepared frames/receipt: `.local/media/home/prepared-scroll/media/`.
- Hero photograph and loop source: the approved inputs under `.local/media/home/`.

```sh
npm run media:images
npm run media:frames:prepare
npm run media:posters:publish
npm run media:videos:publish
npm run media:loop:publish
npm run identity:generate
```

Media publication is an intentional operation, never an implicit build step.
Preserve the source hashes, encoding contracts and qualification semantics in
ADR 0036. Do not change a published version's bytes. Source frame preparation
requires FFmpeg and the Python packages imported by `background-transform.py`;
the operator bytes are part of its immutable contract.

Canonical identity geometry and lettering generate the compact SVG, static public-page mark and both
SSR identity fragments. Edit their source/templates, run generation, and review
output changes. Generated output, geographic source/license data and delivery
receipts must not be discarded as unused trial assets.

## Behavior preservation

Keep exact wheel-stop reversibility, native fine scrolling/touch, input ownership,
fixed film framing, the five-notch gallery entrance, default Projects and tuner
selection behavior. Verify changes across intermediate widths, short viewports,
orientation changes, keyboard operation, reduced motion, media/WebGL failure and
Back/BFCache restoration. Navigator state continues beneath the gallery curtain;
quick reverse scrolling must reveal the still-active interaction.

Page resource cleanup is owned by `DisposalScope`; motion presentation is reconciled
with the current preference during scene updates. Suspension is distinct from
destruction. Feature animation loops sleep when idle or unavailable. Performance
changes need representative before/after measurements and retained visual quality.

## Delivery and runtime budgets

Release builds hash the actual published video/poster bytes. This is separate
from full codec/pixel qualification; ordinary edits need no decoder or source footage.
The build also emits an exact immutable URL inventory consumed by the application:
only those qualified release assets receive one-year public immutable caching.
HTML, theme CSS, fixed manifests and revocable CMS media retain their own policies.
Production compression applies to CSS, JavaScript and SVG, never video; verify the
external ingress preserves these headers and native video byte ranges.

The `assetBudgets` build plugin caps the home eager JavaScript closure at
185,000 raw / 60,000 estimated gzip bytes, the main entry at 10,000 / 5,000,
and each stylesheet at 65,000 raw bytes.
Dynamic imports do not count as eager delivery. These budgets prevent accidental
startup regressions; they are not hardware FPS or wire-transfer guarantees.
The browser performance check permits at most 12 layouts during its 90-step film
scroll sample. Task time, RAF intervals and JavaScript heap are retained as
diagnostics; they do not measure compositor FPS or decoder/GPU allocations.
Physics prepares near the fourth writing; reduced motion retains the static mark.
Media resolution follows the actual 16:9 cover box with hysteresis. A preparing
replacement cannot remove the last usable presentation. Readiness waits are bounded
and explicit background gestures retry failures. Automatic next-film preparation
is limited to the single incoming background after pointer dwell or hold intent.

## Gallery cover curation

Use Media Library cover variants for real projects/publications; keep the important
subject inside the central 4:3 crop and avoid baked-in small text. Review covers with
the existing hover/focus title gradient and two-line ellipsis, at phone and desktop
sizes. Full titles remain in semantic links. The static collection-specific artwork
is an intentional fallback, never a substitute for fabricated project screenshots.
Publication labels, collection membership and public URLs remain server-owned.

Public pages share SSR identity/navigation and retain their editorial surface. A direct
visit loads only the small marker reader; cover animation loads only for a valid
one-use navigation marker. Source-frame preparation is retained for explicit video
encoding, and video publication consumes its qualified local output directly.
Keep source media and matching receipts in a recoverable archive outside delivery;
ordinary builds need no archive, `.local/` or FFmpeg. See ADR 0036.

## Server-owned destinations and covers

Shared public footers and the homepage footer expose CV only when the selected
language has an available public PDF. Architecture is an ordinary published,
listed PAGE with editorial slug `architecture`; no reserved page or empty
destination is created. Its source and Discovery route must both be eligible.
Content relationships are read through Content Publishing's public navigation
query, with a targeted active-series membership query. Related tag/series links
and project tags use the existing public route resolvers; unresolved destinations
stay absent (project tag names remain readable). Public route/source mutations
remain owned by their existing contexts.

Cover presentation emits only the publicly eligible, stored variants supplied by
Media Library, with actual width descriptors and native automatic lazy-image sizing. A conservative
viewport fallback also covers the wider unenhanced reading layout. The medium
variant remains the default when available; missing larger variants do not remove
a usable smaller cover. No client-side asset URL guessing is involved.

Accepted English cinematic passages and film announcements are marked `lang=en`;
the SSR copy model continues to own localized collection and navigation labels.
The base hero explicitly keeps its decorative environment behind semantic text.
Its visual fallback test compares rendered pixels with and without the headline,
so a DOM-visible heading hidden behind an image cannot pass.
