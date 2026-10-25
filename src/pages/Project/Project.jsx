import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import Navbar from '../../components/Navbar/Navbar';
import Footer from '../../components/Footer/Footer';
import AppDecor from '../../components/AppDecor/AppDecor';
import { api, downloadBlob } from '../../api/client';
import { isRunning, graphReady, statusChip, PIPELINE } from '../../utils/projectStatus';
import Overview from './Overview';
import GraphExplorer from './GraphExplorer';
import DocsViewer from './DocsViewer';
import ApiEndpoints from './ApiEndpoints';
import ChatPanel from './ChatPanel';
import '../../styles/app.css';
import './Project.css';

const TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'graph', label: 'Knowledge Graph' },
  { key: 'docs', label: 'Documentation' },
  { key: 'api', label: 'API Endpoints' },
];

const pageVariants = {
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.4 } },
  exit: { opacity: 0, transition: { duration: 0.25 } },
};

export default function Project() {
  const { id } = useParams();
  const [project, setProject] = useState(null);
  const [error, setError] = useState('');
  const [tab, setTab] = useState('overview');
  const [graph, setGraph] = useState(null);
  const [graphError, setGraphError] = useState('');
  const [focusNodeId, setFocusNodeId] = useState(null);
  const [busy, setBusy] = useState(false);
  const [chatOpen, setChatOpen] = useState(false);
  const [selectedNode, setSelectedNode] = useState(null);
  const [activeDoc, setActiveDoc] = useState(null);

  const load = useCallback(() => {
    api.project(id).then(setProject).catch((err) => setError(err.message));
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const running = project && isRunning(project.status);
  useEffect(() => {
    if (!running) return undefined;
    const timer = setInterval(load, 2000);
    return () => clearInterval(timer);
  }, [running, load]);

  // Load the full graph once it has been saved (it is reused by several tabs).
  const ready = project && graphReady(project.status);
  useEffect(() => {
    if (!ready || graph) return;
    api.graph(id).then(setGraph).catch((err) => setGraphError(err.message));
  }, [ready, graph, id]);

  // Summaries arrive after the graph is first shown; refresh once the analysis completes.
  const completed = project?.status === 'COMPLETED';
  useEffect(() => {
    if (completed && graph) {
      api.graph(id).then(setGraph).catch(() => {});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [completed]);

  const openInGraph = useCallback((nodeId) => {
    setFocusNodeId(nodeId);
    setTab('graph');
  }, []);

  const rerun = async () => {
    setBusy(true);
    try {
      await api.reanalyze(id);
      setGraph(null);
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const exportDocs = async () => {
    setBusy(true);
    try {
      const { blob, fileName } = await api.exportDocs(id);
      downloadBlob(blob, fileName);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  // What the assistant should treat as "this": the selected graph node or the open document.
  const chatContext = useMemo(() => ({
    tab,
    selectedNode: tab === 'graph' ? selectedNode : null,
    document: tab === 'docs' ? activeDoc : null,
  }), [tab, selectedNode, activeDoc]);

  if (!project) {
    return (
      <div className="app-page">
        <Navbar variant="app" />
        <main className="app-container">
          {error
            ? <div className="bento"><div className="cell app-error">{error} <Link to="/app" className="auth-link">Back to dashboard</Link></div></div>
            : <div className="app-loading"><span className="app-spinner" /> Loading project…</div>}
        </main>
        <Footer />
      </div>
    );
  }

  const chip = statusChip(project.status);
  const chatTab = tab === 'graph' || tab === 'docs';
  const hasActions = (project.sourceType === 'GITHUB' && !running) || completed;
  const sourceLabel = project.sourceType === 'GITHUB'
    ? project.sourceUrl.replace(/^https:\/\//, '').replace(/\.git$/, '')
    : 'Uploaded ZIP archive';

  return (
    <motion.div className="app-page" variants={pageVariants} initial="initial" animate="animate" exit="exit">
      <Navbar variant="app" />
      <AppDecor sheet={`PRJ-${String(project.id).padStart(4, '0')}`} />
      <main className="app-container">
        <div className="proj-telemetry">
          <span className="sheet-head__index">[02] <span>// PROJECT · PRJ-{String(project.id).padStart(4, '0')}</span></span>
          <div className="sheet-head__tags">
            <span className={`tech-tag ${project.status === 'FAILED' ? 'tech-tag--err' : running ? 'tech-tag--warn' : 'tech-tag--ok'}`}>
              [<b>STATUS</b>: {project.status.replace('_', '.')}]
            </span>
            {project.stats?.totalNodes != null && <span className="tech-tag">[<b>NODES</b>: {project.stats.totalNodes.toLocaleString('en-US')}]</span>}
            {project.stats?.totalEdges != null && <span className="tech-tag">[<b>LINKS</b>: {project.stats.totalEdges.toLocaleString('en-US')}]</span>}
            {project.stats?.syntaxTreeParsedFiles != null && <span className="tech-tag">[<b>AST.PARSED</b>: {project.stats.syntaxTreeParsedFiles}/{project.stats.files}]</span>}
            {project.stats?.aiModel && <span className={`tech-tag ${project.aiAvailable ? 'tech-tag--ok' : 'tech-tag--warn'}`}>[<b>AI.DOCS</b>: {project.aiAvailable ? project.stats.aiModel : 'STATIC'}]</span>}
          </div>
        </div>
        <div className="bento cad proj-header">
          <Link to="/app" className="cell cell--tight proj-back">← All projects</Link>
          <div className={`cell proj-title-cell ${hasActions ? 'cell--8' : ''}`}>
            <div className="proj-title-row">
              <h1 className="app-heading proj-title">{project.name}</h1>
              <span className={`chip ${chip.className}`}>{chip.label}</span>
            </div>
            <div className="proj-meta">
              {project.sourceType === 'GITHUB'
                ? <a className="mono proj-source" href={project.sourceUrl.replace(/\.git$/, '')} target="_blank" rel="noreferrer">{sourceLabel}</a>
                : <span className="mono proj-source">{sourceLabel}</span>}
              {project.branch && <span className="chip">branch {project.branch}</span>}
              <span className="chip">{project.depth.toLowerCase()} depth</span>
              {project.aiAvailable === true && <span className="chip chip--ok">AI docs</span>}
              {project.aiAvailable === false && <span className="chip chip--warn">Static docs only</span>}
            </div>
          </div>
          {hasActions && <div className="cell cell--4 cell--tight proj-actions">
            {project.sourceType === 'GITHUB' && !running && (
              <button type="button" className="btn-line" onClick={rerun} disabled={busy}>Re-run analysis</button>
            )}
            {completed && (
              <button type="button" className="btn-line btn-line--blue" onClick={exportDocs} disabled={busy}>Export docs (.zip)</button>
            )}
          </div>}
          {error && <div className="cell app-error">{error}</div>}
        </div>

        {running && <Pipeline project={project} />}

        {project.status === 'FAILED' && (
          <div className="bento proj-section">
            <div className="cell">
              <span className="cell-label">Analysis failed</span>
              <p className="cell-text">{project.statusMessage}</p>
            </div>
          </div>
        )}

        {ready && (
          <>
            <div className="tabs tabs--wrap proj-tabs" role="tablist">
              {TABS.map((t) => (
                <button key={t.key} type="button" role="tab" aria-selected={tab === t.key}
                  className={`tab ${tab === t.key ? 'tab--active' : ''}`} onClick={() => setTab(t.key)}>
                  {t.label}
                </button>
              ))}
            </div>
            <div className="proj-tab-body">
              {graphError && <div className="bento"><div className="cell app-error">{graphError}</div></div>}
              {!graph && !graphError && <div className="app-loading"><span className="app-spinner" /> Loading knowledge graph…</div>}
              {graph && tab === 'overview' && <Overview project={project} graph={graph} onOpenNode={openInGraph} />}
              {graph && tab === 'graph' && (
                <GraphExplorer projectId={id} graph={graph} initialNodeId={focusNodeId} onNodeShown={() => setFocusNodeId(null)}
                  onSelectionChange={setSelectedNode} />
              )}
              {tab === 'docs' && <DocsViewer projectId={id} status={project.status} onExport={exportDocs} onActiveChange={setActiveDoc} />}
              {graph && tab === 'api' && <ApiEndpoints graph={graph} onOpenNode={openInGraph} />}
            </div>

            {chatTab && !chatOpen && (
              <button type="button" className="chat-launcher" onClick={() => setChatOpen(true)}>
                <span aria-hidden="true">✦</span> Ask Synapse AI
              </button>
            )}
            <ChatPanel
              projectId={id}
              projectName={project.name}
              open={chatOpen && chatTab}
              onClose={() => setChatOpen(false)}
              onOpenNode={openInGraph}
              context={chatContext}
            />
          </>
        )}
      </main>
      <Footer />
    </motion.div>
  );
}

function Pipeline({ project }) {
  const currentIndex = Math.max(0, PIPELINE.findIndex((s) => s.key === project.status));
  return (
    <div className="bento proj-section proj-pipeline">
      {PIPELINE.map((stage, i) => {
        const state = project.status === 'QUEUED' ? 'todo' : i < currentIndex ? 'done' : i === currentIndex ? 'active' : 'todo';
        return (
          <div key={stage.key} className={`cell proj-stage proj-stage--${state}`}>
            <span className="proj-stage__num">{state === 'done' ? '✓' : `0${i + 1}`}</span>
            <span className="proj-stage__label">{stage.label}</span>
          </div>
        );
      })}
      <div className="cell">
        <div className="progress"><div className="progress__fill" style={{ width: `${project.progress}%` }} /></div>
        <p className="cell-hint proj-pipeline__msg">
          <span className="app-spinner" /> {project.progress}% · {project.statusMessage}
          {project.status === 'GENERATING_DOCS' && ' — the knowledge graph below is already explorable.'}
        </p>
      </div>
    </div>
  );
}
