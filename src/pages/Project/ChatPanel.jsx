import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { api } from '../../api/client';
import Markdown from '../../components/Markdown/Markdown';
import { KIND_META } from './graphMeta';
import './ChatPanel.css';

const HISTORY_SENT = 8;

function storageKey(projectId) {
  return `synapse-chat-${projectId}`;
}

function loadHistory(projectId) {
  try {
    return JSON.parse(sessionStorage.getItem(storageKey(projectId))) || [];
  } catch {
    return [];
  }
}

/**
 * Turns the model's [[ID|Name]] citations into graph links, and also links `Name` code spans that
 * match a retrieved source (the local model does not always use the citation format).
 */
function linkify(text, sources) {
  const byName = new Map();
  for (const s of sources || []) {
    if (!byName.has(s.name)) byName.set(s.name, s.id);
  }
  return text.split(/(```[\s\S]*?(?:```|$))/g).map((part, i) => {
    if (i % 2 === 1) return part; // fenced code stays untouched
    return part
      .replace(/\[\[(\d+)\|([^\]]+)\]\]/g, (_, id, name) => `[\`${name}\`](#node-${id})`)
      .replace(/\[\[(\d+)\]\]/g, (_, id) => `[#${id}](#node-${id})`)
      .replace(/(?<!\[)`([A-Za-z_$][\w$.]*?)(?:\(\))?`(?!\]\()/g, (match, name) => {
        const bare = name.split('.').pop();
        const id = byName.get(name) ?? byName.get(bare);
        return id ? `[${match}](#node-${id})` : match;
      });
  }).join('');
}

function shortPath(path) {
  if (!path) return '';
  const parts = path.split('/');
  return parts.length > 2 ? `…/${parts.slice(-2).join('/')}` : path;
}

