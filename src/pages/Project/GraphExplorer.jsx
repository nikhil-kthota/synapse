import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import cytoscape from 'cytoscape';
import fcose from 'cytoscape-fcose';
import { api } from '../../api/client';
import { KIND_META, EDGE_META, KIND_ORDER } from './graphMeta';
import './GraphExplorer.css';

cytoscape.use(fcose);

const MAX_VISIBLE = 1200;
// Above this many nodes, methods and functions start hidden so the overview stays readable.
const DETAIL_HIDE_THRESHOLD = 300;
const LARGE_GRAPH = 700;

/**
 * Semantic zoom: how much node, label and edge sizes are counter-scaled at a zoom level.
 * Zooming in grows the space between nodes much faster than the nodes themselves;
 * zooming out keeps nodes from shrinking to dots.
 */
function zoomScale(zoom) {
  return zoom >= 1 ? Math.pow(zoom, -0.65) : Math.pow(zoom, -0.4);
}

/** After a layout, size nodes for the new zoom and refit so the graph fills the canvas. */
function settleZoom(cy, scaleRef) {
  for (let i = 0; i < 2; i++) {
    scaleRef.current = zoomScale(cy.zoom());
    cy.style().update();
    cy.fit(undefined, 40);
  }
}

function layoutOptions(nodeCount, animate) {
  const spread = Math.sqrt(Math.max(nodeCount, 1));
  return {
    name: 'fcose',
    quality: nodeCount > 500 ? 'draft' : 'default',
    randomize: true,
    animate,
    animationDuration: 600,
    nodeDimensionsIncludeLabels: true,
    nodeSeparation: 110,
    nodeRepulsion: () => 14000 + nodeCount * 40,
    idealEdgeLength: (edge) => (edge.data('kind') === 'CONTAINS' ? 55 : 110) + spread * 3,
    edgeElasticity: () => 0.35,
    gravity: 0.18,
    gravityRange: 4,
    numIter: nodeCount > 500 ? 1500 : 2800,
    tile: true,
    tilingPaddingVertical: 40,
    tilingPaddingHorizontal: 40,
    packComponents: true,
    fit: true,
    padding: 40,
  };
}

