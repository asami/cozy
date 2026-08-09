const DEFAULT_CLEARANCE = 24;
const DEFAULT_CANVAS = Object.freeze({ width: 1004, height: 382 });
const NODE_HEIGHT = 64;
const NODE_MIN_WIDTH = 132;
const NODE_MAX_WIDTH = 244;
const LABEL_FONT_SIZE = 21;
const MIN_LABEL_FONT_SIZE = 18;
const NODE_PADDING_X = 20;
const NODE_PADDING_Y = 13;

export class DiagramLayoutError extends Error {
  constructor(sceneId, violation, nodeId = null) {
    super(`Diagram scene ${sceneId}${nodeId ? ` node ${nodeId}` : ""}: ${violation}`);
    this.name = "DiagramLayoutError";
    this.sceneId = sceneId;
    this.nodeId = nodeId;
    this.violation = violation;
  }
}

function fail(sceneId, violation, nodeId = null) {
  throw new DiagramLayoutError(sceneId, violation, nodeId);
}

function estimateTextWidth(text, fontSize = LABEL_FONT_SIZE) {
  return Array.from(text || "").reduce((width, char) => width + (/[^\u0000-\u007f]/.test(char) ? fontSize : fontSize * 0.56), 0);
}

function legalSegments(label) {
  return String(label).split(/(?<=-)|\s+/).filter(Boolean);
}

function labelLines(node, maxTextWidth, sceneId, fontSize) {
  const label = node.label.trim();
  if (node.labelPolicy === "atomic") {
    if (estimateTextWidth(label, fontSize) > maxTextWidth) {
      fail(sceneId, "impossible-atomic-fit", node.id);
    }
    return [label];
  }
  const segments = legalSegments(label);
  if (segments.some((segment) => estimateTextWidth(segment, fontSize) > maxTextWidth)) {
    fail(sceneId, "label-overflow", node.id);
  }
  const lines = [];
  let current = "";
  segments.forEach((segment) => {
    const candidate = current ? `${current}${current.endsWith("-") ? "" : " "}${segment}` : segment;
    if (current && estimateTextWidth(candidate, fontSize) > maxTextWidth) {
      lines.push(current);
      current = segment;
    } else {
      current = candidate;
    }
  });
  if (current) lines.push(current);
  if (lines.length === 0 || lines.length > 3) {
    fail(sceneId, "label-overflow", node.id);
  }
  return lines;
}

function sizedNode(node, sceneId, fontSize = LABEL_FONT_SIZE) {
  const naturalWidth = Math.max(...legalSegments(node.label).map((segment) => estimateTextWidth(segment, fontSize)), estimateTextWidth(node.label, fontSize));
  const width = Math.max(NODE_MIN_WIDTH, Math.min(NODE_MAX_WIDTH, Math.ceil(naturalWidth + NODE_PADDING_X * 2)));
  const lines = labelLines(node, width - NODE_PADDING_X * 2, sceneId, fontSize);
  return {
    ...node,
    width,
    height: Math.max(NODE_HEIGHT, lines.length * Math.round(fontSize * 1.2) + NODE_PADDING_Y * 2),
    lines,
    fontSize,
  };
}

function rectanglesOverlap(a, b) {
  return a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y;
}

function rectDistance(a, b) {
  const dx = Math.max(a.x - (b.x + b.width), b.x - (a.x + a.width), 0);
  const dy = Math.max(a.y - (b.y + b.height), b.y - (a.y + a.height), 0);
  return Math.max(dx, dy);
}

function segmentIntersectsRect(start, end, rect) {
  const expanded = { x: rect.x - 1, y: rect.y - 1, width: rect.width + 2, height: rect.height + 2 };
  if (start.x === end.x) {
    return start.x >= expanded.x && start.x <= expanded.x + expanded.width && Math.max(start.y, end.y) >= expanded.y && Math.min(start.y, end.y) <= expanded.y + expanded.height;
  }
  return start.y >= expanded.y && start.y <= expanded.y + expanded.height && Math.max(start.x, end.x) >= expanded.x && Math.min(start.x, end.x) <= expanded.x + expanded.width;
}

