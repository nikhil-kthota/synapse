import { useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import './ScrollDotNav.css';

const SECTIONS = [
  { id: 'hero',         label: 'Home' },
  { id: 'how-it-works', label: 'How It Works' },
  { id: 'features',     label: 'Features' },
  { id: 'preview',      label: 'Preview' },
];

// Semicircle arc offset: items in the middle are pushed further left
const arcOffset = (index, total) => {
  const t = index / (total - 1);        // 0 → 1 top to bottom
  return Math.sin(t * Math.PI) * 28;    // 0 at ends, 28px at center
};

export default function ScrollDotNav() {
  const [active, setActive] = useState('hero');

  useEffect(() => {
    const handleScroll = () => {
      const triggerPoint = window.scrollY + window.innerHeight * 0.35;
      let current = 'hero';

      SECTIONS.forEach(({ id }) => {
        const el = document.getElementById(id);
        if (el) {
          const top = el.offsetTop;
          if (triggerPoint >= top) {
            current = id;
          }
        }
      });

      setActive(current);
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    handleScroll();

    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const scrollTo = (id) => {
    const el = document.getElementById(id);
    if (!el) return;
    if (window.__lenis) {
      window.__lenis.scrollTo(el, { offset: -80, duration: 1.1 });
    } else {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  return (
    <nav className="scroll-dot-nav" aria-label="Section navigation">
      {/* Vertical spine line */}
      <div className="scroll-dot-spine" />

      {SECTIONS.map(({ id, label }, i) => {
        const isActive = active === id;
        const offset = arcOffset(i, SECTIONS.length);

        return (
          <motion.div
            key={id}
            className={`dot-item ${isActive ? 'dot-item--active' : ''}`}
            style={{ x: -offset }}
            animate={{ x: -offset }}
            transition={{ type: 'spring', stiffness: 260, damping: 28 }}
            onClick={() => scrollTo(id)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => e.key === 'Enter' && scrollTo(id)}
            aria-label={`Go to ${label} section`}
          >
            {/* Label */}
            <motion.span
              className="dot-label"
              animate={{
                opacity: isActive ? 1 : 0.3,
                fontSize: isActive ? '1.15rem' : '0.88rem',
              }}
              transition={{ type: 'spring', stiffness: 320, damping: 28 }}
            >
              {label}
            </motion.span>

            {/* Dot + pulse ring */}
            <div className="dot-wrap">
              <AnimatePresence>
                {isActive && (
                  <motion.div
                    className="dot-ring"
                    key="ring"
                    initial={{ scale: 1, opacity: 0.8 }}
                    animate={{ scale: 1 }}
                    exit={{ scale: 0, opacity: 0 }}
                  />
                )}
              </AnimatePresence>
              <motion.div
                className="dot-core"
                animate={{
                  scale: isActive ? 1.55 : 1,
                  backgroundColor: isActive
                    ? 'var(--accent-blue)'
                    : 'var(--text-muted)',
                  boxShadow: isActive
                    ? '0 0 14px var(--accent-blue-glow-strong)'
                    : 'none',
                }}
                transition={{ type: 'spring', stiffness: 400, damping: 25 }}
              />
            </div>
          </motion.div>
        );
      })}
    </nav>
  );
}
