import { Link, useLocation, useNavigate } from 'react-router-dom';
import LogoIcon from '../LogoIcon';
import { useAuth } from '../../context/AuthContext';
import './Footer.css';

const SECTIONS = [
  { id: 'features', label: 'Features' },
  { id: 'how-it-works', label: 'How It Works' },
  { id: 'preview', label: 'Preview' },
];

/** Scrolls to a landing-page section; from other pages it navigates home first (see Landing). */
export function useSectionLink() {
  const location = useLocation();
  const navigate = useNavigate();
  return (id) => (e) => {
    e.preventDefault();
    if (location.pathname === '/') {
      const el = document.getElementById(id);
      if (el && window.__lenis) window.__lenis.scrollTo(el, { offset: -80, duration: 1.1 });
      else el?.scrollIntoView({ behavior: 'smooth' });
    } else {
      navigate('/', { state: { scrollTo: id } });
    }
  };
}

export default function Footer() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const sectionLink = useSectionLink();

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  return (
    <footer className="footer">
      {/* Architectural Dot-Matrix Field matching Hero background */}
      <div className="footer-bg__grid" aria-hidden="true" />

      <div className="footer__inner container">
        <div className="footer__left">
          <Link to="/" className="footer__logo">
            <span className="footer__logo-mark"><LogoIcon size={22} /></span>
            <span className="footer__logo-text">SYNAPSE</span>
          </Link>
          <p className="footer__tagline">
            Your codebase has a brain. We help you unlock it.
          </p>
        </div>

        <div className="footer__columns">
          <nav className="footer__links" aria-label="Product">
            <span className="footer__heading">Product</span>
            {SECTIONS.map((s) => (
              <a key={s.id} href={`#/`} className="footer__link" onClick={sectionLink(s.id)}>{s.label}</a>
            ))}
          </nav>

          <nav className="footer__links" aria-label="Account">
            <span className="footer__heading">{user ? 'Workspace' : 'Account'}</span>
            {user ? (
              <>
                <Link to="/app" className="footer__link">Dashboard</Link>
                <Link to="/app/account" className="footer__link">Account</Link>
                <button type="button" className="footer__link footer__link--button" onClick={handleLogout}>Log Out</button>
              </>
            ) : (
              <>
                <Link to="/login" className="footer__link">Log In</Link>
                <Link to="/signup" className="footer__link">Sign Up</Link>
              </>
            )}
          </nav>

          <nav className="footer__links" aria-label="Project">
            <span className="footer__heading">Project</span>
            <a href="https://github.com/nikhil-kthota/synapse" className="footer__link" target="_blank" rel="noreferrer">GitHub</a>
          </nav>
        </div>
      </div>

      <div className="footer__bottom container">
        <span className="footer__copy">© 2026 Synapse. All rights reserved.</span>
        <span className="footer__sub">Built for developers navigating legacy code.</span>
      </div>
    </footer>
  );
}
