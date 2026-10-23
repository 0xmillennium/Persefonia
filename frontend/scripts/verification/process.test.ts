import { expect, it } from 'vitest';
import { runProcess } from './process.mjs';
import { readFileSync } from 'node:fs';
it('retains the original nonzero exit and output without retry', async () => {
  const result = await runProcess(
    process.execPath,
    ['-e', "console.log('first failure');process.exit(7)"],
    { stdio: ['ignore', 'pipe', 'pipe'] },
    2000,
  );
  expect(result.exitCode).toBe(7);
  expect(result.stdout).toContain('first failure');
  expect(result.timedOut).toBe(false);
});
it('terminates an owned process group including an uncooperative descendant on deadline', async () => {
  const script =
    "const {spawn}=require('node:child_process'); const child=spawn(process.execPath,['-e',\"process.on('SIGTERM',()=>{});setInterval(()=>{},1000)\"],{stdio:'ignore'}); console.log(child.pid); process.on('SIGTERM',()=>{}); setInterval(()=>{},1000);";
  const result = await runProcess(
    process.execPath,
    ['-e', script],
    { stdio: ['ignore', 'pipe', 'pipe'] },
    150,
  );
  expect(result.exitCode).toBe(124);
  expect(result.timedOut).toBe(true);
  const pid = Number(result.stdout.trim());
  expect(pid).toBeGreaterThan(0);
  // A killed child may remain a zombie until the host reaps it; it is no longer executing.
  let running = false;
  try {
    running = !readFileSync(`/proc/${pid}/stat`, 'utf8').includes(') Z ');
  } catch {}
  expect(running).toBe(false);
}, 5000);
