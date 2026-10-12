# Experiment 02 — bounded finalization report

> This records the first checkpoint. Subsequent user feedback rejected navigator visibility and the restoration pilot, accepted scroll speed, and delegated further implementation without questions. See [FOLLOWUP.md](FOLLOWUP.md) for the later active-media and input changes; do not attribute this checkpoint’s tests/media identity to that source.

2026-10-12, Europe/Istanbul. **Verdict: NEEDS_REWORK.** Objective wheel/theme/lifecycle/workflow repairs are implemented. Smooth delivery and source-quality improvement remain incomplete; navigator artistic acceptance and the reported Brave secure-profile invisibility remain unresolved. This is a checkpoint report, not completed G8 qualification or production integration.

The task's §15 requires V1/V2 human review where artistic judgment is necessary. No more shader, transport, media or performance iterations are scheduled at this checkpoint. Test-owned servers have exited. Historical results in [QUALIFICATION.md](QUALIFICATION.md) remain historical.

## A. Repository state

Starting and final branch: `feat/scroll-film-navigator`; HEAD `fa3aefe8b79a14751c47ddfb178783627a48fb3b`. Starting tree was clean. Changes remain uncommitted; nothing was pushed. No dependencies or lockfile changes. Experiment 01, Java/Spring/JTE and production homepage sources were untouched.

Changed files, relative to this experiment unless otherwise specified:

```text
frontend/package.json
README.md
QUALIFICATION.md
FINALIZATION.md
index.html
playwright.config.ts
production.playwright.config.ts
performance.playwright.config.ts
vite.config.ts
scripts/prepare.mjs
scripts/media-config.mjs
scripts/preflight.mjs
scripts/execute.mjs
scripts/check.mjs
scripts/restoration-study.py
scripts/delivery-review.py
scripts/review-capture.mjs
scripts/transport-short.mjs
src/main.ts
src/gpu-fluid.ts
src/transport.ts
src/style.css
src/theme.ts
src/wheel.ts
tests/transport.test.ts
tests/finalization.spec.ts
tests/performance-smoke.spec.ts
tests/preflight.test.ts
tests/theme.test.ts
tests/wheel.test.ts
```

All 384 canonical 3840×2160 PNGs remain unchanged. Full preparation validated their hashes/chunk integrity and all 768 delivery hashes/dimensions/pixel decodes once, reusing the existing publication. The 14 pilot source hashes were checked again. Final cheap preflight confirmed unchanged source metadata and a matching built receipt; that last check is not another full hash verification.

Active media identity: `04e06c899fd6b8167c4bfce861ef8e3e440289f20a7b55bc4c1c78350090db52`.

Source inventory hash: `b51a02afed8301ea4a1b6dede1669ba7c183175776c9499903ccc75bd7baf100`.

Preparation remains version 3, Lanczos, WebP q82, 960/1600. No restored pilot was published or copied over active media.

## B. Diagnosis and implemented corrections

### G0 diagnostic classification

| Concern | Classification | Observed evidence |
| --- | --- | --- |
| Invisible navigator | Environment-specific limitation / not yet verified in reported profile | Normal-motion Chromium and isolated default Brave show GPU pigment. User reports Brave secure profile; that profile was not inspected or reproduced. Reduced motion and basic fallback intentionally omit pigment. |
| Wheel skips frames | Input calibration defect | Native 120px over 4200px selects about five frames. No physical detent information exists in a WheelEvent. |
| Dark robot defects | Source-media and media-delivery defects | Matched source/derivative crops show source defects compounded by lossy delivery. HTML/WebGL matched candidate crops are identical in the tested geometry. |
| Graphics pacing | Performance limitation / environment-specific limitation | Linux VM uses ANGLE SwiftShader, not accelerated integrated graphics. |
| Dark-only interface | Confirmed application defect | Existing colors did not follow selected film. |
| Nested preparation/performance | Test infrastructure defect | Dev/build/record and browser commands previously pulled expensive work into ordinary checks. |
| Queued input after suspension | Confirmed application defect in narrow causal harness | Routed old main leaves stale queued pigment after absence; current suspension discards it. |
| Alternating target readiness | Scheduling defect plus unresolved delivery limitation | Two speculative transfers can block a missing current target. Targeted preemption passes deterministic tests; captured smooth delivery remains unacceptable. |

