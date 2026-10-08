import type { PhysicsCore } from "./core";
import { PHYSICS } from "./core";

const NS = "http://www.w3.org/2000/svg";
export class Renderer {
  private nodes = new Map<string, SVGPolygonElement>();
  private debug: SVGGElement;
  private navigator: SVGCircleElement;
  private anchorNodes: { line: SVGLineElement; current: SVGCircleElement; target: SVGCircleElement }[] = [];
  showAnchors = false;
  constructor(private readonly svg: SVGSVGElement) {
    svg.querySelectorAll<SVGPolygonElement>("[data-fragment]").forEach(p => this.nodes.set(p.dataset.fragment!, p));
    this.debug = document.createElementNS(NS, "g"); this.debug.classList.add("anchors"); svg.append(this.debug);
    this.navigator = document.createElementNS(NS, "circle"); this.navigator.classList.add("navigator");
    this.navigator.setAttribute("r", String(PHYSICS.navigatorRadius)); svg.append(this.navigator);
  }
  render(core: PhysicsCore): void {
    core.bodies.forEach(b => {
      const p = this.nodes.get(b.geometry.id)!;
      p.setAttribute("transform", `translate(${b.position.x} ${b.position.y}) rotate(${b.angle * 180 / Math.PI})`);
    });
    this.navigator.setAttribute("cx", String(core.navigator.position.x));
    this.navigator.setAttribute("cy", String(core.navigator.position.y));
    this.navigator.setAttribute("visibility", core.navigator.active ? "visible" : "hidden");
    this.debug.setAttribute("visibility", this.showAnchors ? "visible" : "hidden");
    if (this.showAnchors) {
      let index = 0;
      core.bodies.forEach(b => core.anchors(b).forEach(a => {
        let nodes = this.anchorNodes[index++];
        if (!nodes) {
          nodes = { line: document.createElementNS(NS, "line"), current: document.createElementNS(NS, "circle"),
            target: document.createElementNS(NS, "circle") };
          nodes.current.setAttribute("r", "2"); nodes.target.setAttribute("r", "3");
          this.debug.append(nodes.line, nodes.current, nodes.target); this.anchorNodes.push(nodes);
        }
        nodes.line.setAttribute("x1", String(a.current.x)); nodes.line.setAttribute("y1", String(a.current.y));
        nodes.line.setAttribute("x2", String(a.target.x)); nodes.line.setAttribute("y2", String(a.target.y));
        for (const [node, point] of [[nodes.current, a.current], [nodes.target, a.target]] as const) {
          node.setAttribute("cx", String(point.x)); node.setAttribute("cy", String(point.y));
        }
      }));
    }
  }
  dispose(): void { this.debug.remove(); this.navigator.remove(); }
}