function anchorPoints(node) {
  return [
    { x: node.x + node.width, y: node.y + node.height / 2 },
    { x: node.x, y: node.y + node.height / 2 },
    { x: node.x + node.width / 2, y: node.y + node.height },
    { x: node.x + node.width / 2, y: node.y },
  ].map((point) => ({ x: Math.round(point.x), y: Math.round(point.y) }));
}

function routeGrid(start, end, blockers, canvas) {
  const xs = Array.from(new Set([2, canvas.width - 2, start.x, end.x, ...blockers.flatMap((node) => [Math.max(2, node.x - 2), Math.min(canvas.width - 2, node.x + node.width + 2)])])).sort((a, b) => a - b);
  const ys = Array.from(new Set([2, canvas.height - 2, start.y, end.y, ...blockers.flatMap((node) => [Math.max(2, node.y - 2), Math.min(canvas.height - 2, node.y + node.height + 2)])])).sort((a, b) => a - b);
  const indexFor = (x, y) => `${x}:${y}`;
  const blocked = (point) => blockers.some((node) => point.x > node.x && point.x < node.x + node.width && point.y > node.y && point.y < node.y + node.height);
  const distances = new Map([[indexFor(start.x, start.y), 0]]);
  const previous = new Map();
  const queue = [{ point: start, distance: 0 }];
  while (queue.length) {
    queue.sort((a, b) => a.distance - b.distance || a.point.y - b.point.y || a.point.x - b.point.x);
    const current = queue.shift();
    const currentKey = indexFor(current.point.x, current.point.y);
    if (current.distance !== distances.get(currentKey)) continue;
    if (current.point.x === end.x && current.point.y === end.y) {
      const path = [end];
      let key = currentKey;
      while (previous.has(key)) {
        const point = previous.get(key);
        path.push(point);
        key = indexFor(point.x, point.y);
      }
      return path.reverse();
    }
    const xi = xs.indexOf(current.point.x);
    const yi = ys.indexOf(current.point.y);
    const candidates = [[xi - 1, yi], [xi + 1, yi], [xi, yi - 1], [xi, yi + 1]].filter(([x, y]) => x >= 0 && y >= 0 && x < xs.length && y < ys.length).map(([x, y]) => ({ x: xs[x], y: ys[y] }));
    candidates.forEach((next) => {
      if (blocked(next) || blockers.some((node) => segmentIntersectsRect(current.point, next, node))) return;
      const key = indexFor(next.x, next.y);
      const distance = current.distance + Math.abs(next.x - current.point.x) + Math.abs(next.y - current.point.y);
      if (distance < (distances.get(key) ?? Infinity)) {
        distances.set(key, distance);
        previous.set(key, current.point);
        queue.push({ point: next, distance });
      }
    });
  }
  return null;
}

function simplifyPath(points) {
  return points.filter((point, index, all) => index === 0 || index === all.length - 1 || (all[index - 1].x - point.x) * (point.y - all[index + 1].y) !== (all[index - 1].y - point.y) * (point.x - all[index + 1].x));
}

function routeEdge(from, to, allNodes, canvas, sceneId) {
  const blockers = allNodes.filter((node) => node.id !== from.id && node.id !== to.id);
  const routes = anchorPoints(from).flatMap((start) => anchorPoints(to).map((end) => routeGrid(start, end, blockers, canvas))).filter(Boolean);
  if (routes.length === 0) fail(sceneId, "node-edge-overlap", to.id);
  return simplifyPath(routes.sort((a, b) => a.length - b.length || a.map((point) => `${point.y}:${point.x}`).join("|").localeCompare(b.map((point) => `${point.y}:${point.x}`).join("|")))[0]);
}

