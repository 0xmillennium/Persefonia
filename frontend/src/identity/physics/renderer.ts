import type { Vec } from './math';

type RenderFragment = Readonly<{
  geometry: Readonly<{ id: string }>;
  position: Vec;
  angle: number;
}>;
type RenderAnchor = Readonly<{ current: Vec; target: Vec }>;

const NS = 'http://www.w3.org/2000/svg';
export class Renderer {
  private nodes = new Map<string, SVGPolygonElement>();
  private debug: SVGGElement;
  private navigator: SVGCircleElement;
  private anchorNodes: {
    line: SVGLineElement;
    current: SVGCircleElement;
    target: SVGCircleElement;
  }[] = [];
  showAnchors = false;
  constructor(svg: SVGSVGElement, navigatorRadius: number) {
    svg
      .querySelectorAll<SVGPolygonElement>('[data-fragment]')
      .forEach((p) => this.nodes.set(p.dataset.fragment!, p));
    this.debug = document.createElementNS(NS, 'g');
    this.debug.classList.add('anchors');
    svg.append(this.debug);
    this.navigator = document.createElementNS(NS, 'circle');
    this.navigator.classList.add('navigator');
    this.navigator.setAttribute('r', String(navigatorRadius));
    svg.append(this.navigator);
  }
  private attribute(node: SVGElement, name: string, value: string): void {
    if (node.getAttribute(name) !== value) node.setAttribute(name, value);
  }
  render(
    fragments: readonly RenderFragment[],
    navigator: Readonly<{ position: Vec; active: boolean }>,
    anchors: readonly RenderAnchor[] = [],
  ): void {
    fragments.forEach((b) => {
      const p = this.nodes.get(b.geometry.id)!;
      const degrees = (b.angle * 180) / Math.PI;
      const transform =
        Math.abs(degrees) > 1e-4
          ? `translate(${b.position.x} ${b.position.y}) rotate(${degrees})`
          : `translate(${b.position.x} ${b.position.y})`;
      this.attribute(p, 'transform', transform);
    });
    this.attribute(this.navigator, 'cx', String(navigator.position.x));
    this.attribute(this.navigator, 'cy', String(navigator.position.y));
    this.attribute(this.navigator, 'visibility', navigator.active ? 'visible' : 'hidden');
    this.attribute(this.debug, 'visibility', this.showAnchors ? 'visible' : 'hidden');
    if (this.showAnchors) {
      let index = 0;
      anchors.forEach((a) => {
        let nodes = this.anchorNodes[index++];
        if (!nodes) {
          nodes = {
            line: document.createElementNS(NS, 'line'),
            current: document.createElementNS(NS, 'circle'),
            target: document.createElementNS(NS, 'circle'),
          };
          nodes.current.setAttribute('r', '2');
          nodes.target.setAttribute('r', '3');
          this.debug.append(nodes.line, nodes.current, nodes.target);
          this.anchorNodes.push(nodes);
        }
        this.attribute(nodes.line, 'x1', String(a.current.x));
        this.attribute(nodes.line, 'y1', String(a.current.y));
        this.attribute(nodes.line, 'x2', String(a.target.x));
        this.attribute(nodes.line, 'y2', String(a.target.y));
        for (const [node, point] of [
          [nodes.current, a.current],
          [nodes.target, a.target],
        ] as const) {
          this.attribute(node, 'cx', String(point.x));
          this.attribute(node, 'cy', String(point.y));
        }
      });
      while (this.anchorNodes.length > index) {
        const nodes = this.anchorNodes.pop()!;
        nodes.line.remove();
        nodes.current.remove();
        nodes.target.remove();
      }
    }
  }
  dispose(): void {
    this.debug.remove();
    this.navigator.remove();
  }
}
