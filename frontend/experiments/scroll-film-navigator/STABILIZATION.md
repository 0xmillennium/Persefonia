# Experiment 02 — bounded stabilization and qualification

2026-10-12, Europe/Istanbul. Functional qualification is complete with the accepted local WebKit limitation, but sustained-scroll image delivery still has unresolved visible holds. This report applies the current assignment’s approvals without rewriting historical checkpoints. Production integration remains outside scope.

## A. Repository state

Starting and final branch: `feat/scroll-film-navigator`; HEAD: `a13c888e3b798dfc38cdfc5bbb5d95540d5b784b`. Starting tree was clean. The final tree contains only uncommitted Experiment 02 changes. No commit, push, history operation, dependency change, framework migration, production homepage or Experiment 01 change occurred.

Final inventory covers 79 code/config/test/script/package files, excluding documentation and immutable media: `a19c20a36c56bab4ae4a1c3adf4e09d4af9257e2fef283d52e71f9a3a1d18157`. Final runtime/tooling fingerprint and built receipt: `2a96a6e0c5bb9df6bb5531c3e0b0806f61792f20920de30ca64ba0ec9cc561cf`. See [source-freeze.json](../../test-results/scroll-film-navigator/stabilization/source-freeze.json) for the inventory and fingerprint method.

The complete gate began at inventory `e3347438ab286a14b5443f09962b0faacc238c0687604b0ce07fa0a2b7325aa2`, runtime/tooling fingerprint `dbb23ff7c190d056d296d37fa9b3e2fabf46c0b388b8605cdf70aafac29c4580`. Two fixtures were corrected during qualification and rerun afterward; reporting and BFCache-probe scripts were corrected afterward with focused verification. Application source stayed unchanged throughout this gate and its supplements. Final emitted HTML, CSS and JavaScript are **byte-identical** to the gate’s actual bundle; [built-final-identity.json](../../test-results/scroll-film-navigator/stabilization/built-final-identity.json) records the comparison and current receipt. Earlier freezes and raw failures remain available.

Changed paths, relative to the experiment:

- `src/transport.ts`: measured decode-prefetch priority.
- `vite.config.ts`: build-only media/receipt publication after bundle output.
- `playwright.config.ts`, `production.playwright.config.ts`: independent JSON/artifact paths; presentation cases included.
- `scripts/check.mjs`, new `scripts/qualification.mjs`: independent projects, truthful counts and explicit VM policy.
- New `scripts/webkit-diagnose.mjs`; `scripts/transport-short.mjs`, `scripts/review-capture.mjs`, `scripts/bfcache.mjs`: bounded diagnostics, startup evidence, residual re-press capture and native restoration probe.
- New `tests/presentation.spec.ts`, `tests/qualification.test.ts`, `tests/build-receipt.test.ts`; `tests/transport.test.ts`, `tests/performance-smoke.spec.ts`, `tests/support.ts`, `tests/resilience.spec.ts`, `tests/finalization.spec.ts`: mapping/readiness, policy, receipt, resource and corrected compatibility/reload coverage.
- `README.md`, a current-report pointer in `QUALIFICATION.md`, and this report.

Approved source PNGs, the hero film, locked Triptych analysis and historical approval reports were not edited. Cheap media consumption and final built preflight passed: unchanged source metadata, preparation configuration, inventory/output sizes and retained integrity receipt. This is **not** a new full content hash/decode campaign; metadata-preserving, same-size tampering is outside this check.

Publication: `1cc6d4a6616442c9557b9b434630f0b86ef76b2aeac91c86ccb6ff3502f79a0d`. Retained source inventory hash: `b51a02afed8301ea4a1b6dede1669ba7c183175776c9499903ccc75bd7baf100`. Full integrity provenance remains in FOLLOWUP.md. Derivatives regenerated: zero.

## B. Toolchain preflight

| Capability | Current status | Why used or omitted |
| --- | --- | --- |
| Repository / Node / TypeScript / Vitest / Vite | Executed | Inspection, corrections, units, media consumption, builds |
| Local Codex through registered MCP | Registered, reachable; safe reads succeeded | `get_active_context` returned no active project; `list_skills` confirmed selected current IDs |
| Playwright 1.57.0 | Executed | Primary browser, compositor, native input and resource evidence |
| Chrome DevTools MCP | Not invoked or reverified | Playwright/CDP supplied the needed diagnostics |
| Context7 | Registered; not invoked or reverified | No version-specific documentation question required it |
| Other design/media/animation plugins | Not invoked | No new aesthetic, media, dependency or runtime requirement |