### Navigator

The baseline pipeline already produced visible pigment with WebGL2 and normal motion: 24 injections and 42,977 changed whole-viewport pixels in a controlled gesture. Native wall-clock capture produced a smaller visible contribution under software pacing. No evidence justified a shader rewrite or relaxing reduced-motion behavior.

Added diagnostic availability/reason and explicit reduced-motion/basic-mode status. Added actual final-pixel tests on both canonical films, persistence/decay assertions and a GPU-backend assertion. Latest robot result: 48,210 changed pixels in the gesture crop, 44,542 residual pixels, then 56 after decay. These controlled-step results prove visible contribution and dissipation in the tested browser, not wall-clock artistic quality or physical-device performance.

Installed Brave 154.0.8037.98 in an isolated default automation profile produced WebGL2 pigment with 15 injections. This does not resolve the user's **Brave secure profile** report. Next useful evidence is that profile's renderer/backend, navigator reason, reduced-motion state and context errors. Do not weaken its security settings by assumption.

### Wheel and native progression

`WheelCalibration` maps common 120px and three-line units to `range / 191`, retaining magnitude and fractional remainder. It consumes only eligible vertical scene wheel input. Continuous bursts, horizontal/page/modifier/zoom input, controls, touch, keyboard and scrollbars retain native behavior. Native scroll remains the film authority.

Unit coverage verifies all 191 forward/reverse intervals, multi-unit and fractional line input. Native Chromium wheel coverage confirms 120→frame 1, 240→two additional frames, reverse→one frame back and continuous 5px input remaining native. Native touch pan/cancellation passes separately.

Physical mouse/OS settings are not verified. An isolated quantized trackpad packet can resemble a wheel; the 180ms continuous-burst heuristic cannot universally identify hardware. This is an explicit calibration tradeoff, not a claim that every event is a detent.

### Frame scheduling and remaining transport issue

Missing current targets preempt only enough unpinned speculative transfers for a film change, reversal or large jump. Ordinary progression retains ahead work. Aborted/nonabortable work retains its slot until settlement, preserving two-load ownership. A failing head-of-line unit regression passed after the fix; a further regression protects useful ahead transfers.

Captured warm traversal did not meet smooth delivery. Three causal explanations were investigated: speculative head-of-line blocking; overly aggressive preemption starving useful ahead work; and capture/software decode overhead. Guarded scheduling addresses the first two. No fourth architecture experiment was attempted.

The remaining failure and capture limitations are reported in E. Retain the narrow scheduling fix, but do not declare G3 complete. Accelerated-device observation is the next useful decision; no WebCodecs or renderer replacement is justified by these data alone.

### Robotic-arm restoration pilot

Frames 49, 97 and 139–150 only. Two reproducible candidates use the same matched 1600×900 source: A applies edge-aware filtering only to dark low-detail regions plus fixed screen-coordinate high-pass sub-LSB dither; B applies dither only. Protected regions are pixel-exact. Maximum changes are three channel levels for A and one for B. No detail recovery or higher-bit display claim.

Lossless candidates, q94 delivery of existing A outputs, matched browser comparisons and a 12-consecutive-frame forward/reverse APNG are prepared. Source defects remain; improvement is subtle and not objectively established as sufficient. Full 192-frame restoration/publication is paused for V2.

Current entire arm1600 delivery is 7,735,904 bytes. Pilot extrapolations are approximately 12.6MB for q94 A, 94.5MB for lossless A and 84.0MB for lossless B, not measured full-film outputs. Preparation took 43.9s for the 14-frame pilot; q94 delivery 5.57s. Single frame145 HTML decode/bitmap observations: scaled PNG 36.5/14.3ms, lossless A 114.4/28.9ms, q94 A 37.1/17.3ms. These single observations are not distributions or clean performance benchmarks.

