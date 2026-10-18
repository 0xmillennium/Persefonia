import { expect, it, vi } from 'vitest';
import { DisposalScope } from './disposal';

it('unwinds acquired resources once, continues after failure, and releases late acquisitions', () => {
  const scope = new DisposalScope(),
    calls: number[] = [];
  const report = vi.spyOn(console, 'error').mockImplementation(() => {});
  try {
    scope.add(() => calls.push(1));
    scope.add(() => {
      calls.push(2);
      throw new Error('release failed');
    });
    scope.add(() => calls.push(3));
    scope.dispose();
    scope.dispose();
    scope.add(() => calls.push(4));
    expect(calls).toEqual([3, 2, 1, 4]);
    expect(report).toHaveBeenCalledOnce();
  } finally {
    report.mockRestore();
  }
});
