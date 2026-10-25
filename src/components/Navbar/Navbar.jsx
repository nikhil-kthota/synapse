import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import ThemeToggle from '../ThemeToggle/ThemeToggle';
import LogoIcon from '../LogoIcon';
import { useAuth } from '../../context/AuthContext';
import { useSectionLink } from '../Footer/Footer';
import './Navbar.css';

export default function Navbar({ variant = 'landing' }) {
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const accountRef = useRef(null);
  const closeTimer = useRef(null);
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const isApp = variant === 'app';
  const sectionLink = useSectionLink();

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 30);
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  // Close menus on route change
  useEffect(() => { setMenuOpen(false); setAccountOpen(false); }, [location]);

  // Close the account menu on outside click or Escape (for touch and keyboard users).
  useEffect(() => {
    if (!accountOpen) return undefined;
    const onDown = (e) => {
      if (!accountRef.current?.contains(e.target) && !e.target.closest?.('.navbar__dropdown')) setAccountOpen(false);
    };
    const onKey = (e) => { if (e.key === 'Escape') setAccountOpen(false); };
    document.addEventListener('pointerdown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [accountOpen]);

  // Hover opens the menu; a short delay lets the pointer travel into it.
  const [dropdownRight, setDropdownRight] = useState(0);
  const openAccount = () => {
    clearTimeout(closeTimer.current);
    const rect = accountRef.current?.getBoundingClientRect();
    if (rect) setDropdownRight(Math.max(0, document.documentElement.clientWidth - rect.right));
    setAccountOpen(true);
  };
  const closeAccountSoon = () => { closeTimer.current = setTimeout(() => setAccountOpen(false), 160); };
  useEffect(() => () => clearTimeout(closeTimer.current), []);

  // Dashboard is the way back from anywhere; on the dashboard itself the first link goes Home.
  const onDashboard = location.pathname === '/app';
  const primary = onDashboard ? { to: '/', label: 'Home' } : { to: '/app', label: 'Dashboard' };

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
        {isApp ? (
          <div className="navbar__spacer" />
        ) : (
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
        )}

        {/* Navigation items displayed directly sequentially across all screen sizes */}
        <nav className="navbar__actions">
          {user ? (
            <>
              <Link to={primary.to} className="navbar__text-link">
                {primary.label}
              </Link>
              <div
                className="navbar__account"
                ref={accountRef}
                onMouseEnter={openAccount}
                onMouseLeave={closeAccountSoon}
                onFocus={openAccount}
              >
                <button
                  type="button"
                  className={`navbar__text-link navbar__text-link--signup navbar__account-btn ${location.pathname === '/app/account' ? 'navbar__text-link--active' : ''}`}
                  aria-haspopup="menu"
                  aria-expanded={accountOpen}
                  onClick={() => (accountOpen ? setAccountOpen(false) : openAccount())}
                >
                  Account
                  <svg className={`navbar__caret ${accountOpen ? 'open' : ''}`} width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">
                    <path d="M1.5 3.5 5 7l3.5-3.5" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </button>
              </div>
            </>
          ) : (
            <>
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
            </>
          )}
          <div className="navbar__toggle-wrapper">
            <ThemeToggle />
          </div>
        </nav>
      </div>

      {/* Account menu lives outside .navbar__inner, which clips overflow on small screens */}
      <AnimatePresence>
        {accountOpen && user && (
          <motion.div
            className="navbar__dropdown"
            role="menu"
            style={{ right: dropdownRight }}
            onMouseEnter={openAccount}
            onMouseLeave={closeAccountSoon}
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.16 }}
          >
            <div className="navbar__dropdown-head">
              <span className="navbar__dropdown-name">{user.name}</span>
              <span className="navbar__dropdown-email">{user.email}</span>
            </div>
            <Link to="/app/account" role="menuitem" className="navbar__dropdown-item">
              View profile
            </Link>
            <button type="button" role="menuitem" className="navbar__dropdown-item navbar__dropdown-item--danger" onClick={handleLogout}>
              Log out
            </button>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Dropdown navigation menu for landing sections on screens <= 770px */}
      <AnimatePresence>
        {menuOpen && !isApp && (
          <motion.nav
            className="navbar__mobile-menu"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.28, ease: [0.25, 0.46, 0.45, 0.94] }}
          >
            {[['hero', 'Home'], ['how-it-works', 'How It Works'], ['features', 'Features'], ['preview', 'Preview']].map(([id, label]) => (
              <a key={id} href="#/" className="navbar__mobile-link"
                onClick={(e) => { sectionLink(id)(e); setMenuOpen(false); }}>
                {label}
              </a>
            ))}
          </motion.nav>
        )}
      </AnimatePresence>
    </motion.header>
  );
}
