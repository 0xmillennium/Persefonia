# Experiment 03 — interactive location globe

An independent reference-faithful 3D globe for **0xmillennium**, adapted to
the existing turquoise pigment family and exactly one **Istanbul, Türkiye**
marker. This does not change the production homepage or Experiment 02.
Human browser review is the remaining visual acceptance step.

## Run

From `frontend/`, using the existing installed tooling:

```sh
npm run globe:dev
```

Open http://127.0.0.1:4280/. For the self-contained built version:

```sh
npm run globe:check
npm run globe:preview
```

Open http://127.0.0.1:4281/. Ports 4190/4191 were rejected by Firefox/WebKit
as restricted service ports and are deliberately avoided.

```sh
npm run globe:typecheck
npm run globe:test
npm run globe:geography          # regenerate pinned offline geographic mask
npm run globe:build              # verifies mask before building
GLOBE_SOFTWARE_GPU=1 npm run globe:browser -- --project=chromium
GLOBE_SOFTWARE_GPU=1 GLOBE_BUILT=1 npm run globe:browser -- --project=chromium --project=firefox --project=chromium-touch --project=webkit
GLOBE_SOFTWARE_GPU=1 node experiments/location-earth-globe/scripts/review.mjs
```

`GLOBE_REUSE_SERVER=1` explicitly reuses an already running matching dev or
preview server. Software GPU selection only affects Chromium test launch;
it does not change application rendering or Firefox/WebKit configuration.
The review script defaults to the built preview. `GLOBE_URL` can select a
running development server. Evidence goes into ignored
`frontend/test-results/location-earth-globe/`.

## Controls and accessibility

- Mouse/pen: hover illuminates the actual front sphere intersection without
  steering. Press freezes rotation and removes the glow; two-axis drag turns
  and tilts; recent horizontal speed transfers momentum on release. Pitch
  remains where released. Drag continues outside the globe through capture.
- Touch: horizontal swipe turns; vertical gestures belong to native page
  scrolling. Tilt buttons provide vertical adjustment. A near-marker tap
  reveals the compact card; touch has no simulated hover illumination.
- Focus the globe and use arrow keys for immediate turning/tilting. Native
  controls have visible focus and at least 44 px height. Tab leaves normally.
- **Find Istanbul** explicitly centers Istanbul, returns to the initial
  inclination, pauses rotation, and reveals its card. Resume is a separate
  control. This accessible shortcut is our adaptation, not observed reference
  behavior. Location content is also permanently present as semantic HTML.
- **Pause rotation** persists through suspension and preference changes.
  Reduced motion turns off autonomous spin and inertia while preserving
  manual exploration; the preference responds live. The panel has no
  transition in reduced motion. Escape cancels input and retracts the card.
- No JavaScript, unavailable WebGL 2, failed geographic loading, or context
  loss retains readable Istanbul information. Inactive controls are disabled.

## Approved reference and evidence boundaries

Required reference recordings are under the ignored
`frontend/experiments/sample-site-analysis/` directory:

- `earth location effect.mp4`: 852×778, 30 fps, 14.566633 s. Main 1–9 s:
  rapid turns and inclination changes; 10.5 s: detailed sample lattice;
  10–12.8 s: surface hover; about 13 s: cursor leaves.
- `location special conditions effect.mp4`: 856×802, 30 fps, 10.333300 s.
  About 0.25–2.75 s: card follows slowly turning geographic marker;
  about 2.75–3 s: withdrawal; 4–10 s: moving glow, edge misses, pole/edge
  approach and continued modest rotation.
- `triptych-current-interactive-page-analysis-locked.md`, Section 13:
  intrinsic-axis spin, press suspension, limited retained pitch, release
  momentum and marker proximity.

Both videos were decoded and visually inspected through time-sampled
sequences, with finer samples around panel withdrawal and changing pitch.
They contain no input-event trace. Precise button timestamps, original
engine, camera, speeds and shader equations are unknown. Exact numerical
values below are implementation choices, not measured original constants.
Detailed local interpretation and approval provenance are in
`.local/evidence/location-earth-globe/REFERENCE.md`.

## Renderer and modules

Direct **WebGL 2**, TypeScript and native browser APIs; no new dependency.
Three.js was considered and recommended by the initial adviser for general
camera/transform plumbing. The selected scene needs one immutable buffer,
one VAO, one custom program and one draw call. The bounded shared analytical
camera/transform math was verified directly, making the small renderer
appropriate here. Neither renderer guarantees fidelity without visual review.

