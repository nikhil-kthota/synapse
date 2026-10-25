import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import LogoIcon from '../../components/LogoIcon';
import HeroBackground from '../../components/HeroBackground/HeroBackground';
import Footer from '../../components/Footer/Footer';
import { useAuth } from '../../context/AuthContext';
import './SignUp.css';

const formVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.06 } },
};

const pageVariants = {
  initial: { opacity: 0, y: 15 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.45 } },
  exit: { opacity: 0, y: -15, transition: { duration: 0.3 } },
};

export default function SignUp() {
  const [form, setForm] = useState({ name: '', email: '', password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const { register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleChange = (e) => {
    setForm(f => ({ ...f, [e.target.name]: e.target.value }));
    setErrors(er => ({ ...er, [e.target.name]: '' }));
    setServerError('');
  };

  const validate = () => {
    const errs = {};
    if (!form.name.trim()) errs.name = 'Name is required';
    if (!form.email.includes('@')) errs.email = 'Enter a valid email';
    if (form.password.length < 8) errs.password = 'Min. 8 characters';
    else if (form.password.length > 72) errs.password = 'Max. 72 characters';
    if (form.password !== form.confirm) errs.confirm = 'Passwords do not match';
    return errs;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length) { setErrors(errs); return; }
    setSubmitting(true);
    try {
      await register(form.name.trim(), form.email.trim(), form.password);
      navigate(location.state?.from || '/app', { replace: true });
    } catch (err) {
      if (err.fields && Object.keys(err.fields).length) setErrors(err.fields);
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
      <div className="auth-page">
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
                <h3 className="sidebar-info-title">KNOWLEDGE ENGINE</h3>
                <p className="sidebar-info-desc">
                  Synapse ingests legacy codebases and extracts living, visual dependency graphs in seconds.
                </p>
                <div className="sidebar-specs">
                  <span className="sidebar-spec-item">✓ Auto Documentation</span>
                  <span className="sidebar-spec-item">✓ Zero Config Required</span>
                </div>
              </div>
            </aside>

            {/* Right Form Card Matrix (Shifted slightly right) */}
            <form onSubmit={handleSubmit} noValidate className="auth-right-form">
              {/* Row 1: Title & Subtitle (Spans 2 cols) */}
              <div className="card-cell card-cell--full card-cell--heading">
                <h1 className="auth-title">Create your account</h1>
                <p className="auth-subtitle">Start analyzing legacy code.</p>
              </div>

              {/* Row 4: Input Col 1 (Full Name) & Input Col 2 (Email) */}
              <div className="card-cell card-cell--input">
                <label className="card-input-label" htmlFor="signup-name">Full Name</label>
                <input
                  id="signup-name"
                  name="name"
                  type="text"
                  className="card-input-field"
                  placeholder="Ada Lovelace"
                  value={form.name}
                  onChange={handleChange}
                  autoComplete="name"
                />
                {errors.name && <span className="auth-error">{errors.name}</span>}
              </div>

              <div className="card-cell card-cell--input">
                <label className="card-input-label" htmlFor="signup-email">Email</label>
                <input
                  id="signup-email"
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

              {/* Row 5: Input Col 1 (Password) & Input Col 2 (Confirm Password) */}
              <div className="card-cell card-cell--input">
                <label className="card-input-label" htmlFor="signup-password">Password</label>
                <input
                  id="signup-password"
                  name="password"
                  type="password"
                  className="card-input-field"
                  placeholder="Min. 8 characters"
                  value={form.password}
                  onChange={handleChange}
                  autoComplete="new-password"
                />
                {errors.password && <span className="auth-error">{errors.password}</span>}
              </div>

              <div className="card-cell card-cell--input">
                <label className="card-input-label" htmlFor="signup-confirm">Confirm Password</label>
                <input
                  id="signup-confirm"
                  name="confirm"
                  type="password"
                  className="card-input-field"
                  placeholder="Repeat password"
                  value={form.confirm}
                  onChange={handleChange}
                  autoComplete="new-password"
                />
                {errors.confirm && <span className="auth-error">{errors.confirm}</span>}
              </div>

              {serverError && (
                <div className="card-cell card-cell--full card-cell--server-error" role="alert">
                  {serverError}
                </div>
              )}

              {/* Row 6: Submit Button (Spans 2 cols) */}
              <button type="submit" className="card-cell card-cell--full card-cell--submit" disabled={submitting}>
                {submitting ? 'Creating account…' : 'Create Account →'}
              </button>

              {/* Row 7: Login Footer Link (Spans 2 cols) */}
              <div className="card-cell card-cell--full card-cell--footer">
                <span>Already have an account? </span>
                <Link to="/login" className="auth-link">Login</Link>
              </div>
            </form>
          </motion.div>
        </main>
      </div>
      <Footer />
    </motion.div>
  );
}