### Film-linked themes

Semantic dark/light foreground, surface, secondary, border, selected, focus and status tokens follow the committed film. Growth, cancellation and mixed reveal retain the outgoing theme; commit exchanges film and theme together. Reload/history uses committed identity. Film pixels are unchanged. Unit and Chromium hold/cancel/commit/reload coverage passes; light-theme surface text meets the tested 4.5:1 minimum.

### Suspension

`GpuFluid.discardPending()` removes only unconsumed samples on suspension. Already-rendered pigment remains and ages by elapsed time. Wheel burst state resets too. Narrow browser coverage checks discarded input and retained prior dye. The old-main causal harness reports resumed stale mass 351.87 versus current zero after 4.4s; it routes old main into otherwise current modules, not the entire old application. Its old `instanceof` backend label is unreliable across the routed module identities; GPU readback supplies the actual causal measurement.

BFCache, context restoration, DPR changes and the full cross-engine lifecycle matrix were not requalified.

### Execution architecture

Normal dev/build/record uses cheap configuration, source metadata, inventory and size checks against an explicit full-validation receipt. Same-size tampering preserving metadata is outside cheap preflight's guarantee. Missing/incompatible media fails clearly. Full preparation retains hashing/decoding/publication semantics and never runs implicitly.

Functional browser selection excludes the existing extended performance test. Smoke, soak and recording are explicit commands. Built browsers consume an existing matching build and never rebuild. `film:check` consumes validated media, builds only if stale, runs functional/built and production frontend regressions, then verifies the source fingerprint. A command ledger records expensive operations; signals propagate to owned child process groups. No valid test was deleted or weakened.

## C. Test execution and cost

| Category | Current-session executions and result |
| --- | --- |
| Targeted unit | Six invocations: preflight/delivery, wheel/motion, theme/hold, expected failing transport regression, then two transport/loader validations. |
| Experiment subsystem | One complete experiment unit invocation: **57 passed across 10 files**, 2.26s. Experiment typechecking passed. |
| Focused functional browser | Five invocations: one endpoints case (4.7s); initial four finalization cases (18s, three pass/one failure); isolated pigment retry (23.1s); final four cases (38.5s, all pass); native touch-pan case (4.4s, pass). |
| Built browser | One initial selection returned `No tests found`; corrected selection executed two no-JS/reduced-motion cases, **2 passed**, 3.0s. No preparation or rebuild occurred inside either command. |
| Build | One explicit Vite build passed, transform/build reported 229ms; includes copying validated media. |
| Production frontend | Typecheck passed. Production unit/browser suites were not run at this checkpoint. Historical 216-pass result is not current evidence. |
| Full functional G8 | **0**; source/media/visual/performance acceptance remains unresolved. |
| Heavy performance | Three short smoke launches: interrupted, failed reused-server attempt, successful test-owned-server attempt. **0** extended performance workloads; **0** 1,800-step soaks. |
| Media | One full integrity reuse, 32.9s; one 14-frame restoration pilot, 44.5s ledger time; one q94 pilot, 5.7s ledger time. **0** full restored-film batches. |
| Capture | One short navigator/hold recording; initial transport capture, one failed capture analysis and one corrected guarded capture; two narrow no-CDP causal comparisons. No full recording campaign. |

The [major-command ledger](../../test-results/scroll-film-navigator/finalization/execution.jsonl) totals 274.6s of command durations, including stopped/failed work. Listed focused browser executions total about 91.7s; unit runs about 4s. These totals exclude one-off probes, server setup, advisory calls and manual inspection, and are not elapsed session time; some work overlapped.

Necessary reruns and mistakes remain explicit:

