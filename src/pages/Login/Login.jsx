import { useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import LogoIcon from '../../components/LogoIcon';
import HeroBackground from '../../components/HeroBackground/HeroBackground';
import '../SignUp/SignUp.css';
import './Login.css';

const formVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.06 } },
};

const pageVariants = {
  initial: { opacity: 0, y: 15 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.45 } },
  exit: { opacity: 0, y: -15, transition: { duration: 0.3 } },
};

export default function Login() {
  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});

  const handleChange = (e) => {
    setForm(f => ({ ...f, [e.target.name]: e.target.value }));
    setErrors(er => ({ ...er, [e.target.name]: '' }));
  };

  const validate = () => {
    const errs = {};
    if (!form.email.includes('@')) errs.email = 'Enter a valid email';
    if (!form.password) errs.password = 'Password is required';
    return errs;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length) { setErrors(errs); return; }
    alert('Signed in! (Backend integration pending)');
  };

  return (
    <motion.div
      className="auth-page login-page"
      variants={pageVariants}
      initial="initial"
      animate="animate"
      exit="exit"
    >
      {/* Background matching exact Hero section with left knowledge graph & dot-matrix */}
      <HeroBackground />

      {/* Main Centered Architectural Bento Layout */}
      <main className="auth-main-container">
        <motion.div
          className="auth-bento-layout"
          variants={formVariants}
          initial="hidden"
          animate="visible"
        >
          {/* Left Grid Sidebar Column (Full Height of Form) */}
          <aside className="auth-left-sidebar">
            {/* Cell 1: Back to Home */}
            <Link to="/" className="sidebar-cell sidebar-cell--link">
              ← Back to Home
            </Link>

            {/* Cell 2: Synapse Logo */}
            <Link to="/" className="sidebar-cell sidebar-cell--logo">
              <span className="auth-logo-mark"><LogoIcon size={22} /></span>
              <span className="auth-logo-text">SYNAPSE</span>
            </Link>

            {/* Cell 3: Telemetry & Brand Info */}
            <div className="sidebar-cell sidebar-cell--info">
              <h3 className="sidebar-info-title">WELCOME BACK</h3>
              <p className="sidebar-info-desc">
                Sign in to access your saved knowledge graphs, API specs, and codebase intelligence.
              </p>
              <div className="sidebar-specs">
                <span className="sidebar-spec-item">✓ Auto Documentation</span>
                <span className="sidebar-spec-item">✓ Zero Config Required</span>
              </div>
            </div>
          </aside>

          {/* Right Form Card Matrix */}
          <form onSubmit={handleSubmit} noValidate className="auth-right-form">
            {/* Row 1: Title & Subtitle (Spans 2 cols) */}
            <div className="card-cell card-cell--full card-cell--heading">
              <h1 className="auth-title">Log in</h1>
              <p className="auth-subtitle">Good to have you back. Let's explore your codebase.</p>
            </div>

            {/* Row 2: OAuth Options (GitHub & Google) */}
            <button type="button" className="card-cell card-cell--oauth">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 0C5.373 0 0 5.373 0 12c0 5.302 3.438 9.8 8.207 11.387.6.113.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23A11.509 11.509 0 0 1 12 5.803c1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576C20.566 21.797 24 17.3 24 12c0-6.627-5.373-12-12-12z" />
              </svg>
              Login with GitHub
            </button>

            <button type="button" className="card-cell card-cell--oauth">
              <svg width="18" height="18" viewBox="0 0 24 24">
                <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
                <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
              </svg>
              Login with Google
            </button>

            {/* Row 3: Divider Label (Spans 2 cols) */}
            <div className="card-cell card-cell--full card-cell--divider">
              <span>OR CONTINUE WITH EMAIL</span>
            </div>

            {/* Row 4: Input Email (Spans 2 cols) */}
            <div className="card-cell card-cell--full card-cell--input">
              <label className="card-input-label" htmlFor="login-email">Email</label>
              <input
                id="login-email"
                name="email"
                type="email"
                className="card-input-field"
                placeholder="ada@lab.dev"
                value={form.email}
                onChange={handleChange}
                autoComplete="email"
              />
              {errors.email && <span className="auth-error">{errors.email}</span>}
            </div>

            {/* Row 5: Input Password (Spans 2 cols) */}
            <div className="card-cell card-cell--full card-cell--input">
              <div className="login-password-header">
                <label className="card-input-label" htmlFor="login-password">Password</label>
                <Link to="/forgot-password" className="auth-forgot-link">
                  Forgot password?
                </Link>
              </div>
              <input
                id="login-password"
                name="password"
                type="password"
                className="card-input-field"
                placeholder="Your password"
                value={form.password}
                onChange={handleChange}
                autoComplete="current-password"
              />
              {errors.password && <span className="auth-error">{errors.password}</span>}
            </div>

            {/* Row 6: Submit Button (Spans 2 cols) */}
            <button type="submit" className="card-cell card-cell--full card-cell--submit">
              Sign In →
            </button>

            {/* Row 7: Signup Footer Link (Spans 2 cols) */}
            <div className="card-cell card-cell--full card-cell--footer">
              <span>New to Synapse? </span>
              <Link to="/signup" className="auth-link">Create an account</Link>
            </div>
          </form>
        </motion.div>
      </main>
    </motion.div>
  );
}
