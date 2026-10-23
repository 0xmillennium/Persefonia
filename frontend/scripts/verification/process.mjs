import { spawn } from 'node:child_process';
/** Bound an owned process group, including descendants, and retain the first exit. */
export async function runProcess(command, args, options, deadlineMs) {
  const started = performance.now();
  const child = spawn(command, args, { ...options, detached: true });
  let timedOut = false,
    interrupted = null,
    stdout = '',
    stderr = '';
  child.stdout?.on('data', (data) => {
    stdout += data;
  });
  child.stderr?.on('data', (data) => {
    stderr += data;
  });
  const signalGroup = (signal) => {
    try {
      process.kill(-child.pid, signal);
    } catch (error) {
      if (error.code !== 'ESRCH') throw error;
    }
  };
  let escalation;
  const stop = () => {
    signalGroup('SIGTERM');
    escalation ??= setTimeout(() => signalGroup('SIGKILL'), 2000);
  };
  const onTerm = () => {
    interrupted = 'SIGTERM';
    stop();
  };
  const onInt = () => {
    interrupted = 'SIGINT';
    stop();
  };
  process.once('SIGTERM', onTerm);
  process.once('SIGINT', onInt);
  const deadline = setTimeout(() => {
    timedOut = true;
    stop();
  }, deadlineMs);
  try {
    const result = await new Promise((resolve, reject) => {
      child.once('error', reject);
      child.once('close', (code, signal) => resolve({ code, signal }));
    });
    return {
      exitCode: timedOut ? 124 : interrupted ? 143 : (result.code ?? 1),
      signal: interrupted ?? result.signal,
      timedOut,
      durationMs: performance.now() - started,
      stdout,
      stderr,
    };
  } finally {
    clearTimeout(deadline);
    clearTimeout(escalation);
    if (child.pid) signalGroup('SIGKILL');
    process.removeListener('SIGTERM', onTerm);
    process.removeListener('SIGINT', onInt);
  }
}