/** Exact locations from the parsed code, listed in full so nothing depends on the model copying them. */
function CallSites({ groups, onOpenNode }) {
  return (
    <div className="chat-callsites">
      <div className="chat-callsites__title">[CALL.SITES] exact locations from the parsed code</div>
      {groups.map((g) => (
        <div key={g.targetId} className="chat-callsites__group">
          <button type="button" className="chat-callsites__target" onClick={() => onOpenNode(g.targetId)} title={g.definedAt}>
            <span className="chip-dot" style={{ background: KIND_META[g.targetKind]?.color }} />
            {g.targetName}
            <span className="chat-callsites__def">defined {shortPath(g.definedAt)}</span>
          </button>
          {g.sites.length === 0 ? (
            <p className="chat-callsites__none">No calls in this repository — likely invoked by the framework or external code.</p>
          ) : (
            <ul>
              {g.sites.map((site) => (
                <li key={`${site.callerId}-${site.relation}`}>
                  <button type="button" onClick={() => onOpenNode(site.callerId)} title={site.filePath}>
                    <span className="chat-callsites__loc">
                      {shortPath(site.filePath)}{site.lines.length > 0 ? `:${site.lines.join(', ')}` : ''}
                    </span>
                    <span className="chat-callsites__caller">{site.relation} <b>{site.callerName}</b></span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      ))}
    </div>
  );
}

export default function ChatPanel({ projectId, projectName, open, onClose, context, onOpenNode }) {
  const [messages, setMessages] = useState(() => loadHistory(projectId));
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const abortRef = useRef(null);
  const listRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    try {
      sessionStorage.setItem(storageKey(projectId), JSON.stringify(messages.filter((m) => !m.pending).slice(-30)));
    } catch { /* storage unavailable */ }
  }, [messages, projectId]);

  useEffect(() => {
    if (open) setTimeout(() => inputRef.current?.focus(), 250);
  }, [open]);

  // Keep the newest text in view while an answer streams in.
  useEffect(() => {
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages, open]);

  useEffect(() => () => abortRef.current?.abort(), []);

  const suggestions = useMemo(() => {
    if (context.selectedNode) {
      const name = context.selectedNode.name;
      return [`What does ${name} do?`, `What would break if I change ${name}?`, `Where is ${name} used?`];
    }
    if (context.tab === 'docs' && context.document) {
      return [`Summarise ${context.document.fileName}`, 'What are the key workflows?', 'Which business rules are enforced?'];
    }
    return ['Give me a tour of the architecture', 'Where is the main business logic?', 'Which API endpoints exist and what do they do?'];
  }, [context]);

  const ask = useCallback(async (raw) => {
    const question = raw.trim();
    if (!question || busy) return;
    const history = messages.filter((m) => !m.error).slice(-HISTORY_SENT).map((m) => ({ role: m.role, content: m.content }));
    const assistant = { role: 'assistant', content: '', sources: [], pending: true, status: 'Searching the knowledge graph…' };
    setMessages((list) => [...list, { role: 'user', content: question }, assistant]);
    setInput('');
    setBusy(true);

    const update = (patch) => setMessages((list) => {
      const next = [...list];
      const last = next[next.length - 1];
      next[next.length - 1] = { ...last, ...(typeof patch === 'function' ? patch(last) : patch) };
      return next;
    });

    const controller = new AbortController();
    abortRef.current = controller;
    try {
      await api.chat(projectId, {
        message: question,
        history,
        context: {
          tab: context.tab,
          selectedNodeId: context.selectedNode?.id ?? null,
          documentKind: context.tab === 'docs' ? context.document?.kind ?? null : null,
        },
      }, (event) => {
        if (event.type === 'sources') update({ sources: event.sources, status: 'Thinking…' });
        else if (event.type === 'callsites') update({ callSites: event.groups });
        else if (event.type === 'waiting') update({ status: event.message });
        else if (event.type === 'token') update((m) => ({ content: m.content + event.text, status: null }));
        else if (event.type === 'error') update({ error: true, content: event.message, status: null });
      }, controller.signal);
    } catch (err) {
      if (err.name === 'AbortError') update((m) => ({ content: m.content || '_Stopped._' }));
      else update({ error: true, content: err.message });
    } finally {
      update({ pending: false, status: null });
      setBusy(false);
      abortRef.current = null;
    }
  }, [busy, messages, projectId, context]);

  const submit = (e) => {
    e.preventDefault();
    ask(input);
  };

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      ask(input);
    }
  };

  const clear = () => {
    abortRef.current?.abort();
    setMessages([]);
  };

  const contextLabel = context.selectedNode
    ? `${KIND_META[context.selectedNode.kind]?.label || 'Node'}: ${context.selectedNode.name}`
    : context.tab === 'docs' && context.document
      ? context.document.fileName
      : 'Whole project';

  return (
    <AnimatePresence>
      {open && (
        <motion.aside
          className="chat"
          role="dialog"
          aria-label="Ask Synapse about this project"
          initial={{ opacity: 0, x: 40 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: 40 }}
          transition={{ duration: 0.25, ease: [0.25, 0.46, 0.45, 0.94] }}
        >
          <div className="chat-chrome">
            <div className="gx-dots"><span style={{ background: '#FF5F57' }} /><span style={{ background: '#FFBD2E' }} /><span style={{ background: '#28CA41' }} /></div>
            <div className="chat-title">Ask Synapse</div>
            {messages.length > 0 && <button type="button" className="chat-icon-btn" onClick={clear} title="Start a new conversation">New</button>}
            <button type="button" className="chat-icon-btn chat-close" onClick={onClose} aria-label="Close assistant">×</button>
          </div>

          <div className="chat-context">
            <span className="chat-context-label">Context</span>
            <span className="chip">{contextLabel}</span>
          </div>

          <div className="chat-messages" ref={listRef} data-lenis-prevent>
            {messages.length === 0 && (
              <div className="chat-empty">
                <div className="chat-empty-title">Ask anything about {projectName}</div>
                <p>
                  Answers come from this project&apos;s knowledge graph, source code and generated documentation,
                  written by your local AI model. Cited elements open in the graph.
                </p>
                <div className="chat-suggestions">
                  {suggestions.map((s) => (
                    <button key={s} type="button" className="chat-suggestion" onClick={() => ask(s)}>{s}</button>
                  ))}
                </div>
              </div>
            )}

            {messages.map((m, i) => (
              m.role === 'user' ? (
                <div key={i} className="chat-msg chat-msg--user">{m.content}</div>
              ) : (
                <div key={i} className={`chat-msg chat-msg--ai ${m.error ? 'chat-msg--error' : ''}`}>
                  {m.status && <div className="chat-status"><span className="app-spinner" /> {m.status}</div>}
                  {m.content && (m.error ? <p>{m.content}</p> : <Markdown onNodeLink={onOpenNode}>{linkify(m.content, m.sources)}</Markdown>)}
                  {m.pending && m.content && <span className="chat-caret" aria-hidden="true" />}
                  {m.callSites?.length > 0 && <CallSites groups={m.callSites} onOpenNode={onOpenNode} />}
                  {!m.pending && m.sources?.length > 0 && (
                    <details className="chat-sources">
                      <summary>{m.sources.length} sources from the knowledge graph</summary>
                      <div className="chat-source-list">
                        {m.sources.map((s) => (
                          <button key={s.id} type="button" className="chat-source" onClick={() => onOpenNode(s.id)} title={s.filePath || s.name}>
                            <span className="chip-dot" style={{ background: KIND_META[s.kind]?.color }} />
                            {s.name}
                          </button>
                        ))}
                      </div>
                    </details>
                  )}
                </div>
              )
            ))}
          </div>

          <form className="chat-input" onSubmit={submit}>
            <textarea
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={onKeyDown}
              placeholder={context.selectedNode ? `Ask about ${context.selectedNode.name}…` : 'Ask about this codebase…'}
              rows={2}
              maxLength={2000}
              aria-label="Your question"
            />
            {busy ? (
              <button type="button" className="chat-send chat-send--stop" onClick={() => abortRef.current?.abort()}>Stop</button>
            ) : (
              <button type="submit" className="chat-send" disabled={!input.trim()}>Ask →</button>
            )}
          </form>
        </motion.aside>
      )}
    </AnimatePresence>
  );
}
