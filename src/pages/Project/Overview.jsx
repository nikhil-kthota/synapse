import { useMemo } from 'react';
import { KIND_META } from './graphMeta';
import { formatNumber } from '../../utils/projectStatus';
import './Overview.css';

export default function Overview({ project, graph, onOpenNode }) {
  const stats = project.stats || {};
  const nodes = stats.nodes || {};
  const edges = stats.edges || {};

  const { hubs, libraries } = useMemo(() => {
    const degree = new Map();
    const inbound = new Map();
    for (const e of graph.edges) {
      if (e.kind === 'CONTAINS') continue;
      degree.set(e.source, (degree.get(e.source) || 0) + e.weight);
      degree.set(e.target, (degree.get(e.target) || 0) + e.weight);
      inbound.set(e.target, (inbound.get(e.target) || 0) + 1);
    }
    const byId = new Map(graph.nodes.map((n) => [n.id, n]));
    const ranked = [...degree.entries()]
      .map(([id, d]) => ({ node: byId.get(id), d }))
      .filter((x) => x.node && ['CLASS', 'FUNCTION', 'METHOD', 'FILE'].includes(x.node.kind))
      .sort((a, b) => b.d - a.d)
      .slice(0, 8);
    const libs = graph.nodes
      .filter((n) => n.kind === 'EXTERNAL')
      .map((n) => ({ node: n, uses: inbound.get(n.id) || 0 }))
      .sort((a, b) => b.uses - a.uses)
      .slice(0, 18);
    return { hubs: ranked, libraries: libs };
  }, [graph]);

  const languages = Object.entries(stats.languages || {}).sort((a, b) => b[1].lines - a[1].lines);
  const totalLines = languages.reduce((sum, [, v]) => sum + v.lines, 0) || 1;

  const tiles = [
    { label: 'Files', value: stats.files },
    { label: 'Lines of code', value: stats.linesOfCode },
    { label: 'Classes', value: nodes.CLASS || 0 },
    { label: 'Functions & methods', value: (nodes.FUNCTION || 0) + (nodes.METHOD || 0) },
    { label: 'API endpoints', value: nodes.ENDPOINT || 0 },
    { label: 'Libraries', value: nodes.EXTERNAL || 0 },
    { label: 'Call links', value: (edges.CALLS || 0) + (edges.INSTANTIATES || 0) },
    { label: 'Import links', value: edges.IMPORTS || 0 },
  ];

  return (
    <div className="overview">
      <div className="bento overview-tiles">
        {tiles.map((t) => (
          <div key={t.label} className="cell cell--3 overview-tile">
            <span className="overview-tile__value">{formatNumber(t.value)}</span>
            <span className="cell-label">{t.label}</span>
          </div>
        ))}
      </div>

      <div className="overview-columns">
        <div className="bento">
          <div className="cell cell--muted"><span className="cell-label">Languages</span></div>
          <div className="cell overview-langs">
            {languages.map(([name, v]) => (
              <div key={name} className="overview-lang">
                <div className="overview-lang__head">
                  <span>{name}</span>
                  <span className="mono">{v.files} files · {formatNumber(v.lines)} lines</span>
                </div>
                <div className="overview-bar"><div style={{ width: `${Math.max(1.5, (100 * v.lines) / totalLines)}%` }} /></div>
              </div>
            ))}
          </div>
          <div className="cell cell--tight">
            <p className="cell-hint">
              {stats.syntaxTreeParsedFiles ?? 0} files parsed with full syntax trees · {stats.skippedFiles ?? 0} binary or generated files skipped
              {stats.truncated ? ' · file limit reached, the largest repositories are analysed partially' : ''}
              {stats.durationSeconds !== undefined ? ` · analysed in ${stats.durationSeconds}s` : ''}
            </p>
          </div>
        </div>

        <div className="bento">
          <div className="cell cell--muted"><span className="cell-label">Most connected components</span></div>
          {hubs.length === 0 && <div className="cell cell-text">No cross-file relationships were found.</div>}
          {hubs.map(({ node, d }) => (
            <button key={node.id} type="button" className="cell overview-hub" onClick={() => onOpenNode(node.id)}>
              <span className="chip-dot" style={{ background: KIND_META[node.kind].color }} />
              <span className="overview-hub__text">
                <strong>{node.name}</strong>
                <span className="mono">{node.filePath}</span>
              </span>
              <span className="chip">{d} links</span>
            </button>
          ))}
        </div>
      </div>

      {libraries.length > 0 && (
        <div className="bento overview-libs">
          <div className="cell cell--muted"><span className="cell-label">Third-party libraries and modules</span></div>
          <div className="cell chip-row">
            {libraries.map(({ node, uses }) => (
              <button key={node.id} type="button" className="chip overview-lib" onClick={() => onOpenNode(node.id)}
                title={`Used by ${uses} file(s)`}>
                {node.name}{uses > 1 ? ` · ${uses}` : ''}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