export default function GraphExplorer({ projectId, graph, initialNodeId, onNodeShown, onSelectionChange }) {
  const containerRef = useRef(null);
  const cyRef = useRef(null);
  const scaleRef = useRef(1);

  const { byId, degree, adjacency, kindCounts, edgeCounts } = useMemo(() => indexGraph(graph), [graph]);

  const [kinds, setKinds] = useState(() => {
    const n = graph.nodes.length;
    return new Set(KIND_ORDER.filter((k) => !(
      (n > DETAIL_HIDE_THRESHOLD && (k === 'METHOD' || k === 'FUNCTION' || k === 'EXTERNAL'))
      || (n > LARGE_GRAPH && k === 'DIRECTORY'))));
  });
  const [edgeKinds, setEdgeKinds] = useState(() => new Set(Object.keys(EDGE_META)));
  const [focusId, setFocusId] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState(null);

  // ---------------------------------------------------------------- visible subgraph
  const visible = useMemo(() => {
    let ids;
    if (focusId != null) {
      ids = new Set([focusId]);
      for (const { other, kind } of adjacency.get(focusId) || []) {
        if (edgeKinds.has(kind)) ids.add(other);
      }
    } else {
      ids = new Set(graph.nodes.filter((n) => kinds.has(n.kind)).map((n) => n.id));
    }
    let capped = false;
    if (ids.size > MAX_VISIBLE) {
      capped = true;
      ids = new Set([...ids].sort((a, b) => (degree.get(b) || 0) - (degree.get(a) || 0)).slice(0, MAX_VISIBLE));
    }
    const edges = graph.edges.filter((e) => edgeKinds.has(e.kind) && ids.has(e.source) && ids.has(e.target)
      && !(e.kind === 'DEPENDS_ON' && byId.get(e.source)?.kind === 'REPOSITORY'));
    return { ids, edges, capped };
  }, [graph, kinds, edgeKinds, focusId, adjacency, degree, byId]);

  // ---------------------------------------------------------------- cytoscape lifecycle
  useEffect(() => {
    const cy = cytoscape({
      container: containerRef.current,
      wheelSensitivity: 0.25,
      minZoom: 0.05,
      maxZoom: 3,
      style: cyStyle(scaleRef),
    });
    cyRef.current = cy;
    let frame = 0;
    cy.on('zoom', () => {
      if (frame) return;
      frame = requestAnimationFrame(() => {
        frame = 0;
        const next = zoomScale(cy.zoom());
        if (Math.abs(next - scaleRef.current) / scaleRef.current > 0.03) {
          scaleRef.current = next;
          cy.style().update();
        }
      });
    });
    cy.on('tap', 'node', (evt) => setSelectedId(Number(evt.target.id())));
    cy.on('tap', (evt) => { if (evt.target === cy) setSelectedId(null); });
    return () => {
      if (frame) cancelAnimationFrame(frame);
      cy.destroy();
    };
  }, []);

  useEffect(() => {
    const cy = cyRef.current;
    if (!cy) return;
    const elements = [];
    for (const id of visible.ids) {
      const n = byId.get(id);
      const d = degree.get(id) || 0;
      elements.push({
        group: 'nodes',
        data: {
          id: String(id),
          label: n.kind === 'DIRECTORY' ? `${n.name}/` : n.name,
          color: KIND_META[n.kind].color,
          size: Math.round(16 + Math.min(34, Math.sqrt(d) * 5)),
          kind: n.kind,
        },
      });
    }
    visible.edges.forEach((e, i) => {
      const meta = EDGE_META[e.kind];
      elements.push({
        group: 'edges',
        data: {
          id: `e${i}-${e.source}-${e.target}-${e.kind}`,
          source: String(e.source),
          target: String(e.target),
          color: meta.color,
          lineStyle: meta.style,
          width: Math.min(4, 1 + Math.log2(e.weight)),
          kind: e.kind,
        },
      });
    });
    cy.batch(() => {
      cy.elements().remove();
      cy.add(elements);
    });
    cy.edges().style('curve-style', visible.edges.length > 2500 ? 'haystack' : 'bezier');
    const layout = cy.layout(layoutOptions(visible.ids.size, visible.ids.size < 250));
    layout.one('layoutstop', () => settleZoom(cy, scaleRef));
    layout.run();
  }, [visible, byId, degree]);

  // Tell the page (and the assistant) what is selected.
  useEffect(() => {
    const n = selectedId == null ? null : byId.get(selectedId);
    onSelectionChange?.(n ? { id: n.id, name: n.name, kind: n.kind } : null);
  }, [selectedId, byId, onSelectionChange]);

  // Highlight the selected node and its neighbours.
  useEffect(() => {
    const cy = cyRef.current;
    if (!cy) return;
    cy.batch(() => {
      cy.elements().removeClass('faded selected neighbour');
      if (selectedId == null) return;
      const node = cy.getElementById(String(selectedId));
      if (node.empty()) return;
      const hood = node.closedNeighborhood();
      cy.elements().not(hood).addClass('faded');
      hood.nodes().addClass('neighbour');
      node.addClass('selected');
    });
  }, [selectedId, visible]);

  const revealNode = useCallback((id) => {
    if (!visible.ids.has(id)) {
      setFocusId(id);
    }
    setSelectedId(id);
    setTimeout(() => {
      const cy = cyRef.current;
      const node = cy?.getElementById(String(id));
      if (node && !node.empty()) cy.animate({ center: { eles: node }, zoom: Math.max(cy.zoom(), 1.1) }, { duration: 450 });
    }, 700);
  }, [visible]);

  useEffect(() => {
    if (initialNodeId != null && byId.has(initialNodeId)) {
      revealNode(initialNodeId);
      onNodeShown?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialNodeId]);

  // ---------------------------------------------------------------- search
  useEffect(() => {
    const q = query.trim();
    if (q.length < 2) { setResults(null); return undefined; }
    const timer = setTimeout(() => {
      api.search(projectId, q).then(setResults).catch(() => setResults([]));
    }, 280);
    return () => clearTimeout(timer);
  }, [query, projectId]);

  const toggle = (setter, value) => setter((prev) => {
    const next = new Set(prev);
    if (next.has(value)) next.delete(value); else next.add(value);
    return next;
  });

  const fit = () => cyRef.current?.animate({ fit: { padding: 30 } }, { duration: 400 });

  const relayout = () => {
    const cy = cyRef.current;
    if (!cy) return;
    const layout = cy.layout(layoutOptions(cy.nodes().length, true));
    layout.one('layoutstop', () => settleZoom(cy, scaleRef));
    layout.run();
  };

  return (
    <div className="gx-window cad">
      <div className="gx-chrome">
        <div className="gx-dots"><span style={{ background: '#FF5F57' }} /><span style={{ background: '#FFBD2E' }} /><span style={{ background: '#28CA41' }} /></div>
        <div className="gx-title">Synapse — Knowledge Graph Viewer</div>
        <div className="gx-chrome-right">
          <span className="chip">{graph.edges.length.toLocaleString()} links</span>
          <span className="chip chip--blue">{visible.ids.size.toLocaleString()} / {graph.nodes.length.toLocaleString()} nodes</span>
        </div>
      </div>

      <div className={`gx-body ${selectedId != null ? 'gx-body--panel' : ''}`}>
        <aside className="gx-sidebar" data-lenis-prevent>
          <div className="gx-search">
            <span aria-hidden="true">⌕</span>
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search classes, files, APIs…"
              aria-label="Search the knowledge graph" />
            {query && <button type="button" onClick={() => setQuery('')} aria-label="Clear search">×</button>}
          </div>

          {results ? (
            <div className="gx-results">
              {results.length === 0 && <p className="gx-empty">No matches.</p>}
              {results.map((n) => (
                <button key={n.id} type="button" className={`gx-item ${selectedId === n.id ? 'active' : ''}`}
                  onClick={() => revealNode(n.id)} title={n.qualifiedName}>
                  <span className="gx-badge" style={{ background: KIND_META[n.kind].color }}>{KIND_META[n.kind].label[0]}</span>
                  <span className="gx-item-text">
                    <span className="gx-item-name">{n.name}</span>
                    <span className="gx-item-path">{n.filePath || KIND_META[n.kind].label}</span>
                  </span>
                </button>
              ))}
            </div>
          ) : (
            <>
              <div className="gx-group">
                <span className="gx-group-title">Show</span>
                {KIND_ORDER.filter((k) => kindCounts[k]).map((k) => (
                  <label key={k} className="gx-toggle">
                    <input type="checkbox" checked={kinds.has(k)} onChange={() => toggle(setKinds, k)} disabled={focusId != null} />
                    <span className="chip-dot" style={{ background: KIND_META[k].color }} />
                    <span className="gx-toggle-label">{KIND_META[k].plural}</span>
                    <span className="gx-count">{kindCounts[k].toLocaleString()}</span>
                  </label>
                ))}
              </div>
              <div className="gx-group">
                <span className="gx-group-title">Relationships</span>
                {Object.keys(EDGE_META).filter((k) => edgeCounts[k]).map((k) => (
                  <label key={k} className="gx-toggle">
                    <input type="checkbox" checked={edgeKinds.has(k)} onChange={() => toggle(setEdgeKinds, k)} />
                    <span className={`gx-line gx-line--${EDGE_META[k].style}`} style={{ borderColor: EDGE_META[k].color }} />
                    <span className="gx-toggle-label">{EDGE_META[k].label}</span>
                    <span className="gx-count">{edgeCounts[k].toLocaleString()}</span>
                  </label>
                ))}
              </div>
            </>
          )}

          <div className="gx-sidebar-actions">
            <button type="button" className="btn-line" onClick={fit}>Fit</button>
            <button type="button" className="btn-line" onClick={relayout}>Re-layout</button>
          </div>
        </aside>

        <div className="gx-canvas-wrap" data-lenis-prevent>
          {focusId != null && (
            <div className="gx-banner">
              Focused on <strong>{byId.get(focusId)?.name}</strong> and its direct connections
              <button type="button" className="btn-line" onClick={() => setFocusId(null)}>Show full graph</button>
            </div>
          )}
          {visible.capped && focusId == null && (
            <div className="gx-banner gx-banner--warn">
              Showing the {MAX_VISIBLE.toLocaleString()} most connected nodes. Hide some kinds or search to narrow down.
            </div>
          )}
          <div ref={containerRef} className="gx-canvas" />
          {visible.ids.size === 0 && <div className="gx-canvas-empty">Nothing to show. Turn on some node kinds.</div>}
        </div>

        {selectedId != null && (
          <NodePanel
            projectId={projectId}
            nodeId={selectedId}
            onClose={() => setSelectedId(null)}
            onNavigate={revealNode}
            onFocus={(id) => { setFocusId(id); setSelectedId(id); }}
          />
        )}
      </div>
    </div>
  );
}

function NodePanel({ projectId, nodeId, onClose, onNavigate, onFocus }) {
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState('');
  const [impact, setImpact] = useState(null);
  const [showSource, setShowSource] = useState(false);

  useEffect(() => {
    setDetail(null);
    setImpact(null);
    setError('');
    setShowSource(false);
    api.node(projectId, nodeId).then(setDetail).catch((err) => setError(err.message));
  }, [projectId, nodeId]);

  const loadImpact = () => {
    setImpact('loading');
    api.impact(projectId, nodeId, 4).then(setImpact).catch((err) => { setImpact(null); setError(err.message); });
  };

  const groups = useMemo(() => {
    if (!detail) return [];
    const map = new Map();
    for (const nb of detail.neighbors) {
      const meta = EDGE_META[nb.edgeKind];
      const label = nb.direction === 'out' ? meta.label : meta.inverse;
      if (!map.has(label)) map.set(label, []);
      map.get(label).push(nb);
    }
    return [...map.entries()];
  }, [detail]);

  const node = detail?.node;

  return (
    <aside className="gx-panel" data-lenis-prevent>
      <button type="button" className="gx-panel-close" onClick={onClose} aria-label="Close details">×</button>
      {error && <p className="app-error">{error}</p>}
      {!detail && !error && <div className="gx-panel-loading"><span className="app-spinner" /></div>}
      {node && (
        <>
          <div className="gx-tag" style={{ background: KIND_META[node.kind].color }}>
            {KIND_META[node.kind].label}
            {node.subkind && !['FILE', 'ENDPOINT'].includes(node.kind) && node.subkind !== KIND_META[node.kind].label.toLowerCase()
              ? ` · ${node.subkind}` : ''}
          </div>
          <h3 className="gx-panel-name">{node.name}</h3>
          {node.filePath && (
            <p className="gx-panel-path mono">
              {node.filePath}{node.startLine ? `:${node.startLine}${node.endLine && node.endLine !== node.startLine ? `–${node.endLine}` : ''}` : ''}
            </p>
          )}

          <div className="gx-summary">
            <span className="gx-group-title">AI summary</span>
            <p>{node.summary || (node.kind === 'EXTERNAL'
              ? 'A third-party library or module this project depends on.'
              : 'No AI summary for this element. Summaries are written for the most important files and their declarations.')}</p>
          </div>

          {node.signature && node.kind !== 'ENDPOINT' && <pre className="gx-signature">{node.signature}</pre>}

          <div className="gx-panel-actions">
            <button type="button" className="btn-line" onClick={() => onFocus(node.id)}>Focus</button>
            {node.kind !== 'REPOSITORY' && (
              <button type="button" className="btn-line" onClick={loadImpact}>Impact analysis</button>
            )}
            {detail.snippet && (
              <button type="button" className="btn-line btn-line--blue" onClick={() => setShowSource(true)}>View source</button>
            )}
          </div>

          {impact && (
            <div className="gx-impact">
              <span className="gx-group-title">If this changes, these may be affected</span>
              {impact === 'loading' && <p className="gx-empty"><span className="app-spinner" /></p>}
              {Array.isArray(impact) && impact.length === 0 && <p className="gx-empty">Nothing else depends on this element.</p>}
              {Array.isArray(impact) && impact.length > 0 && (
                <>
                  <p className="gx-impact-count">{impact.length} dependent element{impact.length === 1 ? '' : 's'}</p>
                  {impact.slice(0, 60).map((r) => (
                    <button key={r.id} type="button" className="gx-dep" onClick={() => onNavigate(r.id)}>
                      <span className="gx-dep-depth">{r.depth === 1 ? 'direct' : `${r.depth} hops`}</span>
                      <span className="chip-dot" style={{ background: KIND_META[r.kind]?.color }} />
                      <span className="gx-dep-name">{r.name}</span>
                    </button>
                  ))}
                </>
              )}
            </div>
          )}

          <div className="gx-relations">
            {groups.map(([label, list]) => (
              <div key={label} className="gx-relation">
                <span className="gx-group-title">{label} · {list.length}</span>
                {list.slice(0, 25).map((nb) => (
                  <button key={`${label}-${nb.node.id}`} type="button" className="gx-dep" onClick={() => onNavigate(nb.node.id)}
                    title={nb.node.qualifiedName}>
                    <span className="chip-dot" style={{ background: KIND_META[nb.node.kind].color }} />
                    <span className="gx-dep-name">{nb.node.name}</span>
                    {nb.lines?.length > 0 ? (
                      <span className="gx-dep-loc" title={`${nb.direction === 'in' ? nb.node.filePath : node.filePath} line ${nb.lines.join(', ')}`}>
                        {nb.direction === 'in' ? baseName(nb.node.filePath) : ''}:{nb.lines.slice(0, 3).join(',')}{nb.lines.length > 3 ? '…' : ''}
                      </span>
                    ) : nb.weight > 1 && <span className="gx-count">×{nb.weight}</span>}
                  </button>
                ))}
                {list.length > 25 && <p className="gx-empty">and {list.length - 25} more</p>}
              </div>
            ))}
          </div>

          {showSource && (
            <SourceModal title={node.filePath || node.name} startLine={node.startLine || 1} code={detail.snippet}
              onClose={() => setShowSource(false)} />
          )}
        </>
      )}
    </aside>
  );
}

function SourceModal({ title, startLine, code, onClose }) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  const lines = code.split('\n');
  return (
    <div className="gx-modal" role="dialog" aria-modal="true" aria-label={`Source of ${title}`} onClick={onClose}>
      <div className="gx-modal-box" onClick={(e) => e.stopPropagation()}>
        <div className="gx-chrome">
          <div className="gx-title gx-title--left mono">{title}</div>
          <button type="button" className="btn-line" onClick={onClose}>Close</button>
        </div>
        <pre className="gx-code" data-lenis-prevent>
          {lines.map((line, i) => (
            <div key={i} className="gx-code-line"><span className="gx-code-num">{startLine + i}</span><span>{line || ' '}</span></div>
          ))}
        </pre>
      </div>
    </div>
  );
}