Read actual installed `emil-design-eng`, `review-animations`, and `impeccable-design-polish` bodies, `review-animations/STANDARDS.md`, and accessibility craft. Applied interruption/continuity, final-settlement, idle-work, focus, contrast and semantic-fallback guidance. Inspected the existing hold advisory artifact from run `14631b5b-970c-44dc-8243-be7d80cedbb5` before deciding another review was unnecessary.

New advisory runs: **zero**. Reads are not specialist runs or verification. React/Motion substitutions, generic sub-300ms timing rules, physical-device prerequisites and redesign prescriptions do not override the approved cinematic hold or repository. No plugin run, source synchronization or Cloud/generative operation occurred. All operational exchanges used registered MCP; no direct HTTP, CLI, database or hidden-client route was used.

## C. WebKit investigation

[webkit-diagnostic.json](../../test-results/scroll-film-navigator/stabilization/webkit-diagnostic.json) records Playwright 1.57.0, Linux WebKit build 2227, browser label 26.0 and three independent launches per layer:

| Layer | Outcome |
| --- | --- |
| W1 empty semantic HTML, no application/graphics | 3/3 PASS |
| W2 minimal WebGL2 texture/framebuffer/readback | 3/3 PASS; complete framebuffer, expected green pixel, no GL error |
| W3 existing compatibility application | 3/3 PASS; Canvas2D opening image |
| W4 normal application | 3/3 PASS; WebGL2 opening image |

The complete strict gate then reproduced `page.goto: Page crashed` during desktop GPU navigator navigation, after 422ms. [Native log](../../test-results/scroll-film-navigator/stabilization/webkit-final-native.log) contains the automation-session CRITICAL warning and subsequent browser-wrapper exitCode 0; it provides no native child crash stack or verified trigger. The same warning occurs in successful launches. Whether the crashed page reached application/GPU initialization is unknown. `Apple GPU` metadata does not identify physical host hardware.

A single scoped follow-up completed the interrupted GPU and never-run compatibility navigator cases: **2 passed in 1.0 minute**. This completes case coverage; it does not erase the crash or establish permanent stability.

Classification: **INCONCLUSIVE — LINUX VM WEBKIT** under task §12.3, [classification artifact](../../test-results/scroll-film-navigator/stabilization/webkit-classification.json). No reproducible application-specific cause was established. No browser upgrade, guessed graphics variable, renderer downgrade or WebKit repair was applied. Desktop WebKit is non-blocking for this local closure, with the original native failure retained. WebKit touch and both built variants passed. Physical Safari/macOS/iOS remain unverified.

Strict policy remains the default. The opt-in `FILM_WEBKIT_POLICY=vm-inconclusive` requires explicit evidence and accepts only native WebKit navigation crashes. Assertions, missing reports, worker errors and other engines remain blocking. Playwright’s exact max-failure control notice is retained but distinguished from substantive errors; never-run cases are distinct from intentional skips. All WebKit definitions remain for future local/CI runs.

## D. Implementation and functional corrections

| Problem and evidenced cause | Minimal correction | Validation and remaining risk |
| --- | --- | --- |
| First failed browser command concealed later projects | Independent serial dev/built executions and reports | Complete matrix produced all twelve reports; strict raw verdict remains FAIL |
| Max-failure notice blocked the explicit VM policy; an unrun case counted as skipped | Separate control notice and substantive errors; count unrun cases | Three policy regressions passed, including assertions/other engines/missing reports remaining blocking; actual reporter classified explicitly |
| Dev-server `closeBundle` stamped current source onto stale compiled output | Build-only plugin publishes from `writeBundle` | Real Vite server-close regression passed; actual build and emitted prefetch code inspected |
| Slow decoding starved exact-current presentation behind speculative neighbor work | Measure successful load duration and prioritize anticipated ahead neighbors | Controlled 110/230ms loaders no longer completely starve; existing cache/loader/race tests pass. Integrated visible holds remain unresolved |
| Compatibility fixture expected inactivity immediately after a stroke despite approved Canvas material | Assert active/available material, then real decay and idle before restore | Corrected Chromium/Firefox cases passed; WebKit case passed in the matrix. Rendering was preserved |
| Firefox `page.reload()` fixture lost selected theme while existing native reload cases passed | Use `location.reload()` and assert navigation type `reload` | Native light-theme reload passed in Chromium/Firefox and remaining matrix projects; theme/contrast assertions retained |
| Screenshot delay could exhaust contraction residue before re-press evidence | Capture contraction state without a screenshot; reject zero-progress re-press | Final recording resumed with nonzero progress and completed; evidence tooling only |
| BFCache probe reached the restored URL but waited for a fresh load event | Wait for navigation commit, then verify restored state and `pageshow.persisted` | One failed probe retained; one corrected probe passed actual admission/restoration |