- First four-case browser run observed a basic fallback in the pigment case; cause was not captured/classified. Isolated GPU assertions and the later test-owned-server run passed. Do not claim the transient failure was definitively repaired.
- First smoke was interrupted when noticed overlapping restoration. Second failed waiting for incoming readiness; the reused server was subsequently observed dead. Exact cause of server termination remains unknown. Fresh test-owned-server smoke passed. Its completion does not imply good pacing.
- Guarded capture analysis encountered JPEGs left from the earlier folder. The diagnostic script now requires a unique output directory and caps decoded frame count; original evidence is preserved in `transport-attempt-1/`.
- A brief media browser probe overlapped the guarded capture's 1× workload. Those results are confounded, not an isolated benchmark. Sequential no-CDP old/current comparisons provided narrower causal evidence instead of another full capture loop.
- A first old-main route missed the Vite query string, so it exercised current code. The corrected routed comparison is the retained suspension evidence.

No full preparation was retriggered by edits, browser startup, build, recording or built tests. The final `film:preflight -- --built` passed. No full matrix or soak was started to fill idle time.

## D. Visual evidence and inspection

Evidence root: [finalization artifacts](../../test-results/scroll-film-navigator/finalization/). These are ignored local artifacts, not committed delivery assets.

| Requirement | Actual artifact / limits |
| --- | --- |
| V1 slow/fast, curves, crossing, decay; robot/tree legibility | [Actual desktop recording](../../test-results/scroll-film-navigator/finalization/review/navigator-hold.webm), [timeline](../../test-results/scroll-film-navigator/finalization/review/timeline.json), [contact sheet](../../test-results/scroll-film-navigator/finalization/review/navigator-contact.png). Approximately 32.6s, VP8 at 25fps. |
| Current final-pixel pigment/persistence/decay | `finalization/navigator/{robotic-arm,tree}-{painted,residue,decayed}.png` and `pixels-chromium.json`; controlled simulation, not timing qualification. |
| Wheel | `finalization/wheel-chromium.png`, `wheel-chromium.json`; recording step 02. Physical detent acceptance pending. |
| Theme change, early release, re-press, long press | Recording steps 08–13; `theme-dark-chromium.png`, `theme-light-chromium.png`. Continued press stays at one commit. |
| Outward reveal and rim | Recording contains the transition and logged REVEALING state. Extracted inspected frames show full tint, incoming film with outgoing theme, then committed light theme. The fast moving rim is not sufficiently resolved by these inspected stills to claim visual acceptance. |
| Warm 1×/2× and reversal | `transport-attempt-1/{warm-1x-forward,warm-1x-reverse,warm-2x-forward,warm-2x-reverse,alternating-targets}/`; actual compositor JPEGs/source-identity reports. Guarded forward evidence is separately in `transport-guarded/`. |
| V2 source/restored/delivery | [Matched still](../../test-results/scroll-film-navigator/finalization/restoration/bda6383ac5b326369ce98bb04b4318b9623e593b0c3105a564ac6a5318481b26/delivery-q94/comparison-00145.png), [temporal APNG](../../test-results/scroll-film-navigator/finalization/restoration/bda6383ac5b326369ce98bb04b4318b9623e593b0c3105a564ac6a5318481b26/delivery-q94/temporal-comparison.png), [matched HTML/WebGL](../../test-results/scroll-film-navigator/finalization/restoration/bda6383ac5b326369ce98bb04b4318b9623e593b0c3105a564ac6a5318481b26/browser/matched-browser-comparison.png). Original source is identically scaled for comparison. |

Inspected actual baseline source/delivery crops, navigator contact sheet, theme screenshots, matched restoration/delivery and HTML/WebGL stills, and extracted video frames. Temporal APNG metadata confirms 22 forward/reverse frames from 12 consecutive source times at 42ms; it has not received human temporal/artistic acceptance. No claim that every recording was watched continuously or every captured JPEG was visually examined.

The recording and first transport capture precede the final transport scheduling guard. Navigator shader, wheel, theme and hold code are unchanged since that capture, so those scoped visuals remain applicable; they are not final-source transport qualification. The final four-case browser evidence and built tests apply to current code.

