import { motion } from 'framer-motion';
import './HowItWorks.css';

const steps = [
  {
    label: 'STEP 1',
    title: 'INPUT',
    desc: 'Paste a GitHub repository URL or upload a ZIP archive of your codebase. Synapse securely ingests the source in seconds without requiring custom build environments or complex setup scripts.',
    icon: (
      <svg viewBox="0 0 48 48" fill="none" width="32" height="32">
        <rect x="4" y="8" width="40" height="32" rx="6" stroke="currentColor" strokeWidth="2.2" />
        <path d="M16 20h16M16 28h10" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
        <circle cx="36" cy="36" r="8" fill="var(--bg-surface)" stroke="currentColor" strokeWidth="2.2" />
        <path d="M33 36l2 2 4-4" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    ),
  },
  {
    label: 'STEP 2',
    title: 'ANALYZE',
    desc: 'Our AI engine maps every class, method, function, and structural dependency into a structured knowledge graph — identifying cross-module relationships and logic hierarchies in real time.',
    icon: (
      <svg viewBox="0 0 48 48" fill="none" width="32" height="32">
        <circle cx="24" cy="24" r="7" stroke="currentColor" strokeWidth="2.2" />
        <circle cx="8" cy="12" r="4" stroke="currentColor" strokeWidth="2.2" />
        <circle cx="40" cy="12" r="4" stroke="currentColor" strokeWidth="2.2" />
        <circle cx="8" cy="36" r="4" stroke="currentColor" strokeWidth="2.2" />
        <circle cx="40" cy="36" r="4" stroke="currentColor" strokeWidth="2.2" />
        <path d="M12 14l8 7M28 27l8 7M36 14l-8 7M20 27l-8 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    label: 'STEP 3',
    title: 'EXPLORE',
    desc: 'Navigate the interactive living knowledge graph across your entire system architecture. Dive deep into module dependencies, inspect internal data pathways, and instantly export comprehensive AI-generated READMEs, API specifications, and clear architectural reports.',
    icon: (
      <svg viewBox="0 0 48 48" fill="none" width="34" height="34">
        <rect x="6" y="6" width="22" height="28" rx="3" stroke="currentColor" strokeWidth="2.2" />
        <path d="M12 16h10M12 22h10M12 28h6" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
        <path d="M24 30c0-7.732 6.268-14 14-14" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
        <circle cx="38" cy="38" r="6" stroke="currentColor" strokeWidth="2.2" />
        <path d="M36 38l1.5 1.5L41 35" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    ),
  },
];

export default function HowItWorks() {
  return (
    <section id="how-it-works" className="how-it-works">
      <div className="container">
        <motion.div
          className="how-it-works__header"
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: '-80px' }}
          transition={{ duration: 0.65 }}
        >
          <span className="section-label">Process</span>
          <h2 className="how-it-works__title">
            Steps to complete<br />code clarity.
          </h2>
          <p className="how-it-works__subtitle">
            No configuration. No setup scripts. Just drop in your project and
            let Synapse generate your architecture knowledge graph.
          </p>
        </motion.div>

        {/* Architectural 3-step grid matching hero content width (980px) */}
        <div className="how-it-works__grid-matrix">
          {steps.map((step, i) => (
            <div
              key={i}
              className={`step-card ${i === 2 ? 'step-card--span2' : ''}`}
            >
              {/* Step label, title, and icon aligned on the same header line */}
              <div className="step-card__header-row">
                <div className="step-card__title-group">
                  <span className="step-card__label">{step.label}</span>
                  <h3 className="step-card__title">{step.title}</h3>
                </div>
                <div className="step-card__icon">{step.icon}</div>
              </div>

              <p className="step-card__desc">{step.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
