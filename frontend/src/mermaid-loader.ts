import mermaid from "mermaid";

mermaid.initialize({
  startOnLoad: false,
  securityLevel: "strict"
});

async function renderMermaid(): Promise<void> {
  try {
    await mermaid.run({ querySelector: ".language-mermaid, .mermaid" });
  } catch (error) {
    console.warn("Mermaid rendering failed", error);
  }
}

void renderMermaid();