Local Codex advice was bounded to source diagnosis and a fresh image-only critique. Actual advisory reports were inspected. The critique sees visible dark ribbons and faint light trails, with smoke/electrical characteristics rather than certain ebru acceptance; restoration improvement remains uncertain at scale. One initial project invocation failed; the existing diagnosis project inherited more context than intended, so final critique used a fresh project with a short report and selected artifacts. Advisory success is not product approval.

## E. Performance and physical-device gap

Actual environment: Linux VM, Chromium 143.0.7499.4, ANGLE Vulkan SwiftShader Device (Subzero), 1200×800 DPR1, 1600 media tier. GPU timing extension unavailable. Physical display refresh, physical integrated GPU and mobile hardware were not measured. A default graphics launch selected software rendering; this is not an Intel/AMD iGPU pass.

Short mixed workload: 120 native RAF steps changing native scroll while painting a fine-pointer path, then an actual hold/reveal. Median interval **116.6ms**, p95 **133.4ms**, maximum **216.7ms**. Target-ready fraction 25%; 22 distinct draw-submitted identities. Fetch/decode median 198.3ms, p95 260.1ms in that workload. These are poor performance results despite the test completing.

Cache is bounded at 20 entries, peak two concurrent loads. Estimated decoded pixels: 115,200,000 bytes. Renderer-owned estimate: 16,464,896 bytes before incoming texture residency, 22,224,896 after. These estimates overlap categories such as pinned decoded residents and exclude browser/driver/process overhead; they are not total RAM, process PSS or dedicated VRAM. No current process-memory/thermal/endurance trend was measured. Historical memory measurements are not reused as current measurements.

That smoke precedes the final transport guard. Solver, material, input and graphics budgets are unchanged; its software-pacing evidence applies to those components. Its decode/readiness figures do not qualify final scheduling.

Actual compositor captures:

| Capture | Distinct canonical identities | Maximum captured held interval |
| --- | ---: | ---: |
| Initial warm 1× reverse | 125 | 254.5ms |
| Initial warm 1× forward | 19 | 6995.2ms |
| Initial warm 2× reverse | 4 | 3475.0ms |
| Initial warm 2× forward | 4 | 2918.1ms |
| Initial alternating target | 3 | 214.9ms |
| Guarded warm 1× forward, confounded | 3 | 7911.7ms |
| Guarded warm 2× forward | 2 | 3888.4ms |

Warm means an initial traversal plus a bounded moving decode window, not all 192 frames resident. JPEG capture cadence/overhead can undersample 24/48fps, and the 1× guarded result overlapped another browser probe. No smooth-presentation pass can be inferred from desired frame indices or draw submissions.

Sequential 8s 1× forward comparisons without CDP or another browser showed 73 distinct draw submissions with routed HEAD transport versus 83 current; fetch/decode medians were about 82/81ms, p95 about 100/103.5ms. This does not establish screen presentation or a clean full-application historical comparison. It shows instrumentation and software decode budget materially affect results; it does not erase the delivery failure.

No solver quality reduction, hidden iGPU disablement, loading animation or destructive blur was used to improve metrics. No new rendering technology was introduced. Shared-memory targets still need measured evidence.

### Narrow physical acceptance procedure

1. Use an actual accelerated Intel/AMD integrated-GPU machine and an ordinary mobile device. Record browser/OS, device, viewport/DPR, display refresh and actual graphics backend. If the backend is SwiftShader/llvmpipe, keep it a software result.
2. Run the existing validated publication with normal motion. On the reported Brave secure profile, open Diagnostics and record renderer/backend, navigator reason, reduced-motion state and context errors before changing settings.
3. Test real mouse detents, fast multi-detent/reverse movement, trackpad bursts, touch pan, keyboard/slider and scroll restoration. Confirm source identities and visible frames together.
4. Run one short smoke and warm 1×/2× traversal with ordinary navigator motion; separate recordings/profiling if their overhead matters. Check full tint, early release/re-press, outward rim and a continuous press beyond commit on both films.
5. Record pacing/readiness and whole-process/shared-memory observations where available. Report degraded/failed results; do not infer mobile acceptance from a synthetic desktop pointer in a touch viewport. Run the deferred explicit soak only after source/media/visual decisions are stable.

