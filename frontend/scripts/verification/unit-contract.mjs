import { sameInventory } from './browser-contract.mjs';
import { selectionPolicy } from './selection-policy.mjs';
export const physicsSeeds = (profile = 'ordinary') => {
  if (!['ordinary', 'deep'].includes(profile)) throw new Error('Unknown physics profile');
  return profile === 'deep'
    ? Array.from({ length: 100 }, (_, i) => i + 1)
    : selectionPolicy.ordinaryPhysicsSeeds;
};

export function validateUnitAttempts(cases, attempts) {
  // Native parameterized cases may have identical expanded titles. Preserve their
  // multiplicity rather than mistaking a repeated title for a duplicate execution.
  const expected = cases
    .map((test) => JSON.stringify([test.file, ...test.ancestorTitles, test.title]))
    .sort();
  const actual = attempts.map((test) => test.id).sort();
  if (
    !expected.length ||
    expected.length !== actual.length ||
    expected.some((id, i) => id !== actual[i]) ||
    attempts.some((test) => typeof test.nativeId !== 'string' || !test.nativeId) ||
    new Set(attempts.map((test) => test.nativeId)).size !== attempts.length
  )
    throw new Error('Incomplete or unexpected unit attempts');
  if (
    attempts.some(
      (test) => test.state !== 'passed' || test.retryCount !== 0 || test.repeatCount !== 0,
    )
  )
    throw new Error('Failed, retried or repeated first attempt');
}

export function validateUnitExecution(discovered, report, scalar, live, profile = 'ordinary') {
  if (
    !Array.isArray(discovered) ||
    !Array.isArray(report?.testResults) ||
    !report.success ||
    report.numFailedTests ||
    report.numPendingTests ||
    report.numTodoTests
  )
    throw new Error('Failed, skipped or corrupt mandatory unit execution');
  sameInventory(
    [...new Set(discovered.map((r) => r.file))],
    report.testResults.map((r) => r.name),
  );
  const cases = report.testResults.flatMap((file) =>
    file.assertionResults.map((test) => ({ file: file.name, ...test })),
  );
  const expected = discovered.map((test) => JSON.stringify([test.file, test.name])).sort();
  const actual = cases
    .map((test) => JSON.stringify([test.file, [...test.ancestorTitles, test.title].join(' > ')]))
    .sort();
  if (
    expected.length !== actual.length ||
    expected.some((id, index) => id !== actual[index]) ||
    discovered.some((test) => typeof test.name !== 'string' || !test.name)
  )
    throw new Error('Incomplete or unexpected expanded unit identities');
  if (
    !cases.length ||
    cases.length !== report.numTotalTests ||
    cases.some((test) => test.status !== 'passed')
  )
    throw new Error('Incomplete mandatory unit execution');
  if (
    ![scalar, live].every(
      (results) =>
        Array.isArray(results) &&
        results.every((r) => Number.isInteger(r.seed) && r.seed > 0 && r.failure === null),
    )
  )
    throw new Error('Invalid stress results');
  sameInventory(
    scalar.map((r) => r.seed),
    live.map((r) => r.seed),
  );
  sameInventory(
    physicsSeeds(profile),
    scalar.map((r) => r.seed),
  );
  if (scalar.some((r) => r.passed !== true) || live.some((r) => r.rest !== true))
    throw new Error('Incomplete scalar/live stress signal');
  return cases;
}

export function validateUnitPhases(cases, phases) {
  if (
    !Array.isArray(phases) ||
    phases.some(
      (phase) =>
        phase.state !== 'passed' || !Number.isFinite(phase.durationMs) || phase.durationMs < 0,
    )
  )
    throw new Error('Incomplete or failed monotonic unit phases');
  sameInventory(
    [...new Set(cases.map((test) => test.file))],
    phases.map((phase) => phase.file),
  );
}

/** Compare evidence as JSON data, independent of producer key order and workspace root. */
export function unitEvidenceInventory(rows) {
  const normalize = (value) =>
    typeof value === 'string'
      ? value.replace(/^.*\/frontend\//, 'frontend/')
      : Array.isArray(value)
        ? value.map(normalize)
        : value && typeof value === 'object'
          ? Object.fromEntries(
              Object.entries(value)
                .sort(([a], [b]) => a.localeCompare(b))
                .map(([key, item]) => [key, normalize(item)]),
            )
          : value;
  return rows.map((row) => JSON.stringify(normalize(row))).sort();
}
