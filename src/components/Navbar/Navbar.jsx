import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import ThemeToggle from '../ThemeToggle/ThemeToggle';
import LogoIcon from '../LogoIcon';
import './Navbar.css';

export default function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 30);
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  // Close mobile menu on route change
  useEffect(() => { setMenuOpen(false); }, [location]);

  return (
    <motion.header
      className={`navbar ${scrolled ? 'navbar--scrolled' : ''}`}
      initial={{ y: -80, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      transition={{ duration: 0.7, ease: [0.25, 0.46, 0.45, 0.94] }}
    >
      <div className="navbar__inner">
        {/* Logo bounded directly by exact CSS borders */}
        <Link to="/" className="navbar__logo">
          <span className="navbar__logo-mark"><LogoIcon size={26} /></span>
          <span className="navbar__logo-text">SYNAPSE</span>
        </Link>

        {/* Dynamically sizing middle hamburger button visible exclusively on screens <= 770px */}
        <button
          className={`navbar__burger-middle ${menuOpen ? 'open' : ''}`}
          onClick={() => setMenuOpen(v => !v)}
          aria-label="Toggle section navigation menu"
          aria-expanded={menuOpen}
        >
          <div className="navbar__burger-icon">
            <span /><span /><span />
          </div>
        </button>

        {/* Navigation items displayed directly sequentially across all screen sizes */}
        <nav className="navbar__actions">
          <Link
            to="/login"
            className={`navbar__text-link ${location.pathname === '/login' ? 'navbar__text-link--active' : ''}`}
          >
            Login
          </Link>
          <Link
            to="/signup"
            className={`navbar__text-link navbar__text-link--signup ${location.pathname === '/signup' ? 'navbar__text-link--active' : ''}`}
          >
            Signup
          </Link>
          <div className="navbar__toggle-wrapper">
            <ThemeToggle />
          </div>
        </nav>
      </div>

      {/* Dropdown navigation menu for landing sections on screens <= 770px */}
      <AnimatePresence>
        {menuOpen && (
          <motion.nav
            className="navbar__mobile-menu"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.28, ease: [0.25, 0.46, 0.45, 0.94] }}
          >
            <a href="#hero" className="navbar__mobile-link" onClick={() => setMenuOpen(false)}>Home</a>
            <a href="#how-it-works" className="navbar__mobile-link" onClick={() => setMenuOpen(false)}>How It Works</a>
            <a href="#features" className="navbar__mobile-link" onClick={() => setMenuOpen(false)}>Features</a>
            <a href="#preview" className="navbar__mobile-link" onClick={() => setMenuOpen(false)}>Preview</a>
          </motion.nav>
        )}
      </AnimatePresence>
    </motion.header>
  );
}
