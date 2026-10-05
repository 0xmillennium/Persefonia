import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { chromium, expect, test, type Page } from "@playwright/test";

const film = (page: Page) => page.locator("[data-home-film]");
const video = (page: Page) => page.locator<HTMLVideoElement>("[data-home-video]");

test.beforeEach(() => {
  const container = process.env.PERSEFONIA_BROWSER_DB_CONTAINER;
  if (!container) throw new Error("browserCheck must provide an isolated fixture database");
  execFileSync("docker", ["exec", "-i", container, "psql", "--set", "ON_ERROR_STOP=1",
    "-U", "persefonia_browser", "-d", "persefonia_browser"],
    { input: readFileSync(new URL("./fixture.sql", import.meta.url)), stdio: ["pipe", "pipe", "pipe"] });
});

async function visit(page: Page) {
  expect((await page.goto("/", { waitUntil: "networkidle" }))?.status()).toBe(200);
  await page.evaluate(() => document.fonts.ready);
}

async function visibleScroll(page: Page, progress: number) {
  await page.locator("#identity").evaluate((scene, fraction) => {
    const rect = scene.getBoundingClientRect();
    const stage = scene.querySelector(".home-scene__stage")!.getBoundingClientRect();
    window.scrollTo(0, rect.top + scrollY + (rect.height - stage.height) * fraction);
  }, progress);
}

async function assertDecoded(page: Page) {
  await expect(film(page)).toHaveAttribute("data-film-state", "ready");
  await expect.poll(() => video(page).evaluate(node => node.readyState)).toBeGreaterThanOrEqual(2);
  const state = await video(page).evaluate(node => ({ width: node.videoWidth, height: node.videoHeight, paused: node.paused, muted: node.muted, time: node.currentTime }));
  expect(state.width).toBeGreaterThanOrEqual(720); expect(state.height).toBeGreaterThan(0);
  expect(state.paused).toBe(true); expect(state.muted).toBe(true);
  const geometry = await page.locator("#identity").evaluate(node => {
    const rect = node.getBoundingClientRect();
    const stage = node.querySelector(".home-scene__stage")!.getBoundingClientRect();
    return { top: rect.top, height: rect.height, stageHeight: stage.height, stageTop: stage.top, stageBottom: stage.bottom, viewport: innerHeight };
  });
  // The actual pinned stage must remain available throughout the seek timeline.
  expect(geometry.stageTop).toBeGreaterThanOrEqual(-1);
  expect(geometry.stageBottom).toBeLessThanOrEqual(geometry.viewport + 1);
  const duration = await video(page).evaluate(node => node.duration);
  const expected = Math.max(0, Math.min(1, -geometry.top / (geometry.height - geometry.stageHeight))) * (duration - 1 / 24);
  await expect.poll(() => video(page).evaluate(node => node.currentTime)).toBeCloseTo(expected, 1);
  // Inspect real decoded pixels, independently of the implementation's state attribute.
  const pixels = await video(page).evaluate(node => {
    const canvas = document.createElement("canvas"); canvas.width = 64; canvas.height = 36;
    const context = canvas.getContext("2d")!; context.drawImage(node, 0, 0, 64, 36);
    const data = context.getImageData(0, 0, 64, 36).data;
    return { sum: [...data].reduce((sum, value) => sum + value, 0), colors: new Set([...data]).size };
  });
  expect(pixels.colors).toBeGreaterThan(30);
  return pixels.sum;
}

test("film decodes, follows only its geometry, reverses and settles without playback", async ({ page }, testInfo) => {
  const errors: string[] = []; page.on("pageerror", error => errors.push(error.message));
  await visit(page);
  expect(await video(page).getAttribute("src")).toBeNull();
  await expect(page.locator(".home-motion")).toBeHidden();
  await visibleScroll(page, .35);
  const opening = await assertDecoded(page);
  const src = await video(page).getAttribute("src");
  expect(src).toContain(testInfo.project.name === "desktop-1440" ? "coast-1280-" : "coast-720-");
  await visibleScroll(page, 1);
  const later = await assertDecoded(page);
  expect(later).not.toBe(opening);
  await page.screenshot({ path: testInfo.outputPath("film-later.png") });
  await visibleScroll(page, .35);
  expect(await assertDecoded(page)).toBe(opening);
  // CMS expansion after the film must never change its mapping.
  await page.locator(".home-writing__list").evaluate(node => node.append(...Array.from({ length: 12 }, () => node.firstElementChild!.cloneNode(true))));
  await visibleScroll(page, .45);
  await assertDecoded(page);
  const settled = await video(page).evaluate(node => ({ time: node.currentTime, frames: node.getVideoPlaybackQuality().totalVideoFrames }));
  await page.waitForTimeout(400);
  expect(await video(page).evaluate(node => ({ time: node.currentTime, frames: node.getVideoPlaybackQuality().totalVideoFrames }))).toEqual(settled);
  await page.locator("#contact").scrollIntoViewIfNeeded();
  const offscreen = await video(page).evaluate(node => ({ time: node.currentTime, frames: node.getVideoPlaybackQuality().totalVideoFrames }));
  await page.waitForTimeout(400);
  expect(await video(page).evaluate(node => ({ time: node.currentTime, frames: node.getVideoPlaybackQuality().totalVideoFrames }))).toEqual(offscreen);
  expect(errors).toEqual([]);
});

