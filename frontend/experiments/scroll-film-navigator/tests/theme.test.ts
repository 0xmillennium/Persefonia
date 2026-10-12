import { it,expect } from "vitest";
import { themeFor } from "../src/theme";
import { FilmTransport } from "../src/transport";
import { Hold } from "../src/hold";
it("Theme follows committed identity rather than a pending or canceled hold",()=>{
  const transport=new FilmTransport(),hold=new Hold();hold.press({x:200,y:200},"mouse");hold.step(1,false,0,false);
  expect(themeFor(transport.film)).toBe("dark");hold.release();expect(themeFor(transport.film)).toBe("dark");
  transport.switchFilm();expect(themeFor(transport.film)).toBe("light");transport.restore("robotic-arm");expect(themeFor(transport.film)).toBe("dark");
});
