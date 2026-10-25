import { useMemo, useState } from 'react';
import './ApiEndpoints.css';

const METHOD_CLASS = {
  GET: 'chip--ok',
  POST: 'chip--blue',
  PUT: 'chip--warn',
  PATCH: 'chip--warn',
  DELETE: 'chip--err',
};

export default function ApiEndpoints({ graph, onOpenNode }) {
  const [filter, setFilter] = useState('');

  const endpoints = useMemo(() => {
    const byId = new Map(graph.nodes.map((n) => [n.id, n]));
    const handlerOf = new Map();
    for (const e of graph.edges) {
      if (e.kind === 'HANDLES') handlerOf.set(e.source, byId.get(e.target));
    }
    return graph.nodes
      .filter((n) => n.kind === 'ENDPOINT')
      .map((n) => {
        const [method, ...rest] = n.name.split(' ');
        return { node: n, method, path: rest.join(' ') || '/', handler: handlerOf.get(n.id) };
      })
      .sort((a, b) => a.path.localeCompare(b.path) || a.method.localeCompare(b.method));
  }, [graph]);

  const shown = endpoints.filter((e) => {
    const q = filter.trim().toLowerCase();
    return !q || e.path.toLowerCase().includes(q) || e.method.toLowerCase() === q || e.handler?.name.toLowerCase().includes(q);
  });

  if (endpoints.length === 0) {
    return (
      <div className="bento">
        <div className="cell cell--center api-empty">
          <div>
            <div className="cell-title">No HTTP endpoints detected</div>
            <p className="cell-text">
              Synapse recognises routes from Spring, JAX-RS, ASP.NET, Express, NestJS, Flask, FastAPI, Django, Gin, Laravel and Rails.
            </p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="bento">
      <div className="cell cell--tight api-toolbar">
        <span className="cell-label">{endpoints.length} endpoint{endpoints.length === 1 ? '' : 's'}</span>
        <input className="app-input api-filter" placeholder="Filter by path, method or handler" value={filter}
          onChange={(e) => setFilter(e.target.value)} />
      </div>
      <div className="cell api-table-cell">
        <table className="api-table">
          <thead>
            <tr><th>Method</th><th>Path</th><th>Handler</th><th>What it does</th></tr>
          </thead>
          <tbody>
            {shown.map(({ node, method, path, handler }) => (
              <tr key={node.id} onClick={() => onOpenNode(node.id)} title="Show in knowledge graph">
                <td><span className={`chip ${METHOD_CLASS[method] || 'chip--dark'}`}>{method}</span></td>
                <td className="mono api-path">{path}</td>
                <td>
                  {handler ? <span className="api-handler">{handler.name}</span> : <span className="cell-hint">—</span>}
                  <div className="mono api-location">{node.filePath}:{node.startLine}</div>
                </td>
                <td className="api-summary">{handler?.summary || <span className="cell-hint">No AI summary</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
