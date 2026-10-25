import { useEffect, useId, useMemo, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import './Markdown.css';

let mermaidPromise;
function loadMermaid() {
  if (!mermaidPromise) {
    mermaidPromise = import('mermaid').then(({ default: mermaid }) => {
      mermaid.initialize({
        startOnLoad: false,
        securityLevel: 'strict',
        theme: 'base',
        themeVariables: {
          fontFamily: 'ClashDisplay, Inter, system-ui, sans-serif',
          primaryColor: '#C4C8D0',
          primaryBorderColor: '#000000',
          primaryTextColor: '#000000',
          lineColor: '#2B7FD9',
          edgeLabelBackground: '#C4C8D0',
        },
      });
      return mermaid;
    });
  }
  return mermaidPromise;
}

function MermaidBlock({ code }) {
  const id = useId().replace(/:/g, '');
  const [svg, setSvg] = useState('');
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    loadMermaid()
      .then((mermaid) => mermaid.render(`mmd-${id}`, code))
      .then(({ svg: out }) => { if (!cancelled) setSvg(out); })
      .catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, [code, id]);

  if (failed) return <pre className="md-code"><code>{code}</code></pre>;
  if (!svg) return <div className="md-diagram md-diagram--loading">Rendering diagram…</div>;
  // Mermaid output is generated locally with securityLevel 'strict' (no scripts or HTML labels).
  return <div className="md-diagram" dangerouslySetInnerHTML={{ __html: svg }} />;
}

function buildComponents(onNodeLink) {
  return {
    code({ className, children, ...props }) {
      const language = /language-(\w+)/.exec(className || '')?.[1];
      const text = String(children).replace(/\n$/, '');
      if (language === 'mermaid') return <MermaidBlock code={text} />;
      if (!className && !text.includes('\n')) return <code className="md-inline" {...props}>{children}</code>;
      return <code className={className} {...props}>{children}</code>;
    },
    pre({ children }) {
      // Mermaid blocks render their own container.
      const child = Array.isArray(children) ? children[0] : children;
      if (child?.props?.className?.includes('language-mermaid')) return <>{children}</>;
      return <pre className="md-code">{children}</pre>;
    },
    a({ href, children }) {
      const nodeMatch = href && /^#node-(\d+)$/.exec(href);
      if (nodeMatch && onNodeLink) {
        return (
          <button type="button" className="md-node-link" onClick={() => onNodeLink(Number(nodeMatch[1]))} title="Show in knowledge graph">
            {children}
          </button>
        );
      }
      const external = href && /^https?:/.test(href);
      return <a href={href} target={external ? '_blank' : undefined} rel={external ? 'noreferrer' : undefined}>{children}</a>;
    },
    table({ children }) {
      return <div className="md-table-wrap"><table>{children}</table></div>;
    },
  };
}

export default function Markdown({ children, onNodeLink }) {
  const components = useMemo(() => buildComponents(onNodeLink), [onNodeLink]);
  return (
    <div className="md">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>{children}</ReactMarkdown>
    </div>
  );
}
