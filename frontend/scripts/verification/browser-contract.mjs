import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';

const skipPolicy = JSON.parse(
  readFileSync(new URL('./expected-skips.json', import.meta.url), 'utf8'),
);
export const groups = [
  { name: 'desktop', suite: 'chromium', project: 'desktop-1440', shards: 2 },
  { name: 'tablet', suite: 'chromium', project: 'tablet-834', shards: 2 },
  { name: 'mobile', suite: 'chromium', project: 'mobile-390', shards: 2 },
  { name: 'firefox', suite: 'cross', project: 'firefox-desktop', shards: 1 },
  { name: 'webkit', suite: 'cross', project: 'webkit-desktop', shards: 1 },
];
export const partitions = groups.flatMap(({ shards, ...group }) =>
  Array.from({ length: shards }, (_, index) => ({
    ...group,
    shard: `${index + 1}/${shards}`,
  })),
);
export const partitionKey = (partition) =>
  `${partition.name}-${partition.shard.replace('/', 'of')}`;
export const digest = (value) => createHash('sha256').update(JSON.stringify(value)).digest('hex');
const requireThat = (condition, message) => {
  if (!condition) throw new Error(message);
};

export function sameInventory(expected, actual) {
  requireThat(expected.length > 0 && actual.length > 0, 'Empty test selection');
  const wanted = new Set(expected),
    observed = new Set(actual);
  requireThat(
    wanted.size === expected.length && observed.size === actual.length,
    'Duplicate test selection',
  );
  requireThat(
    wanted.size === observed.size && [...wanted].every((id) => observed.has(id)),
    'Incomplete or unexpected test selection',
  );
}

/** Normalize the installed Playwright JSON format; discovery and execution remain separate. */
export function reportTests(report) {
  requireThat(report?.config?.forbidOnly === true, 'Focused-test protection is missing');
  requireThat(
    report.config.fullyParallel === false && report.config.workers === 1,
    'Unsafe browser scheduling',
  );
  requireThat(
    Array.isArray(report.config.projects) &&
      report.config.projects.every((p) => p.retries === 0 && p.repeatEach === 1),
    'Retry/repeat policy changed',
  );
  requireThat(
    Array.isArray(report.errors) && report.errors.length === 0,
    'Runner discovery/execution errors',
  );
  const rows = [];
  function visit(suites) {
    requireThat(Array.isArray(suites), 'Corrupt suites');
    for (const suite of suites) {
      for (const spec of suite.specs ?? []) {
        requireThat(
          typeof spec.id === 'string' &&
            spec.id &&
            typeof spec.file === 'string' &&
            typeof spec.title === 'string',
          'Corrupt test identity',
        );
        requireThat(Array.isArray(spec.tests), 'Corrupt project tests');
        for (const test of spec.tests) {
          requireThat(
            typeof test.projectName === 'string' &&
              Array.isArray(test.results) &&
              Array.isArray(test.annotations),
            'Corrupt test results',
          );
          rows.push({
            id: `${test.projectName}:${spec.id}`,
            project: test.projectName,
            file: spec.file,
            title: spec.title,
            expectedStatus: test.expectedStatus,
            annotations: test.annotations,
            results: test.results,
            status: test.status,
          });
        }
      }
      visit(suite.suites ?? []);
    }
  }
  visit(report.suites);
  sameInventory(
    rows.map((r) => r.id),
    rows.map((r) => r.id),
  );
  return rows.sort((a, b) => a.id.localeCompare(b.id));
}