Approved navigator shaders, pigment, fluid architecture, charcoal publication, wheel calibration, theme tokens, hold clock/material/optics and reveal controller remain unchanged. No playback clock, interpolation, skipping scheduler, catch-up playback or stale substitute-image presentation was added. Valid prior assertions were preserved; obsolete fixtures were corrected against source and explicit approvals.

## E. Browser qualification

| Development project | Final local status | Latest pass / intentional skip | Corresponding built project |
| --- | --- | ---: | --- |
| Chromium | PASS | 30 / 3 | `chromium-built`: PASS, 4/4 |
| Firefox | PASS | 28 / 5 | `firefox-built`: PASS, 4/4 |
| Chromium touch | PASS | 17 / 16 | `chromium-touch-built`: PASS, 4/4 |
| Chromium tablet touch | PASS | 17 / 16 | `chromium-tablet-touch-built`: PASS, 4/4 |
| WebKit | INCONCLUSIVE | 28 / 5, plus retained native crash | `webkit-built`: PASS, 4/4 |
| WebKit touch | PASS | 13 / 20 | `webkit-touch-built`: PASS, 4/4 |

[Qualification closure](../../test-results/scroll-film-navigator/stabilization/qualification-closure.json) combines original reports with four corrected desktop fixture reruns (30.2s) and two scoped WebKit material cases. Latest unique coverage: 157 passes, 65 intentional modality/API skips, zero remaining assertion failures or never-run cases. WebKit’s overall status remains INCONCLUSIVE despite its later successful cases.

The original [strict gate summary](../../test-results/scroll-film-navigator/stabilization/gate/summary.json) and log remain FAIL/exit 1: three obsolete-fixture failures plus the native WebKit crash. They were not rewritten as green. Supplemental functional closure is **PASS WITH VM WEBKIT LIMITATION**. Production frontend TypeScript and 216 units passed. Experiment units: 71 in the gate; 72 after the added reporting regression. Final experiment TypeScript, built preflight and `git diff --check` passed.

## F. Scroll/frame presentation

Native geometry selects `round(clamp(scrollY / (scrollHeight - innerHeight)) × 191)`. Accepted 120px/three-line wheel units remain approximately one canonical frame. Native pan, keyboard, slider, continuous wheel bursts and scrollbar remain authoritative. RAF presents the latest selected decoded target at available browser cadence. The source’s 24fps timestamp rate is not a presentation cap.

Ready-media fixtures verify actual compositor colors for frames 0–6 in both directions, a latest-position jump, no autoplay and redraw when a delayed current image becomes ready without another scroll. All applicable matrix cases passed. Async race units verify obsolete completions are rejected/closed; final smoke reported zero stale presentations.

Short eight-second forward/reverse captures investigated the real held-image finding:

| Capture | Distinct reverse / forward images | Maximum captured hold reverse / forward |
| --- | ---: | ---: |
| Baseline default launch | 19 / 54 | 6491 / 4496ms |
| First measured lead; explicit SwiftShader | 90 / 61 | 696 / 1724ms |
| Final bounded lead/window; explicit SwiftShader | 22 / 24 | 1246 / 2047ms |

These separate software/capture workloads are not a clean physical A/B performance comparison. Capture p95 cadence was approximately 73–101ms. Desired/draw-submitted identity corroborated the baseline multi-second hold, so sparse JPEG sampling alone does not explain it. Baseline load medians were about 109–111ms; endpoints became ready after stopping, with no stale presentation acknowledged. See [final transport report](../../test-results/scroll-film-navigator/finalization/stabilization-prefetch-final/report.json).

**Residual sustained-scroll delivery is unresolved.** Decode throughput and software/capture cost vary; the remaining application/environment share has not been isolated. Controlled tests establish removal of complete starvation, not a smoothness guarantee. An initially invented 625ms diagnostic threshold was removed before freeze; measured browser holds remain explicit limitations. Historical readiness/hold evidence remains relevant, while mandatory delivery of every 24/48-source-frame deadline is superseded by the accepted native-scroll/RAF contract. No major transport rewrite or physical 60Hz claim follows.

Two default transport attempts stopped at graphics startup; one captured `acceleratedFailure: WebGL context lost`. Their cause remains unclassified. Explicit software captures did not alter application defaults or suppress fallback behavior.

## G. Performance and lifecycle

Guest device: VMware SVGA II `15ad:0405`. Chromium 143.0.7499.4 exposed ANGLE/Vulkan SwiftShader. No physical iGPU/mobile/Safari measurement is inferred.

Final runtime [smoke](../../test-results/scroll-film-navigator/finalization/performance/chromium/smoke.json) passed in 24.9s: 120 native RAF steps of simultaneous scroll and visible navigator, one wall-clock hold/reveal, idle settlement and disposal. Callback p50/p95/max: 149.9/200/249.9ms; current-target ready fraction: 27.5%. JS p95: 7.7ms; fetch/decode p95: 357.1ms; texture-upload p95: 6.8ms; draw-submission p95: 0.2ms. Submission timing is not GPU completion or scanout. Long-task p95 was 175ms; substantial VM/browser scheduling cost remains, with no claim that all overhead is environmental.

