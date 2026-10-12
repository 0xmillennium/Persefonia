import { chromium } from "playwright";
import { mkdir, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
const output = fileURLToPath(new URL("../../../test-results/location-earth-globe/fidelity/", import.meta.url));
await mkdir(output, { recursive: true });
const software = process.env.GLOBE_SOFTWARE_GPU === "1";
const browser = await chromium.launch({ channel: "chromium", args: software ? ["--use-angle=swiftshader"] : [] });
const context = await browser.newContext({ viewport: { width: 1000, height: 1050 }, recordVideo: { dir: output, size: { width: 900, height: 945 } } });
const page = await context.newPage();
const errors = [];
page.on("pageerror", e => errors.push(e.message));
const state = () => page.evaluate(() => window.__globeExperiment.state());
const waitDraw = async before => page.waitForFunction(n => window.__globeExperiment.state().submitted > n, before);
const snapshots = {};
async function capture(name) {
  if (name === "mobile") await page.screenshot({ path: `${output}${name}.png`, fullPage: true });
  else await page.locator("#stage").screenshot({ path: `${output}${name}.png` });
  snapshots[name] = await state();
}
try {
  await page.goto(`${process.env.GLOBE_URL ?? "http://127.0.0.1:4281"}/?inspect=1`);
  await page.waitForSelector('#stage[data-ready="true"]');
  const gpu = await page.evaluate(() => {
    const gl = document.querySelector("canvas").getContext("webgl2");
    const ext = gl.getExtension("WEBGL_debug_renderer_info");
    return { vendor: ext ? gl.getParameter(ext.UNMASKED_VENDOR_WEBGL) : "unavailable",
      renderer: ext ? gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) : "unavailable" };
  });
  await page.locator("#pause").click();
  await page.waitForFunction(() => !window.__globeExperiment.state().scheduled);
  await capture("idle");
  const box = await page.locator("#stage").boundingBox(), x = box.x + box.width / 2, y = box.y + box.height / 2;
  let before = (await state()).submitted;
  await page.mouse.move(x + 100, y + 30); await waitDraw(before); await capture("hover");
  const marker = (await state()).location;
  before = (await state()).submitted;
  await page.mouse.move(box.x + marker.x, box.y + marker.y); await waitDraw(before); await capture("istanbul-panel");
  // Keep the actual pointer over the marker while intrinsic spin resumes.
  await page.locator("#pause").evaluate(el => el.click());
  await page.mouse.move(box.x + marker.x, box.y + marker.y);
  await page.waitForFunction(() => window.__globeExperiment.state().location.open);
  snapshots.proximityStart = await state();
  await page.waitForFunction(() => !window.__globeExperiment.state().location.open, null, { timeout: 7000 });
  snapshots.proximityWithdrawal = await state();
  await capture("proximity-withdrawn");
  await page.mouse.move(x, y); await page.mouse.down();
  for (let i = 1; i <= 12; i++) {
    await page.mouse.move(x + i * 11, y + i * 10);
    await page.waitForTimeout(12);
  }
  await capture("drag");
  await page.mouse.move(x + 150, y + 120); await page.mouse.up();
  snapshots.release = await state();
  await page.waitForTimeout(1200); await capture("retained-pitch");
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForFunction(() => window.__globeExperiment.state().camera.width === 366);
  await page.locator("#find-location").click();
  await capture("mobile");
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await page.setViewportSize({ width: 1000, height: 1050 });
  await page.locator("#pause").click();
  const frameIntervals = await page.evaluate(() => new Promise(resolve => {
    const intervals = []; let previous = 0;
    function sample(time) { if (previous) intervals.push(time - previous); previous = time;
      if (intervals.length < 120) requestAnimationFrame(sample); else resolve(intervals); }
    requestAnimationFrame(sample);
  }));
  const sorted = [...frameIntervals].sort((a, b) => a - b);
  const performance = { samples: sorted.length, rafMedianMs: sorted[Math.floor(sorted.length / 2)], rafP95Ms: sorted[Math.floor(sorted.length * 0.95)],
    note: "Browser RAF intervals, not GPU completion or physical display timing; isolated local VMware run." };
  await writeFile(`${output}review.json`, JSON.stringify({ software, gpu, errors, snapshots, performance }, null, 2) + "\n");
  console.log(JSON.stringify({ output, gpu, errors, performance }));
} finally {
  const video = page.video(); await context.close();
  await video.saveAs(`${output}interaction.webm`); await video.delete(); await browser.close();
}