export function validateOutcomes(rows, policy = skipPolicy) {
  let passed = 0,
    skipped = 0;
  for (const row of rows) {
    requireThat(row.results.length === 1, `Missing execution or retries: ${row.id}`);
    const result = row.results[0];
    requireThat(
      result.retry === 0 && Number.isFinite(result.duration) && result.duration >= 0,
      `Invalid first attempt: ${row.id}`,
    );
    const rule = policy.find(
      (p) => p.file === row.file && p.title === row.title && p.projects.includes(row.project),
    );
    if (result.status === 'skipped') {
      requireThat(
        rule && row.annotations.some((a) => a.type === 'skip' && a.description === rule.reason),
        `Unexpected skip: ${row.id}`,
      );
      requireThat(row.status === 'skipped', `Inconsistent skip: ${row.id}`);
      skipped++;
    } else {
      requireThat(!rule || rule.optional, `Expected skip predicate changed: ${row.id}`);
      requireThat(
        result.status === 'passed' && row.status === 'expected' && row.expectedStatus === 'passed',
        `Failed or incomplete first attempt: ${row.id}`,
      );
      passed++;
    }
  }
  requireThat(passed > 0, 'No tests executed successfully');
  return { passed, skipped, failed: 0, retries: 0 };
}

export function validateReceipt(receipt, expectedIdentity, policy = skipPolicy) {
  requireThat(
    receipt?.schema === 1 && receipt.status === 'passed' && receipt.exitCode === 0,
    'Missing, corrupt or failed receipt',
  );
  requireThat(
    receipt.identity?.sourceSha === expectedIdentity.sourceSha &&
      receipt.identity?.artifactSha256 === expectedIdentity.artifactSha256,
    'Mismatched source/artifact identity',
  );
  requireThat(
    /^[a-f0-9]{40}$/.test(receipt.identity.sourceSha) &&
      /^[a-f0-9]{64}$/.test(receipt.identity.artifactSha256),
    'Invalid artifact identity',
  );
  requireThat(
    Array.isArray(receipt.selected) && Array.isArray(receipt.outcomes),
    'Corrupt receipt inventory',
  );
  requireThat(
    Array.isArray(receipt.errors) && receipt.errors.length === 0,
    'Receipt contains reporting errors',
  );
  sameInventory(
    receipt.selected,
    receipt.outcomes.map((r) => r.id),
  );
  requireThat(
    receipt.selectionHash === digest([...receipt.selected].sort()),
    'Corrupt selection fingerprint',
  );
  return validateOutcomes(receipt.outcomes, policy);
}

export function joinReceipts(
  inventory,
  receipts,
  identity,
  required = partitions,
  policy = skipPolicy,
) {
  sameInventory(
    required.map(partitionKey),
    receipts.map((r) => partitionKey(r.partition)),
  );
  for (const receipt of receipts) {
    validateReceipt(receipt, identity, policy);
    const expected = required.find((p) => partitionKey(p) === partitionKey(receipt.partition));
    requireThat(
      expected &&
        expected.suite === receipt.partition.suite &&
        expected.project === receipt.partition.project,
      'Unexpected logical partition',
    );
    requireThat(
      receipt.outcomes.every((r) => expected.project === '*' || r.project === expected.project),
      'Wrong viewport partition',
    );
  }
  for (const suite of ['chromium', 'cross']) {
    const matching = receipts.filter((r) => r.partition.suite === suite);
    sameInventory(
      inventory[suite].map((r) => r.id),
      matching.flatMap((r) => r.selected),
    );
    const source = new Map(inventory[suite].map((r) => [r.id, r]));
    const fileOwner = new Map();
    for (const receipt of matching)
      for (const row of receipt.outcomes) {
        const expected = source.get(row.id);
        requireThat(
          expected &&
            expected.project === row.project &&
            expected.file === row.file &&
            expected.title === row.title,
          'Execution identity differs from source discovery',
        );
        const key = `${row.project}:${row.file}`,
          owner = partitionKey(receipt.partition);
        requireThat(
          !fileOwner.has(key) || fileOwner.get(key) === owner,
          `File split across isolated shards: ${key}`,
        );
        fileOwner.set(key, owner);
      }
  }
  return {
    partitions: receipts.length,
    selected: receipts.reduce((n, r) => n + r.selected.length, 0),
  };
}