test("pause, live reduced motion and resizing retain a coherent film", async ({ page }, testInfo) => {
  await visit(page); await visibleScroll(page, .45); await assertDecoded(page);
  const src = await video(page).getAttribute("src");
  await expect.poll(() => video(page).evaluate(node => node.seeking)).toBe(false);
  const pausedTime = await video(page).evaluate(node => node.currentTime);
  await page.locator(".home-motion").click();
  await expect(film(page)).toHaveAttribute("data-film-state", "paused");
  await expect(video(page)).toBeVisible();
  await expect(page.locator(".home-motion")).toHaveText("Resume motion");
  await visibleScroll(page, .55);
  await page.waitForTimeout(150);
  expect(await video(page).evaluate(node => node.currentTime)).toBe(pausedTime);
  await page.locator(".home-motion").click();
  await assertDecoded(page);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await expect(film(page)).toHaveAttribute("data-film-state", "static");
  await expect(video(page)).toBeHidden(); await expect(page.locator(".home-motion")).toBeHidden();
  expect(await page.locator(".home-cinema__image").evaluate(node => getComputedStyle(node).backgroundImage)).toContain("coast-settled-");
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await assertDecoded(page);
  for (const viewport of [{width:600,height:800},{width:1051,height:700},{width:390,height:850},{width:932,height:430}]) {
    await page.setViewportSize(viewport);
    await visibleScroll(page, .4);
    if (viewport.height < 600) {
      await expect(film(page)).toHaveAttribute("data-film-state", "static");
      await expect(video(page)).toBeHidden();
      await expect(page.locator("#identity")).not.toHaveAttribute("data-scene-enhanced", "");
    } else await assertDecoded(page);
    expect(await video(page).getAttribute("src")).toBe(src);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(viewport.width);
  }
  await page.screenshot({path:testInfo.outputPath("film-resized-landscape.png")});
});

test("initial reduced motion, data saving and unavailable film preserve all public content", async ({ browser }, testInfo) => {
  for (const mode of ["reduced", "save-data", "media-error"]) {
    const context = await browser.newContext({viewport:testInfo.project.use.viewport,reducedMotion:mode === "reduced" ? "reduce" : "no-preference"});
    const page=await context.newPage();const mediaRequests:string[]=[];
    page.on("request",request=>{if(request.url().includes(".mp4")) mediaRequests.push(request.url());});
    if(mode === "save-data") await page.addInitScript(()=>Object.defineProperty(navigator,"connection",{value:{saveData:true}}));
    if(mode === "media-error") await page.route("**/*.mp4",route=>route.abort());
    await visit(page);await visibleScroll(page,.4);
    await expect(film(page)).toHaveAttribute("data-film-state",mode === "media-error" ? "failed" : "static");
    await expect(video(page)).toBeHidden();await expect(page.locator(".home-motion")).toBeHidden();
    if(mode !== "media-error") expect(mediaRequests).toEqual([]);
    await expect(page.locator(".home-project h3 a")).toHaveCount(3);
    await page.locator(".home-layer summary").last().click();
    await expect(page.locator(".home-layer").last()).toContainText("PostgreSQL");
    await page.locator(".home-contact__link").click();await expect(page).toHaveURL(/\/contact$/);
    await context.close();
  }
});

