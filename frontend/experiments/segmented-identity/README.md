# Segmented 0x identity

An isolated nine-fragment identity primitive. The production homepage does not load it. No application backend, client framework, or additional runtime dependency is required.

From `frontend/`:

```sh
npm ci
npm run identity:dev
```

Open `http://127.0.0.1:4178/`. Mouse and hovering pen input move a strict circular navigator. Touch and reduced-motion presentations retain the static semantic SVG. Reset, Pause and anchor diagnostics are native keyboard-operable controls. The renderer uses the exact simulation pose without interpolation; scheduling is tested at 60, 90, 120 and 144 Hz. Visual interpolation must never silently change collider geometry.

## Geometry and ordinary physics

`geometry.json` is the canonical polygon source. `geometry.ts` derives area centroids, uniform-density mass/inertia, 3–5 contour anchors and convex decompositions. The left form contains five fragments and the right contains four. `generate-static.mjs` produces the coupled no-JavaScript HTML/SVG; `identity:build` rejects stale generated files. Run `identity:geometry` intentionally after changing geometry, then rerun every qualification command.

`core.ts` advances deterministic 1/120-second ticks with at least four internal subdivisions. Coordinates are SVG stage units in a 960 × 620 stage; linear velocity is units/second, angle is radians, and angular velocity is radians/second. Mass is polygon area/3500. Each anchor contributes a home spring with total stiffness 12/second². Linear/angular damping use exponential decay at 3.2/3.6 per second. Contact impulses include angular lever arms, restitution 0.08 and position-only geometric projection. Ghosted fragment pairs skip both impulse and projection; navigator and wall constraints remain active.

Navigator momentum transfer saturates smoothly as `720 * tanh(observedSpeed / 720)`. Sweeps retain intervals of at most two stage units. A demonstrated free half-turn equilibrium needs a small additional home torque: it acts only within 0.01 radians of an inverted orientation, with peak acceleration 0.024 radians/second² and quadratic falloff. This deterministic restoring term is shared by live and forecast home forces. Ordinary orientations receive no additional torque.

## Deadlock detection and release

`deadlock.ts` sees read-only observations, never mutable bodies. A contact/near-contact graph nominates calm connected islands; approaching edges within four units can be considered before contact. A seven-observation window spans 50 ms. Eligibility considers relative linear kinetic energy, angular energy, instantaneous motion, distributed home error and nonzero home requests. Common translation is removed from internal-motion scoring. Actual navigator contact immediately vetoes its entire physically connected/latched group.

`forecast.ts` compares a solid baseline against singles and joint batches from the same frozen world. It simulates six exact ticks (50 ms) for each plan, with at most twelve plans and six individually ranked candidates. The full current-contact batch and full nominated batch are also considered. A baseline already recovering by 1% of the internal response scale needs no release. A release must improve on the baseline by at least 1% of that scale. Comparable useful plans prefer fewer exemptions. Remaining blocked components and an optional one-second home-only estimate help rank alternatives; that estimate does not certify escape.

Before committing, the selected batch and baseline are simulated again from current poses, velocities, navigator and permissions. Search freshness checks topology, latches, wall contact, age (400 ms), relative pose/velocity drift and nearby influencing bodies during the final live validation. Candidate ranking does not restart solely because an outside body moved; it carries no release authority. Pose limits are three units/0.08 radians; velocity limits correspond to half that error envelope over the 50 ms horizon (30 units/second and 0.8 radians/second). Remote pointer movement does not invalidate an unrelated island; nearby movement and swept paths can. Disconnected islands receive turns in key order with a rotating cursor.

A batch commits atomically only when every selected edge is a current solid contact persisting for at least 25 ms. Permission changes modify no force, pose, velocity or impulse. Each pair remains `GHOSTED_LATCHED` until actual overlap is zero and gap is at least 0.65 units for eight consecutive ticks. A 25-second failure to separate produces a diagnostic failure, never an unsafe timeout rearm. These are bounded short-term counterfactual checks, not a global convergence proof or a prediction of future user input.

