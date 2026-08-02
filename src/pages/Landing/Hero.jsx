import { useRef } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import HeroBackground from '../../components/HeroBackground/HeroBackground';
import './Hero.css';

const textVariants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { staggerChildren: 0.12 } },
};

const itemVariants = {
  hidden: { opacity: 0, y: 42 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.75, ease: [0.25, 0.46, 0.45, 0.94] } },
};

export default function Hero() {
  return (
    <section id="hero" className="hero">
      {/* Interactive Architectural Knowledge Graph Background */}
      <HeroBackground />

      {/* Centered Main Content */}
      <motion.div
        className="hero__content container"
        variants={textVariants}
        initial="hidden"
        animate="visible"
      >
        {/* Brand label */}
        <motion.div className="hero__brand" variants={itemVariants}>
          <span className="hero__brand-dot" />
          <span className="hero__brand-name">SYNAPSE</span>
          <span className="hero__brand-dot" />
        </motion.div>

        {/* High-tech pill badge */}
        <motion.div className="hero__badge-wrapper" variants={itemVariants}>
          <div className="hero__badge">
            <span className="hero__badge-dot" />
            <span className="hero__badge-text">AI-Powered Legacy Software Knowledge Graph Generation System</span>
          </div>
        </motion.div>

        {/* Massive Centered Headline */}
        <motion.h1 className="hero__headline" variants={itemVariants}>
          YOUR CODEBASE HAS <br /><span className="gradient-text">A BRAIN.</span><br />
          UNLOCK IT.
        </motion.h1>

        {/* Sub-tagline */}
        <motion.p className="hero__tagline" variants={itemVariants}>
          Synapse transforms legacy codebases into living knowledge graphs<br />
          and AI-generated documentation that actually makes sense.
        </motion.p>

        {/* CTAs */}
        <motion.div className="hero__ctas" variants={itemVariants}>
          <Link to="/signup" className="btn btn-primary hero__cta-primary">
            Analyze a Repository
            <svg width="15" height="15" viewBox="0 0 14 14" fill="none">
              <path d="M2 7h10M7 2l5 5-5 5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </Link>
          <a href="#how-it-works" className="btn btn-ghost hero__cta-ghost">
            See How It Works
          </a>
        </motion.div>
      </motion.div>

      {/* Scroll indicator */}
      <div className="hero__scroll-indicator">
        <div className="hero__scroll-chevron" />
      </div>
    </section>
  );
}
