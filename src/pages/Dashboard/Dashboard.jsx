import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import Navbar from '../../components/Navbar/Navbar';
import Footer from '../../components/Footer/Footer';
import AppDecor, { SheetHeader } from '../../components/AppDecor/AppDecor';
import { api } from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { isRunning, statusChip, formatNumber, timeAgo } from '../../utils/projectStatus';
import '../../styles/app.css';
import './Dashboard.css';

const DEPTHS = [
  { value: 'quick', label: 'Quick — AI reads 10 key files' },
  { value: 'standard', label: 'Standard — AI reads 25 key files' },
  { value: 'deep', label: 'Deep — AI reads 60 key files' },
];

const pageVariants = {
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.4 } },
  exit: { opacity: 0, transition: { duration: 0.25 } },
};

export default function Dashboard() {
  const { user } = useAuth();
  const [projects, setProjects] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [aiStatus, setAiStatus] = useState(null);

  const load = useCallback(() => {
    api.projects()
      .then((list) => { setProjects(list); setLoadError(''); })
      .catch((err) => setLoadError(err.message));
  }, []);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    api.systemStatus().then((s) => setAiStatus(s.ai)).catch(() => setAiStatus(null));
  }, []);

  // Poll while any analysis is in progress.
  const anyRunning = projects?.some((p) => isRunning(p.status));
  useEffect(() => {
    if (!anyRunning) return undefined;
    const timer = setInterval(load, 2500);
    return () => clearInterval(timer);
  }, [anyRunning, load]);

  const onCreated = (project) => setProjects((list) => [project, ...(list || [])]);

  return (
    <motion.div className="app-page" variants={pageVariants} initial="initial" animate="animate" exit="exit">
      <Navbar variant="app" />
      <AppDecor sheet="WORKSPACE" />
      <main className="app-container">
        <SheetHeader
          index="01"
          label="WORKSPACE"
          title={`Hello, ${user.name.split(' ')[0]}.`}
          subtitle="Turn any repository into a searchable knowledge graph with AI documentation."
          tags={[
            { key: 'SYS.ENGINE', value: 'ONLINE', tone: 'ok' },
            aiStatus?.reachable && aiStatus?.modelInstalled
              ? { key: 'AI.MODEL', value: aiStatus.model, tone: 'ok' }
              : { key: 'AI.MODEL', value: aiStatus ? 'OFFLINE' : 'CHECKING', tone: aiStatus ? 'err' : 'warn' },
            { key: 'GRAPHS', value: projects ? projects.length : '…' },
            ...(anyRunning ? [{ key: 'PIPELINE', value: 'RUNNING', tone: 'warn' }] : []),
          ]}
          aside={<AiStatusChip status={aiStatus} />}
        />

        <NewAnalysis onCreated={onCreated} />

        <div className="app-section-head">
          <div>
            <span className="app-section-index">[02] // KNOWLEDGE_GRAPHS</span>
            <h2 className="app-heading dash-section-title">Your Knowledge Graphs</h2>
          </div>
          {projects && <span className="chip">{projects.length} project{projects.length === 1 ? '' : 's'}</span>}
        </div>

        {loadError && <div className="bento"><div className="cell app-error">{loadError}</div></div>}
        {!projects && !loadError && <div className="app-loading"><span className="app-spinner" /> Loading projects…</div>}
        {projects && projects.length === 0 && (
          <div className="bento">
            <div className="cell cell--center dash-empty">
              <div>
                <div className="cell-title">No projects yet</div>
                <p className="cell-text">Paste a GitHub URL or upload a ZIP above to build your first knowledge graph.</p>
              </div>
            </div>
          </div>
        )}
        {projects && projects.length > 0 && (
          <div className="dash-grid">
            {projects.map((p) => (
              <ProjectCard key={p.id} project={p} onChanged={load} />
            ))}
          </div>
        )}
      </main>
      <Footer />
    </motion.div>
  );
}

function AiStatusChip({ status }) {
  if (!status) return <span className="chip chip--warn">Checking AI…</span>;
  if (status.reachable && status.modelInstalled) {
    return <span className="chip chip--ok" title="Local model served by Ollama">● AI ready · {status.model}</span>;
  }
  if (status.reachable) {
    return <span className="chip chip--warn" title={`Run: ollama pull ${status.model}`}>AI model missing · ollama pull {status.model}</span>;
  }
  return <span className="chip chip--err" title="Start Ollama to enable AI documentation">AI offline · start Ollama</span>;
}

