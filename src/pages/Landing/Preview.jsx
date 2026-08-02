import { motion } from 'framer-motion';
import { Link } from 'react-router-dom';
import WaveformCanvas from '../../components/WaveformCanvas/WaveformCanvas';
import './Preview.css';

export default function Preview() {
  return (
    <>
      {/* Waveform divider */}
      <div className="waveform-divider">
        <WaveformCanvas opacity={1} fullIntensity={true} height="180px" />
      </div>

      {/* Preview section */}
      <section id="preview" className="preview">
        <div className="container">
          <motion.div
            className="preview__header"
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: '-80px' }}
            transition={{ duration: 0.65 }}
          >
            <span className="section-label">Preview</span>
            <h2 className="preview__title">
              See your entire codebase <br />
              <span className="gradient-text">at a glance.</span>
            </h2>
            <p className="preview__subtitle">
              The interactive knowledge graph viewer lets you explore dependencies,
              search components, and navigate your codebase like never before.
            </p>
          </motion.div>

          {/* Mock graph viewer UI matching 980px width */}
          <motion.div
            className="preview__mockup"
            initial={{ opacity: 0, y: 60, scale: 0.98 }}
            whileInView={{ opacity: 1, y: 0, scale: 1 }}
            viewport={{ once: true, margin: '-80px' }}
            transition={{ duration: 0.8, ease: [0.25, 0.46, 0.45, 0.94] }}
          >
            <div className="mockup-window">
              {/* Window chrome */}
              <div className="mockup-chrome">
                <div className="mockup-dots">
                  <span style={{ background: '#FF5F57' }} />
                  <span style={{ background: '#FFBD2E' }} />
                  <span style={{ background: '#28CA41' }} />
                </div>
                <div className="mockup-title">Synapse — Knowledge Graph Viewer</div>
                <div className="mockup-chrome-right">
                  <div className="mockup-chip">TypeScript</div>
                  <div className="mockup-chip">Node.js</div>
                  <div className="mockup-chip mockup-chip--blue">142 nodes</div>
                </div>
              </div>

              {/* Graph area */}
              <div className="mockup-body">
                {/* Sidebar */}
                <div className="mockup-sidebar">
                  <div className="mockup-search">
                    <span>⌕</span>
                    <span className="mockup-search-placeholder">Search components...</span>
                  </div>
                  {[
                    { name: 'AuthService', type: 'class', active: true },
                    { name: 'UserController', type: 'class', active: false },
                    { name: 'DatabasePool', type: 'service', active: false },
                    { name: 'EmailAdapter', type: 'adapter', active: false },
                    { name: 'JWTMiddleware', type: 'middleware', active: false },
                    { name: 'UserRepository', type: 'repository', active: false },
                  ].map((item, i) => (
                    <div key={i} className={`mockup-sidebar-item ${item.active ? 'active' : ''}`}>
                      <span className={`mockup-type-badge mockup-type-badge--${item.type}`}>
                        {item.type[0].toUpperCase()}
                      </span>
                      <span className="mockup-item-name">{item.name}</span>
                    </div>
                  ))}
                </div>

                {/* Graph canvas area */}
                <div className="mockup-graph">
                  <svg viewBox="0 0 540 340" fill="none" className="mockup-svg">
                    {/* Lines */}
                    <line x1="270" y1="170" x2="160" y2="90" stroke="var(--accent-blue)" strokeWidth="1.8" strokeOpacity="0.7" />
                    <line x1="270" y1="170" x2="380" y2="90" stroke="var(--accent-blue)" strokeWidth="1.8" strokeOpacity="0.7" />
                    <line x1="270" y1="170" x2="160" y2="255" stroke="var(--accent-blue)" strokeWidth="1.5" strokeOpacity="0.5" />
                    <line x1="270" y1="170" x2="380" y2="255" stroke="var(--accent-blue)" strokeWidth="1.5" strokeOpacity="0.5" />
                    <line x1="160" y1="90" x2="70" y2="170" stroke="#000000" strokeWidth="1.2" strokeOpacity="0.4" />
                    <line x1="380" y1="90" x2="470" y2="170" stroke="#000000" strokeWidth="1.2" strokeOpacity="0.4" />
                    <line x1="160" y1="90" x2="380" y2="90" stroke="var(--accent-blue)" strokeWidth="1" strokeOpacity="0.3" strokeDasharray="5 4" />
                    <line x1="160" y1="255" x2="380" y2="255" stroke="var(--accent-blue)" strokeWidth="1" strokeOpacity="0.3" strokeDasharray="5 4" />

                    {/* Main node (selected) */}
                    <circle cx="270" cy="170" r="28" fill="var(--bg-surface)" stroke="var(--accent-blue)" strokeWidth="2.5" />
                    <circle cx="270" cy="170" r="20" fill="var(--accent-blue)" fillOpacity="0.2" />
                    <circle cx="270" cy="170" r="38" stroke="var(--accent-blue)" strokeWidth="1.2" strokeOpacity="0.3" strokeDasharray="6 4" />
                    <text x="270" y="166" textAnchor="middle" fontSize="7.5" fill="var(--accent-blue)" fontFamily="sans-serif" fontWeight="700">Auth</text>
                    <text x="270" y="177" textAnchor="middle" fontSize="6.5" fill="var(--text-primary)" fontFamily="sans-serif" fontWeight="600">Service</text>

                    {/* Secondary nodes with high-contrast text */}
                    {[
                      { cx: 160, cy: 90, r: 18, color: 'var(--accent-blue)', label: 'User', sub: 'Controller' },
                      { cx: 380, cy: 90, r: 18, color: 'var(--accent-blue)', label: 'JWT', sub: 'Middleware' },
                      { cx: 160, cy: 255, r: 14, color: 'var(--accent-blue)', label: 'DB', sub: 'Pool' },
                      { cx: 380, cy: 255, r: 14, color: 'var(--accent-blue)', label: 'Email', sub: 'Adapter' },
                      { cx: 70, cy: 170, r: 11, color: '#000000', label: 'Logger', sub: '' },
                      { cx: 470, cy: 170, r: 11, color: '#000000', label: 'Cache', sub: '' },
                    ].map((n, i) => (
                      <g key={i}>
                        <circle cx={n.cx} cy={n.cy} r={n.r} fill="var(--bg-surface)" stroke={n.color} strokeWidth="1.8" />
                        <circle cx={n.cx} cy={n.cy} r={n.r - 5} fill={n.color} fillOpacity="0.15" />
                        <text x={n.cx} y={n.cy + (n.sub ? -1 : 3)} textAnchor="middle" fontSize="7" fill="var(--text-primary)" fontFamily="sans-serif" fontWeight="700">{n.label}</text>
                        {n.sub && <text x={n.cx} y={n.cy + 9} textAnchor="middle" fontSize="5.8" fill="var(--text-secondary)" fontFamily="sans-serif" fontWeight="600">{n.sub}</text>}
                      </g>
                    ))}

                    {/* Signal dot traveling */}
                    <circle r="4" fill="var(--accent-blue)" opacity="0.9" filter="url(#glow)">
                      <animateMotion dur="2.4s" repeatCount="indefinite" path="M270,170 L160,90" />
                    </circle>
                    <defs>
                      <filter id="glow">
                        <feGaussianBlur stdDeviation="3" result="coloredBlur" />
                        <feMerge><feMergeNode in="coloredBlur" /><feMergeNode in="SourceGraphic" /></feMerge>
                      </filter>
                    </defs>
                  </svg>

                  {/* Info panel */}
                  <div className="mockup-info-panel">
                    <div className="mockup-info-tag">CLASS</div>
                    <div className="mockup-info-name">AuthService</div>
                    <div className="mockup-info-desc">Handles user authentication, token generation and session management via JWT.</div>
                    <div className="mockup-info-deps">
                      <span className="mockup-dep">→ UserRepository</span>
                      <span className="mockup-dep">→ JWTMiddleware</span>
                      <span className="mockup-dep">→ EmailAdapter</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </motion.div>

          {/* Clean CTA button without credit card text */}
          <motion.div
            className="preview__cta"
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.55, delay: 0.3 }}
          >
            <Link to="/signup" className="btn btn-primary">
              Get Started →
            </Link>
          </motion.div>
        </div>
      </section>
    </>
  );
}