## F. Qualification matrix

| Requirement | Status | Scope / remaining gap |
| --- | --- | --- |
| Isolation, two canonical films, immutable source media | PASS | Inspected scope and one full integrity reuse; no publication changes. |
| Cheap preflight / explicit prep / separated performance | PASS | Config/inventory fixtures, command graph and observed startup/build/built checks. |
| Native canonical mapping and synthetic discrete wheel | PASS | Unit endpoints and focused native browser input. |
| Physical mouse detents and trackpad diversity | NOT VERIFIED | OS/hardware acceptance required. |
| Warm 1×/2× visible smooth delivery | FAIL | Captured software environment; final reverse capture not repeated. |
| Navigator visible final pixels and decay | PASS | Tested WebGL2 normal-motion Chromium; isolated default Brave visible. |
| Reported Brave secure-profile invisibility | NOT VERIFIED | Exact settings/backend unknown; no causal fix claimed. |
| Ebru material, refraction and light-film legibility | PARTIAL | Organic visible material; faint light trails and artistic V1 pending. |
| Source-aware restoration and delivery improvement | PARTIAL | Protected 14-frame pilot only; V2 pending, active defects unchanged. |
| Film-linked semantic themes | PASS | Scoped unit/Chromium transition/reload and contrast assertions. |
| Growth/cancel/re-press/single press transition | PARTIAL | Existing units plus current recording/scoped checks; full browser matrix deferred. |
| Center-out reveal and luminous-rim visual quality | PARTIAL | Existing implementation retained; recording insufficient for final artistic rim acceptance. |
| Stale unconsumed samples after suspension | PASS | Narrow causal comparison and final browser regression. |
| Full lifecycle/context/BFCache/cross-engine resilience | NOT VERIFIED | No complete current matrix. |
| No-JS poster / reduced motion / keyboard / self-hosted built boundary | PASS | Two current built Chromium cases; broader engines deferred. |
| Native touch pan/cancellation | PASS | Focused emulated Chromium-touch case; no physical mobile claim. |
| Accelerated integrated GPU and ordinary mobile performance | NOT VERIFIED | Physical environment unavailable locally. |
| Software mixed-workload pacing | FAIL | 116.6ms median, not acceptable interactive pacing. |
| Full G8, production unit regression and endurance | NOT VERIFIED | Deliberately deferred; no skips presented as passes. |
| Human V1/V2 approval | NOT VERIFIED | Mandatory review at this checkpoint. |

## G. Verdict and required decisions

**NEEDS_REWORK.** Known media-fidelity and delivered-continuity requirements remain unsatisfied. This cannot be labeled ready merely because focused functional tests pass. Physical GPU performance and the reported secure-profile invisibility also remain unverified.

**V1 decision:** review the recording/contact sheet for turquoise visibility, organic marbling, persistence, refraction and dark/light legibility. Approve the current material or identify the specific visual change needed; current light trails are faint. No speculative shader iteration is pending.

**V2 decision:** review matched source/A/B/q94 comparisons and the temporal APNG. Choose a candidate only if its improvement is material and preserves details/temporal stability, or reject the pilot. Size/decode costs above are estimates/pilot observations. No full batch is authorized by a tool critique.

**Transport/performance escalation:** expected smooth 24/48-source-frame traversal; observed software/capture holds and slow mixed RAF pacing. Classification: graphics environment plus unresolved application delivery limitation; three causal approaches and retained changes are documented in B/E. Next action is one accelerated physical-device run, then a focused fix only if new evidence identifies its bottleneck. No further local speculative repairs are queued.

After these decisions and any justified targeted repairs, freeze current source/media, run the rebuilt full functional gate once, and perform the separately required soak/physical evidence. Nothing in this report authorizes production homepage integration.