function baseName(path) {
  return path ? path.slice(path.lastIndexOf('/') + 1) : '';
}

function indexGraph(graph) {
  const byId = new Map(graph.nodes.map((n) => [n.id, n]));
  const degree = new Map();
  const adjacency = new Map();
  const kindCounts = {};
  const edgeCounts = {};
  for (const n of graph.nodes) kindCounts[n.kind] = (kindCounts[n.kind] || 0) + 1;
  for (const e of graph.edges) {
    edgeCounts[e.kind] = (edgeCounts[e.kind] || 0) + 1;
    degree.set(e.source, (degree.get(e.source) || 0) + 1);
    degree.set(e.target, (degree.get(e.target) || 0) + 1);
    if (!adjacency.has(e.source)) adjacency.set(e.source, []);
    if (!adjacency.has(e.target)) adjacency.set(e.target, []);
    adjacency.get(e.source).push({ other: e.target, kind: e.kind });
    adjacency.get(e.target).push({ other: e.source, kind: e.kind });
  }
  return { byId, degree, adjacency, kindCounts, edgeCounts };
}

function cyStyle(scaleRef) {
  // Sizes are multiplied by the semantic-zoom scale so nodes stay a readable size on screen.
  const s = () => scaleRef.current;
  return [
    {
      selector: 'node',
      style: {
        width: (n) => n.data('size') * s(),
        height: (n) => n.data('size') * s(),
        'background-color': 'data(color)',
        'background-opacity': 0.2,
        'border-width': () => 2 * s(),
        'border-color': 'data(color)',
        label: 'data(label)',
        'font-family': 'ClashDisplay, Inter, system-ui, sans-serif',
        'font-size': () => 11 * s(),
        'font-weight': 600,
        color: '#000000',
        'text-valign': 'bottom',
        'text-margin-y': () => 5 * s(),
        'text-max-width': () => 140 * s(),
        'text-wrap': 'ellipsis',
        'text-background-color': '#C4C8D0',
        'text-background-opacity': 0.8,
        'text-background-padding': () => 2 * s(),
        'text-background-shape': 'roundrectangle',
        'min-zoomed-font-size': 8,
        'transition-property': 'opacity',
        'transition-duration': 200,
      },
    },
    { selector: 'node[kind = "REPOSITORY"]', style: { shape: 'round-rectangle', 'background-opacity': 0.9, 'font-size': () => 14 * s() } },
    { selector: 'node[kind = "DIRECTORY"]', style: { shape: 'round-rectangle' } },
    { selector: 'node[kind = "FILE"]', style: { shape: 'round-rectangle', 'background-opacity': 0.08 } },
    { selector: 'node[kind = "ENDPOINT"]', style: { shape: 'diamond' } },
    { selector: 'node[kind = "EXTERNAL"]', style: { shape: 'hexagon', 'border-style': 'dashed' } },
    {
      selector: 'edge',
      style: {
        width: (e) => e.data('width') * s(),
        'line-color': 'data(color)',
        'line-style': 'data(lineStyle)',
        'target-arrow-color': 'data(color)',
        'target-arrow-shape': 'triangle',
        'arrow-scale': () => 0.8 * Math.max(0.6, s()),
        'curve-style': 'bezier',
        opacity: 0.75,
      },
    },
    { selector: 'edge[kind = "CONTAINS"]', style: { 'target-arrow-shape': 'none', opacity: 0.6 } },
    { selector: '.faded', style: { opacity: 0.12 } },
    { selector: 'node.neighbour', style: { 'background-opacity': 0.45 } },
    {
      selector: 'node.selected',
      style: { 'border-width': () => 4 * s(), 'background-opacity': 0.9, 'overlay-color': '#2B7FD9', 'overlay-opacity': 0.12, 'overlay-padding': () => 8 * s(), 'z-index': 10 },
    },
  ];
}
