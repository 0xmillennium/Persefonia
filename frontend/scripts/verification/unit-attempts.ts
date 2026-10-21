import type { Reporter, TestCase, TestSuite, TestModule } from 'vitest/node';
import { writeJson } from './browser-runtime.mjs';

/** Supplements native JSON with attempt diagnostics that its format does not retain. */
export default class UnitAttempts implements Reporter {
  private moduleStarts = new Map<string, number>();
  private phases: { file: string; durationMs: number | null; state: string }[] = [];

  onTestModuleStart(module: TestModule) {
    this.moduleStarts.set(module.moduleId, performance.now());
  }

  onTestModuleEnd(module: TestModule) {
    const start = this.moduleStarts.get(module.moduleId);
    this.phases.push({
      file: module.moduleId,
      durationMs: start === undefined ? null : performance.now() - start,
      state: module.state(),
    });
  }

  private cases: {
    id: string;
    nativeId: string;
    state: string;
    retryCount: number | undefined;
    repeatCount: number | undefined;
    duration: number | undefined;
    startTime: number | undefined;
  }[] = [];

  onTestCaseResult(test: TestCase) {
    const names = [test.name];
    let parent: TestSuite | TestCase['module'] = test.parent;
    while (parent.type !== 'module') {
      names.unshift(parent.name);
      parent = parent.parent;
    }
    const diagnostic = test.diagnostic();
    this.cases.push({
      id: JSON.stringify([test.module.moduleId, ...names]),
      nativeId: test.id,
      state: test.result().state,
      retryCount: diagnostic?.retryCount,
      repeatCount: diagnostic?.repeatCount,
      duration: diagnostic?.duration,
      startTime: diagnostic?.startTime,
    });
  }

  onTestRunEnd() {
    const destination = process.env.PERSEFONIA_UNIT_ATTEMPTS;
    if (!destination) throw new Error('Missing mandatory unit attempt destination');
    writeJson(destination, this.cases);
    writeJson(process.env.PERSEFONIA_UNIT_PHASES ?? `${destination}.phases.json`, this.phases);
  }
}
