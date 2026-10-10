import { mountIdentity } from "./runtime";

const manual = import.meta.env.DEV && new URLSearchParams(location.search).get("test") === "1";
const installDiagnostics = manual ? (await import("./test-support")).installTestApi : undefined;
mountIdentity(document.querySelector<HTMLElement>("main")!, { manual, installDiagnostics });