Six mixed samples kept renderer allocation at 16,464,896 bytes and cache within 20 images/two loads. Decoded cache estimate plateaued at 115,200,000 bytes. After two-film reveal the renderer retained 22,224,896 bytes; these are owned allocation estimates, not total process RAM or dedicated VRAM. CDP heap initial/mixed/settled/disposed: approximately 2.53/2.69/2.63/3.37MB. Detached/transient allocations and instrumentation prevent a long-term leak-free conclusion. Idle draw count stopped; disposal reduced tracked renderer/cache bytes and pending loads to zero and stopped RAF.

The earlier smoke passed in 25.0s before the prefetch change and is retained separately under `stabilization/smoke-initial/`; it does not qualify final transport readiness. No soak was required for the bounded checks.

Current source already discards queued pointer samples on suspension while aging rendered pigment by real elapsed time; the focused regression passed without a duplicate fix. Matrix coverage passed hold settlement, cancellation/capture/blur, resize/orientation/DPR, reduced motion, decode failure/retry, context loss/restoration, native history/reload, themes, touch arbitration, focus, contrast and semantic no-JS/graphics-failure fallback.

Actual built [BFCache report](../../test-results/scroll-film-navigator/bfcache/report.json): `pageshow.persisted=true`, restored `tree`, scrollY 2310 before/after, countdown hidden, no dev hook. Chromium’s default BFCache-disable flag was explicitly removed for this probe. This is real admission in that configured browser, not universal admission.

One [final recording](../../test-results/scroll-film-navigator/finalization/stabilization-final/navigator-hold.webm) and [timeline](../../test-results/scroll-film-navigator/finalization/stabilization-final/timeline.json) cover motion/decay, wheel 0→1, both scenes, cancellation/nonzero residual re-press, outward reveal, one commit through a continued press and reduced motion. Selected screenshots and state records were inspected; the entire video was not manually watched. Screenshot latency can outlast a brief reveal phase; compositor tests provide its ownership assertions. Capture used explicit SwiftShader and the same application sources as the final bundle; only tooling fingerprints changed afterward.

## H. Test efficiency

Complete functional matrices: **one**, 1104.6s (18m24.6s), with serial browser projects. An earlier attempt was interrupted after 160.5s upon discovering the false build receipt, before remaining engines/built qualification. Its log, report and freeze are retained; observed stale output justified the restart after a focused server-close regression. No third full matrix ran.

Focused work included policy/cache/loader units, ready-image/wheel/lifecycle cases, a starvation reproduction and bounded prefetch correction, four desktop fixture reruns, two WebKit material cases, and one BFCache correction/rerun. Early new-code typechecks found declaration/indexing errors; final TypeScript passed. A new diagnostic’s unsupported timing threshold was corrected; no valid existing test was relaxed to obtain green results.

Short smokes: two, 26.4/25.9s including orchestration; the second was necessary because transport source changed. Final visual recording: one, 39.2s. Transport captures: three completed short pairs and two startup stops, addressing one measured-prefetch hypothesis and its bounded window refinement. Investigation stopped with unresolved holds; no speculative architecture rewrite followed.

Media preparation/rebuilds: zero. Actual bundle builds: three, each about one second or less: first after the receipt repair; then after reporting and BFCache-script changes altered the receipt fingerprint. The latter two emitted byte-identical application assets and did not invalidate browser/runtime evidence or justify another matrix. Extended/1,800-step soaks: zero. Approximately 129MiB of current-pass evidence remains, including failure evidence; one temporary duplicate of the final video was removed after matching its hash. Historical large directories were not deleted. No mandatory toolchain-access stop or user escalation occurred.

## I. Final verdict

**NEEDS_REWORK**

Functional closure passes with the accepted inconclusive local WebKit exception, and the approved visual systems are preserved. The unresolved 1.25–2.05s sustained-scroll holds prevent a complete stabilization claim. Their cause is still uncertain; this verdict does not attribute them to an application bug, demand 60fps from SwiftShader, or require physical hardware.

Remaining work is a bounded correlation of selected-target readiness, in-flight decode/queue behavior and held-image intervals in the same VMware environment before asserting integrated delivery continuity. The current change establishes controlled starvation improvement, not a proven solution to all visible stalls. No artistic redesign, media rebuild, renderer migration or production integration is justified by this evidence.

Future WebKit CI remains useful after an eventual user-authorized push; it is additional validation, not a prerequisite for local closure or proof of physical GPU/Safari behavior. No commit or push was performed.
