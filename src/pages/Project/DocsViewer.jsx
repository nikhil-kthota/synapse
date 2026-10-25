import { useEffect, useState } from 'react';
import { api, downloadBlob } from '../../api/client';
import Markdown from '../../components/Markdown/Markdown';
import './DocsViewer.css';

const DESCRIPTIONS = {
  README: 'Overview, setup and key components',
  ARCHITECTURE: 'Modules, dependencies and data flow',
  BUSINESS_LOGIC: 'Domain entities, workflows and rules',
  API: 'Every detected HTTP endpoint',
};

export default function DocsViewer({ projectId, status, onExport, onActiveChange }) {
  const [docs, setDocs] = useState(null);
  const [active, setActive] = useState(null);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);
  const writing = status === 'GENERATING_DOCS';

  useEffect(() => {
    api.documents(projectId)
      .then((list) => {
        setDocs(list);
        setActive((current) => current || list[0]?.kind || null);
      })
      .catch((err) => setError(err.message));
  }, [projectId, status]);

  const current = docs?.find((d) => d.kind === active) || docs?.[0];
  useEffect(() => {
    onActiveChange?.(current ? { kind: current.kind, fileName: current.fileName } : null);
  }, [current?.kind, current?.fileName, onActiveChange]); // eslint-disable-line react-hooks/exhaustive-deps

  if (error) return <div className="bento"><div className="cell app-error">{error}</div></div>;
  if (!docs) return <div className="app-loading"><span className="app-spinner" /> Loading documentation…</div>;
  if (docs.length === 0) {
    return (
      <div className="bento">
        <div className="cell cell--center docs-empty">
          {writing
            ? <p className="cell-text"><span className="app-spinner" /> The AI is writing the documentation. It appears here when ready.</p>
            : <p className="cell-text">No documentation was generated for this project.</p>}
        </div>
      </div>
    );
  }

  const doc = docs.find((d) => d.kind === active) || docs[0];

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(doc.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch { /* clipboard blocked */ }
  };

  const download = () => downloadBlob(new Blob([doc.content], { type: 'text/markdown' }), doc.fileName);

  return (
    <div className="docs">
      <nav className="bento docs-nav" aria-label="Documents">
        {docs.map((d) => (
          <button key={d.kind} type="button" className={`cell docs-nav__item ${d.kind === doc.kind ? 'docs-nav__item--active' : ''}`}
            onClick={() => setActive(d.kind)}>
            <span className="docs-nav__file mono">{d.fileName}</span>
            <span className="docs-nav__title">{d.title}</span>
            <span className="docs-nav__desc">{DESCRIPTIONS[d.kind]}</span>
            <span className={`chip ${d.aiGenerated ? 'chip--ok' : ''}`}>{d.aiGenerated ? 'AI written' : 'From analysis'}</span>
          </button>
        ))}
        <div className="cell docs-nav__export" style={{ padding: 0 }}>
          <button type="button" className="btn-cell" onClick={onExport} disabled={writing}>Export all (.zip)</button>
        </div>
      </nav>

      <article className="bento docs-body">
        <div className="cell cell--tight docs-toolbar">
          <span className="mono">{doc.fileName}</span>
          <div className="docs-toolbar__buttons">
            <button type="button" className="btn-line" onClick={copy}>{copied ? 'Copied ✓' : 'Copy Markdown'}</button>
            <button type="button" className="btn-line" onClick={download}>Download .md</button>
          </div>
        </div>
        <div className="cell docs-content">
          <Markdown>{doc.content}</Markdown>
        </div>
      </article>
    </div>
  );
}