test("native touch scrolling and orientation preserve film and navigation", async ({ browser },testInfo)=>{
  test.skip(testInfo.project.name !== "mobile-390");
  const context=await browser.newContext({viewport:{width:390,height:850},isMobile:true,hasTouch:true});
  const page=await context.newPage();await visit(page);
  const session=await context.newCDPSession(page);
  await session.send("Input.dispatchTouchEvent",{type:"touchStart",touchPoints:[{x:250,y:650}]});
  for(const y of [600,550,500,450,400]) await session.send("Input.dispatchTouchEvent",{type:"touchMove",touchPoints:[{x:250,y}]});
  await session.send("Input.dispatchTouchEvent",{type:"touchEnd",touchPoints:[]});
  await expect.poll(()=>page.evaluate(()=>scrollY)).toBeGreaterThan(100);
  await assertDecoded(page);
  await page.setViewportSize({width:850,height:390});
  await expect(film(page)).toHaveAttribute("data-film-state", "static");
  await expect(video(page)).toBeHidden();
  await expect(page.locator(".home-identity__statement")).toBeVisible();
  await page.setViewportSize({width:390,height:850});await page.evaluate(()=>scrollTo(0,0));
  await page.locator(".home-nav__mobile summary").tap();await page.locator(".home-nav__mobile a[href='#work']").tap();
  await expect(page).toHaveURL(/#work$/);await expect(page.locator("#work")).toBeFocused();
  await context.close();
});

test("real BFCache restore retains the media node and current motion preference",async({},testInfo)=>{
  test.skip(testInfo.project.name !== "desktop-1440");
  const browser=await chromium.launch({channel:"chromium",ignoreDefaultArgs:["--disable-back-forward-cache"]});
  const context=await browser.newContext({baseURL:process.env.PERSEFONIA_BROWSER_URL,viewport:{width:1440,height:900}});
  const page=await context.newPage();
  await page.addInitScript(()=>{
    (window as Window & {persistedShows?:number}).persistedShows=0;
    window.addEventListener("pageshow",event=>{if(event.persisted) (window as Window & {persistedShows:number}).persistedShows++;});
  });
  try{
    await visit(page);await visibleScroll(page,.4);await assertDecoded(page);
    await page.evaluate(()=>{(window as Window & {retainedVideo?:HTMLVideoElement}).retainedVideo=document.querySelector("video")!;});
    await page.goto("/contact",{waitUntil:"networkidle"});await page.emulateMedia({reducedMotion:"reduce"});
    await page.goBack({waitUntil:"commit"});
    expect(await page.evaluate(()=>(window as Window & {persistedShows?:number}).persistedShows)).toBe(1);
    expect(await page.evaluate(()=>(window as Window & {retainedVideo?:HTMLVideoElement}).retainedVideo===document.querySelector("video"))).toBe(true);
    await expect(film(page)).toHaveAttribute("data-film-state","static");await expect(video(page)).toBeHidden();
    await page.emulateMedia({reducedMotion:"no-preference"});await assertDecoded(page);
    await visibleScroll(page,.6);await assertDecoded(page);
  }finally{await context.close();await browser.close();}
});

test("loading and native scroll report actual long tasks and transfer cost",async({page},testInfo)=>{
  await page.addInitScript(()=>{
    const state=window as Window & {homeLongTasks?:number[];homeLcp?:number;homeShifts?:number};
    state.homeLongTasks=[];state.homeShifts=0;
    new PerformanceObserver(list=>state.homeLongTasks!.push(...list.getEntries().map(entry=>entry.duration))).observe({type:"longtask",buffered:true});
    new PerformanceObserver(list=>{state.homeLcp=list.getEntries().at(-1)?.startTime;}).observe({type:"largest-contentful-paint",buffered:true});
    new PerformanceObserver(list=>{for(const entry of list.getEntries() as (PerformanceEntry & {hadRecentInput:boolean;value:number})[]) if(!entry.hadRecentInput) state.homeShifts!+=entry.value;}).observe({type:"layout-shift",buffered:true});
  });
  await visit(page);
  const loading=await page.evaluate(()=>{
    const state=window as Window & {homeLongTasks?:number[];homeLcp?:number;homeShifts?:number};
    const navigation=performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming;
    const resources=(performance.getEntriesByType("resource") as PerformanceResourceTiming[]).map(entry=>({name:entry.name.split("/").at(-1),bytes:entry.encodedBodySize,duration:entry.duration}));
    return {domReady:navigation.domContentLoadedEventEnd,load:navigation.loadEventEnd,lcp:state.homeLcp,cls:state.homeShifts,longTasks:state.homeLongTasks,resources};
  });
  await page.evaluate(()=>{(window as Window & {homeLongTasks:number[]}).homeLongTasks=[];});
  for(const fraction of [.25,.35,.45,.55,.65,.55,.45,.35]){await visibleScroll(page,fraction);await page.waitForTimeout(100);}
  await assertDecoded(page);
  await page.locator("#contact").scrollIntoViewIfNeeded();await page.waitForTimeout(250);
  const runtime=await page.evaluate(()=>(window as Window & {homeLongTasks:number[]}).homeLongTasks);
  console.log(`${testInfo.project.name} local loading ${JSON.stringify(loading)}; isolated scroll long tasks ${JSON.stringify(runtime)}`);
  await testInfo.attach("performance-evidence",{body:JSON.stringify({loading,runtime},null,2),contentType:"application/json"});
  expect(loading.cls).toBeLessThan(.1);
});

test("the seasonal aperture keeps its subject available from entry to release", async ({ page }, testInfo) => {
  await visit(page);
  for (const progress of [.15, .35, .55, .75, 1]) {
    await visibleScroll(page, progress);
    await assertDecoded(page);
    const opening = await page.locator(".home-cinema__image").evaluate(node => {
      const stage = node.getBoundingClientRect();
      const clip = getComputedStyle(node).clipPath.match(/inset\(([\d.]+)% 0px ([\d.]+)%\)/);
      if (!clip) throw new Error(`Unexpected aperture: ${getComputedStyle(node).clipPath}`);
      return { visible: stage.height * (1 - (Number(clip[1]) + Number(clip[2])) / 100), viewport: innerHeight };
    });
    expect(opening.visible).toBeGreaterThan(opening.viewport * .33);
    await expect(page.locator(".home-actions")).toBeInViewport();
    await expect(page.locator(".home-identity__intro")).toBeInViewport();
    await page.screenshot({ path: testInfo.outputPath(`aperture-${progress}.png`) });
  }
  await page.getByRole("link", { name: /Explore work/ }).click();
  await expect(page).toHaveURL(/#work$/);
  await expect(page.locator("#work")).toBeInViewport();
});

test("a stalled first decode returns to the static edition without a dead scroll interval", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop-1440");
  let release: (() => void) | undefined;
  await page.route("**/*.mp4", async route => {
    await new Promise<void>(resolve => { release = resolve; });
    await route.abort();
  });
  try {
    await visit(page);
    await visibleScroll(page, .25);
    await expect(page.locator(".home-motion")).toBeHidden();
    await expect(film(page)).toHaveAttribute("data-film-state", "failed", { timeout: 14_000 });
    await expect(video(page)).toBeHidden();
    await expect(page.locator("#identity")).not.toHaveAttribute("data-scene-enhanced", "");
    await page.getByRole("link", { name: /Explore work/ }).click();
    await expect(page.locator("#work")).toBeInViewport();
    await page.screenshot({ path: testInfo.outputPath("stalled-media-fallback.png") });
  } finally { release?.(); }
});

test("native scroll measures frame intervals, layout pressure and JS heap", async ({ page }, testInfo) => {
  await visit(page);
  await visibleScroll(page, .1); await assertDecoded(page);
  const session = await page.context().newCDPSession(page);
  await session.send("Performance.enable");
  const before = await session.send("Performance.getMetrics");
  const intervals = await page.evaluate(async () => {
    const scene = document.querySelector("#identity")!;
    const stage = document.querySelector(".home-scene__stage")!;
    const top = scene.getBoundingClientRect().top + scrollY;
    const travel = scene.getBoundingClientRect().height - stage.getBoundingClientRect().height;
    const intervals: number[] = [];
    let previous = performance.now();
    for (let frame = 0; frame < 120; frame++) {
      await new Promise<void>(resolve => requestAnimationFrame(time => {
        intervals.push(time - previous); previous = time;
        const progress = frame < 60 ? frame / 60 : 1 - (frame - 60) / 60;
        scrollTo(0, top + travel * progress); resolve();
      }));
    }
    return intervals;
  });
  await assertDecoded(page);
  const after = await session.send("Performance.getMetrics");
  const metrics = (values: { metrics: { name: string; value: number }[] }) => Object.fromEntries(values.metrics.map(item => [item.name, item.value]));
  const start = metrics(before), end = metrics(after);
  const sorted = [...intervals.slice(1)].sort((a, b) => a - b);
  const evidence = {
    medianFrameMs: sorted[Math.floor(sorted.length / 2)], p95FrameMs: sorted[Math.floor(sorted.length * .95)],
    framesOver50Ms: sorted.filter(value => value > 50).length,
    taskDurationSeconds: end.TaskDuration! - start.TaskDuration!,
    layoutCount: end.LayoutCount! - start.LayoutCount!, recalcStyleCount: end.RecalcStyleCount! - start.RecalcStyleCount!,
    jsHeapBefore: start.JSHeapUsedSize, jsHeapAfter: end.JSHeapUsedSize,
    limitation: "Local headless Chromium; JS heap excludes native decoder, image and GPU allocations."
  };
  console.log(`${testInfo.project.name} runtime ${JSON.stringify(evidence)}`);
  await testInfo.attach("runtime-evidence", { body: JSON.stringify(evidence, null, 2), contentType: "application/json" });
  await session.detach();
  expect(evidence.layoutCount).toBeLessThan(10);
});