function NewAnalysis({ onCreated }) {
  const navigate = useNavigate();
  const [mode, setMode] = useState('github');
  const [form, setForm] = useState({ repoUrl: '', branch: '', token: '', depth: 'standard', name: '' });
  const [file, setFile] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const fileInput = useRef(null);

  const update = (e) => {
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));
    setError('');
  };

  const pickFile = (f) => {
    if (!f) return;
    if (!f.name.toLowerCase().endsWith('.zip')) { setError('Please choose a .zip file'); return; }
    setFile(f);
    setError('');
  };

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    if (mode === 'github' && !form.repoUrl.trim()) { setError('Enter a repository URL'); return; }
    if (mode === 'zip' && !file) { setError('Choose a ZIP file to upload'); return; }
    setSubmitting(true);
    try {
      const project = mode === 'github'
        ? await api.analyzeGitHub({
          repoUrl: form.repoUrl.trim(),
          branch: form.branch.trim() || null,
          token: form.token.trim() || null,
          depth: form.depth,
        })
        : await api.analyzeZip(file, form.name.trim(), form.depth);
      setForm((f) => ({ ...f, repoUrl: '', branch: '', token: '', name: '' }));
      setFile(null);
      onCreated(project);
      navigate(`/app/projects/${project.id}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="bento cad dash-new" onSubmit={submit} noValidate>
      <div className="cell cell--4 cell--muted dash-new__intro">
        <span className="cell-label">New analysis</span>
        <div className="cell-title">Analyze a repository</div>
        <p className="cell-text">
          Synapse parses every file, links classes, functions, imports and APIs into a graph, then a local AI model writes the documentation.
        </p>
        <div className="chip-row dash-new__specs">
          <span className="chip">40+ languages</span>
          <span className="chip">Runs locally</span>
          <span className="chip">Free</span>
        </div>
      </div>

      <div className="cell cell--8 dash-new__form">
        <div className="tabs dash-new__tabs" role="tablist">
          <button type="button" role="tab" aria-selected={mode === 'github'}
            className={`tab ${mode === 'github' ? 'tab--active' : ''}`} onClick={() => setMode('github')}>
            GitHub repository
          </button>
          <button type="button" role="tab" aria-selected={mode === 'zip'}
            className={`tab ${mode === 'zip' ? 'tab--active' : ''}`} onClick={() => setMode('zip')}>
            Upload ZIP
          </button>
        </div>

        {mode === 'github' ? (
          <div className="dash-fields">
            <label className="dash-field dash-field--full">
              <span className="cell-label">Repository URL</span>
              <input className="app-input" name="repoUrl" value={form.repoUrl} onChange={update}
                placeholder="https://github.com/owner/repository" autoComplete="off" spellCheck="false" />
            </label>
            <label className="dash-field">
              <span className="cell-label">Branch <em>(optional)</em></span>
              <input className="app-input" name="branch" value={form.branch} onChange={update} placeholder="default branch" />
            </label>
            <label className="dash-field">
              <span className="cell-label">Analysis depth</span>
              <select className="app-select" name="depth" value={form.depth} onChange={update}>
                {DEPTHS.map((d) => <option key={d.value} value={d.value}>{d.label}</option>)}
              </select>
            </label>
            <label className="dash-field dash-field--full">
              <span className="cell-label">Access token <em>(private repositories only)</em></span>
              <input className="app-input" name="token" type="password" value={form.token} onChange={update}
                placeholder="github_pat_…" autoComplete="off" />
              <span className="cell-hint">Used once to clone the repository, then discarded. It is never stored.</span>
            </label>
          </div>
        ) : (
          <div className="dash-fields">
            <div
              className={`dash-drop dash-field--full ${dragging ? 'dash-drop--over' : ''} ${file ? 'dash-drop--ready' : ''}`}
              onClick={() => fileInput.current?.click()}
              onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
              onDragLeave={() => setDragging(false)}
              onDrop={(e) => { e.preventDefault(); setDragging(false); pickFile(e.dataTransfer.files[0]); }}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') fileInput.current?.click(); }}
            >
              <input ref={fileInput} type="file" accept=".zip,application/zip" hidden onChange={(e) => pickFile(e.target.files[0])} />
              {file ? (
                <>
                  <strong>{file.name}</strong>
                  <span>{(file.size / (1024 * 1024)).toFixed(1)} MB · click to choose a different file</span>
                </>
              ) : (
                <>
                  <strong>Drop a .zip of the source code here</strong>
                  <span>or click to browse · up to 200 MB</span>
                </>
              )}
            </div>
            <label className="dash-field">
              <span className="cell-label">Project name <em>(optional)</em></span>
              <input className="app-input" name="name" value={form.name} onChange={update} placeholder="from file name" />
            </label>
            <label className="dash-field">
              <span className="cell-label">Analysis depth</span>
              <select className="app-select" name="depth" value={form.depth} onChange={update}>
                {DEPTHS.map((d) => <option key={d.value} value={d.value}>{d.label}</option>)}
              </select>
            </label>
          </div>
        )}
      </div>

      {error && <div className="cell app-error" role="alert">{error}</div>}
      <div className="cell dash-new__submit" style={{ padding: 0 }}>
        <button type="submit" className="btn-cell" disabled={submitting}>
          {submitting ? <><span className="app-spinner" /> Starting analysis…</> : 'Analyze Repository →'}
        </button>
      </div>
    </form>
  );
}

function ProjectCard({ project, onChanged }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const running = isRunning(project.status);
  const chip = statusChip(project.status);
  const stats = project.stats || {};
  const languages = Object.entries(stats.languages || {})
    .sort((a, b) => b[1].lines - a[1].lines)
    .slice(0, 3);

  const rerun = async () => {
    setBusy(true);
    setError('');
    try { await api.reanalyze(project.id); onChanged(); } catch (err) { setError(err.message); } finally { setBusy(false); }
  };

  const remove = async () => {
    if (!window.confirm(`Delete "${project.name}" and its knowledge graph? This cannot be undone.`)) return;
    setBusy(true);
    try { await api.deleteProject(project.id); onChanged(); } catch (err) { setError(err.message); setBusy(false); }
  };

  return (
    <article className="bento project-card">
      <div className="cell project-card__head">
        <div className="project-card__title-row">
          <Link to={`/app/projects/${project.id}`} className="cell-title project-card__title">{project.name}</Link>
          <span className={`chip ${chip.className}`}>{chip.label}</span>
        </div>
        <div className="project-card__id mono">PRJ-{String(project.id).padStart(4, '0')} // {project.sourceType}</div>
        <div className="project-card__source mono">
          {project.sourceType === 'GITHUB'
            ? project.sourceUrl.replace(/^https:\/\//, '').replace(/\.git$/, '') + (project.branch ? ` @ ${project.branch}` : '')
            : 'Uploaded ZIP archive'}
        </div>
      </div>

      {running ? (
        <div className="cell project-card__body">
          <div className="progress"><div className="progress__fill" style={{ width: `${project.progress}%` }} /></div>
          <p className="cell-hint">{project.progress}% · {project.statusMessage}</p>
        </div>
      ) : project.status === 'FAILED' ? (
        <div className="cell project-card__body app-error">{project.statusMessage}</div>
      ) : (
        <div className="cell project-card__body">
          <div className="project-card__stats">
            <Stat label="Files" value={formatNumber(stats.files)} />
            <Stat label="Lines" value={formatNumber(stats.linesOfCode)} />
            <Stat label="Nodes" value={formatNumber(stats.totalNodes)} />
            <Stat label="Links" value={formatNumber(stats.totalEdges)} />
          </div>
          <div className="chip-row">
            {languages.map(([name]) => <span key={name} className="chip">{name}</span>)}
            {project.aiAvailable === false && <span className="chip chip--warn">No AI docs</span>}
          </div>
        </div>
      )}

      {error && <div className="cell app-error">{error}</div>}
      <div className="cell cell--tight project-card__actions">
        <span className="cell-hint">{timeAgo(project.createdAt)}</span>
        <div className="project-card__buttons">
          {project.sourceType === 'GITHUB' && !running && (
            <button type="button" className="btn-line" onClick={rerun} disabled={busy}>Re-run</button>
          )}
          {!running && (
            <button type="button" className="btn-line btn-line--danger" onClick={remove} disabled={busy}>Delete</button>
          )}
          <Link to={`/app/projects/${project.id}`} className="btn-line btn-line--blue">Open →</Link>
        </div>
      </div>
    </article>
  );
}

function Stat({ label, value }) {
  return (
    <div className="project-card__stat">
      <span className="project-card__stat-value">{value}</span>
      <span className="project-card__stat-label">{label}</span>
    </div>
  );
}
