const contracts = new Set([
  'homepage/film/model.ts',
  'homepage/contracts/progress.ts',
  'homepage/contracts/readiness.ts',
  'homepage/contracts/choreography.ts',
  'homepage/contracts/gallery.ts',
]);
export function featureEdgeAllowed(from, to) {
  const owner = (path) => /^homepage\/([^/]+)\//.exec(path)?.[1];
  const a = owner(from),
    b = owner(to);
  if (a === 'contracts' && b && b !== 'contracts') return false;
  if (a && !b && to.startsWith('homepage/')) return false;
  return !a || !b || a === b || contracts.has(to);
}
export function importCycles(graph) {
  const complete = new Set(),
    active = new Set(),
    stack = [],
    cycles = [];
  function visit(node) {
    if (active.has(node)) {
      cycles.push([...stack.slice(stack.indexOf(node)), node]);
      return;
    }
    if (complete.has(node)) return;
    active.add(node);
    stack.push(node);
    for (const target of graph.get(node) ?? []) visit(target);
    stack.pop();
    active.delete(node);
    complete.add(node);
  }
  for (const node of graph.keys()) visit(node);
  return cycles;
}
