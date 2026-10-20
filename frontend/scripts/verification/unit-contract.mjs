import { sameInventory } from './browser-contract.mjs';

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

export function validateUnitExecution(discovered, report, scalar, live) {
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
  // This is the preserved minimum signal, not a maximum count for future tests or seeds.
  if (
    !Array.from({ length: 100 }, (_, i) => i + 1).every((seed) =>
      scalar.some((r) => r.seed === seed),
    ) ||
    scalar.some((r) => r.passed !== true) ||
    live.some((r) => r.rest !== true)
  )
    throw new Error('Incomplete scalar/live stress signal');
  return cases;
}
