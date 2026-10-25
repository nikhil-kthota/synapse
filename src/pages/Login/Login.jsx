import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import LogoIcon from '../../components/LogoIcon';
import HeroBackground from '../../components/HeroBackground/HeroBackground';
import Footer from '../../components/Footer/Footer';
import { useAuth } from '../../context/AuthContext';
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
  const [serverError, setServerError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleChange = (e) => {
    setForm(f => ({ ...f, [e.target.name]: e.target.value }));
    setErrors(er => ({ ...er, [e.target.name]: '' }));
    setServerError('');
  };

  const validate = () => {
    const errs = {};
    if (!form.email.includes('@')) errs.email = 'Enter a valid email';
    if (!form.password) errs.password = 'Password is required';
    return errs;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length) { setErrors(errs); return; }
    setSubmitting(true);
    try {
      await login(form.email.trim(), form.password);
      navigate(location.state?.from || '/app', { replace: true });
    } catch (err) {
      setServerError(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <motion.div
      variants={pageVariants}
      initial="initial"
      animate="animate"
      exit="exit"
    >
      <div className="auth-page login-page">
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
                <label className="card-input-label" htmlFor="login-password">Password</label>
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

              {serverError && (
                <div className="card-cell card-cell--full card-cell--server-error" role="alert">
                  {serverError}
                </div>
              )}

              {/* Row 6: Submit Button (Spans 2 cols) */}
              <button type="submit" className="card-cell card-cell--full card-cell--submit" disabled={submitting}>
                {submitting ? 'Signing in…' : 'Sign In →'}
              </button>

              {/* Row 7: Signup Footer Link (Spans 2 cols) */}
              <div className="card-cell card-cell--full card-cell--footer">
                <span>New to Synapse? </span>
                <Link to="/signup" className="auth-link">Create an account</Link>
              </div>
            </form>
          </motion.div>
        </main>
      </div>
      <Footer />
    </motion.div>
  );
}