function validateInput(diagram, sceneId) {
  if (!diagram || typeof diagram !== "object") fail(sceneId, "missing-diagram");
  if (diagram.layout !== "flow" && diagram.layout !== "axis") fail(sceneId, "unsupported-layout");
  if ((diagram.direction || "right") !== "right") fail(sceneId, "unsupported-direction");
  const clearance = diagram.clearance === undefined ? DEFAULT_CLEARANCE : Number(diagram.clearance);
  if (!Number.isFinite(clearance) || clearance <= 0) fail(sceneId, "invalid-clearance");
  if (!Array.isArray(diagram.nodes) || diagram.nodes.length === 0) fail(sceneId, "empty-nodes");
  const ids = new Set();
  const nodes = diagram.nodes.map((raw) => {
    if (!raw || typeof raw !== "object" || typeof raw.id !== "string" || !raw.id.trim()) fail(sceneId, "invalid-node-id");
    if (ids.has(raw.id)) fail(sceneId, "duplicate-node-id", raw.id);
    ids.add(raw.id);
    if (typeof raw.label !== "string" || !raw.label.trim()) fail(sceneId, "invalid-node-label", raw.id);
    if (typeof raw.role !== "string" || !raw.role.trim()) fail(sceneId, "invalid-node-role", raw.id);
    if (raw.labelPolicy !== "atomic" && raw.labelPolicy !== "balanced") fail(sceneId, "unsupported-label-policy", raw.id);
    return { ...raw, id: raw.id.trim(), label: raw.label.trim(), role: raw.role.trim() };
  });
  const edges = diagram.edges === undefined ? [] : diagram.edges;
  if (!Array.isArray(edges)) fail(sceneId, "invalid-edges");
  edges.forEach((edge) => {
    if (!edge || typeof edge.from !== "string" || typeof edge.to !== "string") fail(sceneId, "invalid-edge");
    if (!ids.has(edge.from)) fail(sceneId, "missing-edge-endpoint", edge.from);
    if (!ids.has(edge.to)) fail(sceneId, "missing-edge-endpoint", edge.to);
  });
  if (diagram.layout === "axis" && nodes.filter((node) => node.role === "axis").length !== 1) fail(sceneId, "axis-role-count");
  return { nodes, edges, clearance };
}

function flowPositions(nodes, canvas, clearance, maxColumns) {
  const rows = Array.from({ length: Math.ceil(nodes.length / maxColumns) }, (_, index) => nodes.slice(index * maxColumns, (index + 1) * maxColumns));
  const rowWidths = rows.map((row) => row.reduce((sum, node) => sum + node.width, 0) + clearance * Math.max(0, row.length - 1));
  const rowHeights = rows.map((row) => Math.max(...row.map((node) => node.height)));
  const totalHeight = rowHeights.reduce((sum, height) => sum + height, 0) + clearance * Math.max(0, rows.length - 1);
  if (Math.max(...rowWidths) > canvas.width - clearance * 2 || totalHeight > canvas.height - clearance * 2) return null;
  let y = Math.round((canvas.height - totalHeight) / 2);
  return rows.flatMap((row, rowIndex) => {
    let x = Math.round((canvas.width - rowWidths[rowIndex]) / 2);
    const positioned = row.map((node) => {
      const value = { ...node, x, y: Math.round(y + (rowHeights[rowIndex] - node.height) / 2) };
      x += node.width + clearance;
      return value;
    });
    y += rowHeights[rowIndex] + clearance;
    return positioned;
  });
}

function fittedFlowPositions(rawNodes, canvas, clearance, sceneId) {
  let lastError = null;
  for (let fontSize = LABEL_FONT_SIZE; fontSize >= MIN_LABEL_FONT_SIZE; fontSize -= 1) {
    try {
      const nodes = rawNodes.map((node) => sizedNode(node, sceneId, fontSize));
      const positioned = flowPositions(nodes, canvas, clearance, nodes.length);
      if (positioned) return positioned;
      lastError = new DiagramLayoutError(sceneId, "stage-overflow");
    } catch (error) {
      if (!(error instanceof DiagramLayoutError)) throw error;
      lastError = error;
    }
  }
  for (let columns = rawNodes.length - 1; columns >= 1; columns -= 1) {
    try {
      const nodes = rawNodes.map((node) => sizedNode(node, sceneId, MIN_LABEL_FONT_SIZE));
      const positioned = flowPositions(nodes, canvas, clearance, columns);
      if (positioned) return positioned;
      lastError = new DiagramLayoutError(sceneId, "stage-overflow");
    } catch (error) {
      if (!(error instanceof DiagramLayoutError)) throw error;
      lastError = error;
    }
  }
  throw lastError || new DiagramLayoutError(sceneId, "stage-overflow", rawNodes[0]?.id);
}

