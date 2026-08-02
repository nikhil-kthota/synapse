import './HeroBackground.css';

export default function HeroBackground() {
  return (
    <div className="hero-bg" aria-hidden="true">
      {/* Architectural Dot-Matrix Field */}
      <div className="hero-bg__grid" />
      <div className="hero-bg__crosshair hero-bg__crosshair--tl">+</div>
      <div className="hero-bg__crosshair hero-bg__crosshair--tr">+</div>
      <div className="hero-bg__crosshair hero-bg__crosshair--bl">+</div>
      <div className="hero-bg__crosshair hero-bg__crosshair--br">+</div>

      {/* Technical status overlays */}
      <div className="hero-bg__tech-tag hero-bg__tech-tag--tl">
        <span>[SYS.INTELLIGENCE: ON]</span>
        <span className="hero-bg__tech-sub">SYNAPSE_CORE // v2.4</span>
      </div>
      <div className="hero-bg__tech-tag hero-bg__tech-tag--tr">
        <span>[GRAPH.PARSE: READY]</span>
        <span className="hero-bg__tech-sub">NODES_MAPPED: 10,482</span>
      </div>

      {/* Organized Left-Flank Neural Synapse Architecture */}
      <svg
        className="hero-bg__diagram"
        viewBox="0 0 1400 800"
        preserveAspectRatio="xMidYMid slice"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <defs>
          <filter id="blue-pulse-glow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        {/* ========================================================
            LEFT FLANK ONLY — ORGANIZED KNOWLEDGE GRAPH TREE
            Scaled down and shifted leftwards for both themes
            ======================================================== */}
        <g transform="translate(-55, 60) scale(0.82)">
          <g>
            {/* Main Primary Backbone Track */}
            <path
              d="M 120 0 L 120 140 L 220 240 L 220 480 L 140 560 L 140 800"
              stroke="var(--schematic-line)"
              strokeWidth="1.8"
              strokeOpacity="var(--schematic-line-opacity)"
            />
            {/* Secondary Parallel Dependency Trunk */}
            <path
              d="M 120 140 L 120 360 L 220 360"
              stroke="var(--schematic-line)"
              strokeWidth="1.3"
              strokeDasharray="6 6"
              strokeOpacity="var(--schematic-line-opacity)"
            />
            {/* Tertiary Outer Bypass Branch */}
            <path
              d="M 220 240 L 280 300 L 280 420 L 220 480"
              stroke="var(--schematic-line)"
              strokeWidth="1.2"
              strokeOpacity="0.35"
            />

            {/* ----- Stage 1: Ingestion ----- */}
            <circle cx="120" cy="140" r="7" fill="var(--schematic-node-bg)" stroke="var(--schematic-line)" strokeWidth="2" />
            <circle cx="120" cy="140" r="2.5" fill="var(--schematic-line)" />
            <text x="138" y="138" fill="var(--schematic-text)" fontFamily="JetBrains Mono" fontSize="9.5" fontWeight="600" letterSpacing="0.08em">[01] MONOLITH_INGEST_STREAM</text>

            {/* ----- Stage 2: AST Syntax Parser (Glowing Active Node) ----- */}
            <circle cx="220" cy="240" r="11" fill="var(--schematic-node-bg)" stroke="var(--accent-blue)" strokeWidth="2.2" />
            <circle cx="220" cy="240" r="4.5" fill="var(--accent-blue)" filter="url(#blue-pulse-glow)" />
            <text x="238" y="238" fill="var(--accent-blue)" fontFamily="JetBrains Mono" fontSize="11" fontWeight="700" letterSpacing="0.05em">[02] AST_SYNTAX_PARSER</text>
            <text x="238" y="252" fill="var(--schematic-text)" fontFamily="JetBrains Mono" fontSize="8.5" letterSpacing="0.03em">:: RESOLVED (142 MODULES)</text>

            {/* ----- Stage 3: Dependency Tie-In ----- */}
            <circle cx="120" cy="360" r="6.5" fill="var(--schematic-node-bg)" stroke="var(--schematic-line)" strokeWidth="1.8" />
            <circle cx="120" cy="360" r="2.2" fill="var(--schematic-text)" />
            <text x="105" y="364" textAnchor="end" fill="var(--schematic-text)" fontFamily="JetBrains Mono" fontSize="9" fontWeight="600" letterSpacing="0.06em">DEPENDENCY_TREE</text>

            {/* ----- Stage 4: Vector Embedding Net (Glowing Active Node) ----- */}
            <circle cx="220" cy="480" r="11" fill="var(--schematic-node-bg)" stroke="var(--accent-blue)" strokeWidth="2.2" />
            <circle cx="220" cy="480" r="4.5" fill="var(--accent-blue)" filter="url(#blue-pulse-glow)" />
            <text x="238" y="478" fill="var(--accent-blue)" fontFamily="JetBrains Mono" fontSize="11" fontWeight="700" letterSpacing="0.05em">[03] VECTOR_EMBEDDING_NET</text>
            <text x="238" y="492" fill="var(--schematic-text)" fontFamily="JetBrains Mono" fontSize="8.5" letterSpacing="0.03em">[SEMANTIC_INDEX_ACTIVE]</text>

            {/* ----- Stage 5: Knowledge Graph Output ----- */}
            <circle cx="140" cy="560" r="8" fill="var(--schematic-node-bg)" stroke="var(--schematic-line)" strokeWidth="2" />
            <circle cx="140" cy="560" r="3" fill="var(--schematic-line)" />
            <text x="158" y="563" fill="var(--schematic-text)" fontFamily="JetBrains Mono" fontSize="9.5" fontWeight="600" letterSpacing="0.08em">[04] LIVING_KNOWLEDGE_GRAPH</text>
          </g>

          {/* ========================================================
              ANIMATED SIGNAL PULSES (LEFT SIDE ONLY)
              ======================================================== */}
          <circle r="4.5" fill="var(--accent-blue)" filter="url(#blue-pulse-glow)">
            <animateMotion dur="7s" repeatCount="indefinite" path="M 120 0 L 120 140 L 220 240 L 220 480 L 140 560 L 140 800" />
          </circle>
          <circle r="3.5" fill="var(--accent-blue)" opacity="0.95">
            <animateMotion dur="9s" repeatCount="indefinite" begin="2.5s" path="M 120 0 L 120 140 L 120 360 L 220 360 L 220 480 L 140 560" />
          </circle>
          <circle r="3.5" fill="var(--accent-blue)" opacity="0.9" filter="url(#blue-pulse-glow)">
            <animateMotion dur="8s" repeatCount="indefinite" begin="4s" path="M 220 240 L 280 300 L 280 420 L 220 480 L 140 560 L 140 800" />
          </circle>
        </g>
      </svg>
    </div>
  );
}