- `sampling.mjs` + offline geography script: 180 latitude rows, about 360
  samples around the equator, longitude density proportional to circumference.
- `geometry.ts`: geographic mapping, weak perspective camera, sphere hit,
  shared `Rx(pitch) × Ry(spin)` transform and marker projection.
- `material.ts`: compact antialiased luminous samples forming curved rows;
  geographic land intensity, subdued ocean/rear, local front-surface falloff.
  Turquoise starts from Experiment 02's `.08/.85/.69` pigment family, without
  importing its material, renderer or state.
- `orientation.ts`: unrestricted wrapped yaw, pitch ±0.85 radians, base
  intrinsic speed +0.075 rad/s, analytical exponential excess decay rate 1.6/s.
- `gesture.ts` / `input.ts`: one owned pointer, press/drag/idle states,
  trailing velocity window, 100 ms stale cutoff, directional touch intent,
  bounded interpolation for sparse events after an initial hold,
  cancellation, capture and keyboard input. Minimum fling speed 0.2 rad/s;
  bounded maximum 7 rad/s. No pitch spring or vertical fling.
- `location.ts` / `presentation.ts`: typed singleton location, shared
  geographic projection, perspective horizon, proximity hysteresis (32/52
  CSS px), noninteractive attached card, explicit accessible reveal.
- `lifecycle.ts`: one RAF owner, fresh clock on restoration, hidden/offscreen
  suspension, on-demand frames while static. `experiment.ts` connects modules
  and owns observers/listeners/context restoration/disposal.

Active elapsed time is integrated analytically, including slow/irregular
frames. Suspension resets the clock rather than discarding active frame time.
Graphics availability and page lifecycle are separate gates. Press temporarily
hides a pinned marker card; a second touch tap closes it, while drag/cancel
clears the pin. Surface hover keeps the colored `.22/.76/.65` highlight;
point coverage shrinks near the grazing horizon to limit merged rim samples.

GPU density is capped at DPR 2 and 1,400 px along the longest drawing-buffer
dimension. Immutable buffer data is uploaded once per initialization; shaders
are compiled once per initialization/restoration. No per-frame metadata,
readbacks, capability queries, resource allocation or geometry rebuilding.
Buffers/VAO/program are explicitly deleted; temporary shaders are deleted
after linking. No textures or framebuffers are required.

Dev mode, or an explicit `?inspect=1` in the built experiment, exposes a small
state inspector plus disposal/context-loss probes. State inspection reports
CPU state and submitted draws; it does not query the GPU or establish physical
presentation. The context-loss extension handle is cached outside RAF.

## Assets, differences and limits

[Geographic provenance](assets/PROVENANCE.md) records the pinned public-domain
Natural Earth source, hash and offline pipeline. Runtime uses only a local
5,157-byte mask. No live map API, external font, CDN or reference media is
loaded. The standalone built experiment is tested under a strict self-hosted
CSP. Application security configuration is unchanged.

Deliberate differences: turquoise replaces violet; Istanbul replaces the
reference US location; the card omits its office photo/address and shows
truthful city-level coordinates. Surrounding reference grid, page branding
and footer are excluded. Touch/keyboard/reduced-motion/failure behavior are
our adaptations because neither recording demonstrates them.

Residual visual differences: point sprites do not have exact tangent-patch
foreshortening, coastlines use simplified 1:110m geography, and sample density,
camera, brightness/falloff and motion tuning remain approximations of an
unknown original implementation. Human evaluation should concentrate on the
actual browser and short evidence recording, not test counts alone.

Final local gate (12 October 2026): 22 experiment unit checks passed; the built
browser matrix passed 35 cases across Chromium, Firefox, Chromium touch and
WebKit, with five deliberate exclusions for input-specific cases. Typecheck,
offline geography verification, experiment build, root frontend typecheck,
216 existing frontend unit checks and the production frontend build passed.
Resource instrumentation confirmed one buffer/VAO/program, no allocations,
uploads or metadata queries during steady rendering, and zero owned handles
after disposal. Strict CSP, fallback, responsive, reduced-motion, context
restoration and touch scrolling checks passed. Visibility/navigation event
coverage includes controlled browser-event simulation; a full native bfcache
restoration and screen-reader device session were not claimed.

Detailed results and environment limits are recorded in
`.local/evidence/location-earth-globe/QUALIFICATION.md`. VMware software
rendering is not physical integrated-GPU evidence;
no Safari/iPhone or physical GPU claim is made.