function axisPositions(nodes, canvas, clearance, sceneId) {
  const primary = nodes.filter((node) => node.role === "primary-view");
  const supporting = nodes.filter((node) => node.role === "supporting-view");
  const axis = nodes.find((node) => node.role === "axis");
  const lead = nodes.filter((node) => !["primary-view", "supporting-view"].includes(node.role));
  const rightWidth = primary.length ? Math.max(...primary.map((node) => node.width)) : 0;
  const bottomHeight = supporting.length ? Math.max(...supporting.map((node) => node.height)) : 0;
  const leadWidth = lead.reduce((sum, node) => sum + node.width, 0) + clearance * Math.max(0, lead.length - 1);
  const leadHeight = Math.max(...lead.map((node) => node.height));
  const reservedWidth = rightWidth ? rightWidth + clearance : 0;
  const reservedHeight = bottomHeight ? bottomHeight + clearance : 0;
  if (leadWidth > canvas.width - clearance * 2 - reservedWidth || leadHeight > canvas.height - clearance * 2 - reservedHeight) fail(sceneId, "stage-overflow", axis.id);
  if (primary.reduce((sum, node) => sum + node.height, 0) + clearance * Math.max(0, primary.length - 1) > canvas.height - clearance * 2 - reservedHeight) fail(sceneId, "stage-overflow", primary[0]?.id);
  if (supporting.reduce((sum, node) => sum + node.width, 0) + clearance * Math.max(0, supporting.length - 1) > canvas.width - clearance * 2 - reservedWidth) fail(sceneId, "stage-overflow", supporting[0]?.id);
  const placed = [];
  let leadX = clearance;
  const leadY = Math.round((canvas.height - reservedHeight - leadHeight) / 2);
  lead.forEach((node) => { placed.push({ ...node, x: leadX, y: leadY }); leadX += node.width + clearance; });
  let primaryY = clearance;
  primary.forEach((node) => { placed.push({ ...node, x: canvas.width - clearance - rightWidth, y: primaryY }); primaryY += node.height + clearance; });
  let supportX = clearance;
  supporting.forEach((node) => { placed.push({ ...node, x: supportX, y: canvas.height - clearance - bottomHeight }); supportX += node.width + clearance; });
  return placed;
}

function validateGeometry(nodes, edges, clearance, canvas, sceneId) {
  nodes.forEach((node) => {
    if (node.x < 0 || node.y < 0 || node.x + node.width > canvas.width || node.y + node.height > canvas.height) fail(sceneId, "stage-overflow", node.id);
  });
  nodes.forEach((node, index) => nodes.slice(index + 1).forEach((other) => {
    if (rectanglesOverlap(node, other)) fail(sceneId, "node-node-overlap", node.id);
    if (rectDistance(node, other) < clearance) fail(sceneId, "clearance-failure", node.id);
  }));
  const byId = new Map(nodes.map((node) => [node.id, node]));
  return edges.map((edge) => ({ ...edge, points: routeEdge(byId.get(edge.from), byId.get(edge.to), nodes, canvas, sceneId) }));
}

export function computeDiagramLayout(diagram, options = {}) {
  const sceneId = options.sceneId || "(no id)";
  const canvas = { ...DEFAULT_CANVAS, ...(options.canvas || {}) };
  const { nodes: rawNodes, edges, clearance } = validateInput(diagram, sceneId);
  const positioned = diagram.layout === "axis"
    ? axisPositions(rawNodes.map((node) => sizedNode(node, sceneId)), canvas, clearance, sceneId)
    : fittedFlowPositions(rawNodes, canvas, clearance, sceneId);
  const routedEdges = validateGeometry(positioned, edges, clearance, canvas, sceneId);
  return Object.freeze({
    canvas: Object.freeze(canvas),
    clearance,
    nodes: Object.freeze(positioned.map((node) => Object.freeze(node))),
    edges: Object.freeze(routedEdges.map((edge) => Object.freeze({ ...edge, points: Object.freeze(edge.points.map((point) => Object.freeze(point))) }))),
    diagnostics: Object.freeze([]),
  });
}