## Input, scheduling and lifetime

`input.ts` retains timestamped pointer/coalesced observations. An unidentified device is accepted only for trusted primary, zero-pressure, point-sized hover; known touch and unidentified contact remain static. A trusted hover mouse fallback handles bridges that omit pointer movement after touch. Matching pointer observations and touch-generated mouse events are suppressed; touch input clears the navigator. `pointer-path.ts` preserves corners and reversals, merges redundant straight samples, and delivers at most 32 units per live tick. Long strokes are replayed in order; an 860-unit segment requires at least 27 ticks (225 ms at 120 Hz). A constrained endpoint consumes its attempted stroke rather than retrying indefinitely. Leaving, cancelling, blurring or suspending clears pending motion.

The queue holds at most 128 observations. On overflow it removes the interior point with least additional path length, carries its duration forward and counts the dropped sample. Endpoints remain; geometry can be approximated under sustained overflow. This policy deliberately trades latency/path fidelity against bounded storage and work, and telemetry exposes the tradeoff.

`scheduler.ts` permits at most two live ticks and four shadow ticks per rendered frame, with at most two shadow ticks per live tick, and checks a 10 ms work budget between ticks. This permits the widest supported forecast to finish before its age limit at 60 Hz. Delayed wall-clock time is discarded instead of inflating physics dt. An individual tick cannot be preempted, so this is a scheduling policy rather than a hard frame-time guarantee. Raw deterministic `stepFixed` calls retain unrestricted sweep replay for regression fixtures; browser input goes through the bounded adapter.

Idle, paused, hidden and reduced-motion states stop scheduling. Persisted `pagehide` suspends resources; `pageshow` resumes the cached document. Final teardown disposes listeners and renderer nodes. Anchor nodes are reused, status text changes only with state, and full snapshots are materialized only when telemetry is open. Event history retains the latest 512 transitions plus a cumulative count; headless diagnostic callers may explicitly request `eventHistoryLimit: Infinity` for complete replay logs.

## Qualification

```sh
npm run identity:check
npx playwright install --with-deps --no-shell chromium firefox webkit
npm run identity:browser
npm run identity:browser:built
```

`identity:check` typechecks implementation, configs and tests, runs all deterministic tests including 100 stress seeds and retained failing-seed fixtures, and checks/builds static assets. The browser suite runs all acceptance/hardening checks in Chromium, Firefox and WebKit, plus 100 Chromium stress seeds. CDP pen-bridge qualification is Chromium-specific. The release suite rebuilds on port 4179 and verifies CSP, exclusion of the development test API and actual persisted-history restoration. CI runs these gates separately from the production homepage checks.

If port 4178 already serves this experiment, opt into reusing it with `IDENTITY_REUSE_SERVER=1 npm run identity:browser`. Reuse is disabled by default. Headless manual inspection is available only in Vite development with `/?test=1`; the test API is removed from release builds.

Tests check finite/contained state, navigator penetration ≤0.02 units, solid fragment penetration ≤0.3 units during motion, passive kinetic-energy changes, zero permission-energy injection, safe separation/rearm and eventual tight REST. REST requires anchor error <0.002, linear speed <0.002, angular speed <0.0001, overlap <0.0001 and no ghosted pair. Initial fixtures may overlap; invariants apply after every tick. The kinetic ledger does not claim total mechanical-energy conservation: position projection can change spring potential.

Generated test results/screenshots belong under ignored `frontend/test-results/`, not beside source. Retained JSON fixtures are executable regression inputs, not historical reports. The small SAT solver has no warm-started contact manifold or general body continuous collision detection. Its qualification envelope is this fixed nine-fragment decorative interaction; arbitrary rigid-body stacks and unbounded geometries are outside that envelope.
