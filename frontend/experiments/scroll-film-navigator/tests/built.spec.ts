import { test,expect } from "@playwright/test";
test.beforeEach(({page})=>{page.on("pageerror",error=>{throw error;});});
test("Built media, semantic controls, hook stripping and reload/history restoration",async({page})=>{
  await page.goto("/");await expect(page.locator("#message")).toBeHidden();expect(await page.evaluate(()=>"__filmExperiment"in window)).toBe(false);
  await expect(page.getByRole("button",{name:"Diagnostics"})).toBeHidden();
  await page.locator("#progress").focus();await page.keyboard.press("End");await expect(page.locator("#frame")).toHaveText("192 / 192");
  await page.getByRole("button",{name:"Tree",exact:true}).click();await expect(page.getByRole("button",{name:"Tree",exact:true})).toHaveAttribute("aria-pressed","true");
  await Promise.all([page.waitForNavigation(),page.evaluate(()=>location.reload())]);await expect(page.getByRole("button",{name:"Tree",exact:true})).toHaveAttribute("aria-pressed","true");await expect(page.locator("#frame")).toHaveText("192 / 192");
  // Leave immediately after a scroll: native restoration must win over a possibly
  // throttled history snapshot, and outgoing hidden work must not overwrite Tree.
  await page.evaluate(()=>scrollTo(0,.65*(document.documentElement.scrollHeight-innerHeight)));
  await expect(page.locator("#frame")).toHaveText("125 / 192");
  const back=await page.evaluate(()=>({y:scrollY,film:history.state.scrollFilmNavigator.film}));
  await page.goto("/index.html?history=away");await page.goBack();await expect(page.getByRole("button",{name:"Tree",exact:true})).toHaveAttribute("aria-pressed","true");
  await expect.poll(async()=>page.evaluate(()=>scrollY)).toBe(back.y);await expect(page.locator("#countdown")).toBeHidden();
});
test("Built reduced motion, keyboard navigation and self-hosted asset boundary",async({page})=>{
  const requests:string[]=[];page.on("request",r=>requests.push(r.url()));await page.emulateMedia({reducedMotion:"reduce"});await page.goto("/");await expect(page.locator("#message")).toBeHidden();
  await page.getByRole("button",{name:"Tree",exact:true}).focus();await page.keyboard.press("Enter");await expect(page.getByRole("button",{name:"Tree",exact:true})).toHaveAttribute("aria-pressed","true");
  expect(requests.every(url=>url.startsWith("http://127.0.0.1:4181/"))).toBe(true);
  const css=await page.locator("#tree").evaluate(el=>getComputedStyle(el).outlineStyle);expect(css).not.toBe("none");
});
