import { it,expect } from "vitest";
import { createServer } from "vite";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

it("Development-server close cannot publish a receipt for an unbuilt artifact",async()=>{
  const receipt=fileURLToPath(new URL("../../../dist/scroll-film-navigator/build-receipt.json",import.meta.url));
  const before=await readFile(receipt,"utf8").catch(()=>null);
  const server=await createServer({configFile:fileURLToPath(new URL("../vite.config.ts",import.meta.url)),server:{watch:null}});
  try{expect(server.config.plugins.some(plugin=>plugin.name==="experiment-build-receipt")).toBe(false);}
  finally{await server.close();}
  expect(await readFile(receipt,"utf8").catch(()=>null)).toBe(before);
});
