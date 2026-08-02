import { Link } from 'react-router-dom';
import LogoIcon from '../LogoIcon';
import './Footer.css';

export default function Footer() {
  return (
    <footer className="footer">
      {/* Architectural Dot-Matrix Field matching Hero background */}
      <div className="footer-bg__grid" aria-hidden="true" />

      <div className="footer__inner container">
        <div className="footer__left">
          <div className="footer__logo">
            <span className="footer__logo-mark"><LogoIcon size={22} /></span>
            <span className="footer__logo-text">SYNAPSE</span>
          </div>
          <p className="footer__tagline">
            Your codebase has a brain. We help you unlock it.
          </p>
        </div>

        <div className="footer__links">
          <Link to="/login" className="footer__link">Log In</Link>
          <Link to="/signup" className="footer__link">Sign Up</Link>
          <a href="#features" className="footer__link">Features</a>
          <a href="#how-it-works" className="footer__link">How It Works</a>
        </div>
      </div>

      <div className="footer__bottom container">
        <span className="footer__copy">© 2026 Synapse. All rights reserved.</span>
        <span className="footer__sub">Built for developers navigating legacy code.</span>
      </div>
    </footer>
  );
}
