import type { Film } from "./transport";
export const themeFor=(film:Film):"dark"|"light"=>film==="tree"?"light":"dark";
