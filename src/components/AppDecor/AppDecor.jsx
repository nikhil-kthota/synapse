import './AppDecor.css';

/** Blueprint-sheet framing for app pages: corner crosshairs and vertical telemetry rails, like the hero. */
export default function AppDecor({ sheet = 'WORKSPACE' }) {
  return (
    <div className="app-decor" aria-hidden="true">
      <span className="app-decor__cross app-decor__cross--tl">+</span>
      <span className="app-decor__cross app-decor__cross--tr">+</span>
      <span className="app-decor__cross app-decor__cross--bl">+</span>
      <span className="app-decor__cross app-decor__cross--br">+</span>
      <span className="app-decor__rail app-decor__rail--left">SYNAPSE_CORE // KNOWLEDGE_ENGINE // SHEET: {sheet}</span>
      <span className="app-decor__rail app-decor__rail--right">GRID 22PX // LOCAL_INFERENCE // ZERO_COST_STACK</span>
    </div>
  );
}

/** Indexed page heading with monospace status tags, e.g. [01] // WORKSPACE + [SYS.ENGINE: ONLINE]. */
export function SheetHeader({ index, label, title, subtitle, tags = [], aside }) {
  return (
    <header className="sheet-head">
      <div className="sheet-head__main">
        <span className="sheet-head__index">[{index}] <span>// {label}</span></span>
        <h1 className="app-heading">{title}</h1>
        {subtitle && <p className="app-subheading">{subtitle}</p>}
        {tags.length > 0 && (
          <div className="sheet-head__tags">
            {tags.map((t) => (
              <span key={t.key} className={`tech-tag ${t.tone ? `tech-tag--${t.tone}` : ''}`}>
                [<b>{t.key}</b>: {t.value}]
              </span>
            ))}
          </div>
        )}
      </div>
      {aside && <div className="sheet-head__aside">{aside}</div>}
    </header>
  );
}
