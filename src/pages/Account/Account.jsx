import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import Navbar from '../../components/Navbar/Navbar';
import Footer from '../../components/Footer/Footer';
import AppDecor, { SheetHeader } from '../../components/AppDecor/AppDecor';
import { api } from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { formatNumber } from '../../utils/projectStatus';
import '../../styles/app.css';
import './Account.css';

const pageVariants = {
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.4 } },
  exit: { opacity: 0, transition: { duration: 0.25 } },
};

function initials(name) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('') || '?';
}

export default function Account() {
  const { user, setUser, logout } = useAuth();
  const navigate = useNavigate();
  const [projects, setProjects] = useState(null);

  useEffect(() => {
    api.projects().then(setProjects).catch(() => setProjects([]));
  }, []);

  const totals = (projects || []).reduce((acc, p) => ({
    files: acc.files + (p.stats?.files || 0),
    nodes: acc.nodes + (p.stats?.totalNodes || 0),
    links: acc.links + (p.stats?.totalEdges || 0),
  }), { files: 0, nodes: 0, links: 0 });

  const memberSince = user.createdAt
    ? new Date(user.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })
    : null;

  return (
    <motion.div className="app-page" variants={pageVariants} initial="initial" animate="animate" exit="exit">
      <Navbar variant="app" />
      <AppDecor sheet="ACCOUNT" />
      <main className="app-container">
        <SheetHeader
          index="03"
          label="ACCOUNT"
          title="Your account"
          subtitle="Manage your profile, password and data."
          tags={[
            { key: 'USER.ID', value: String(user.id).padStart(4, '0') },
            { key: 'SESSION', value: 'ACTIVE', tone: 'ok' },
            { key: 'GRAPHS', value: projects ? projects.length : '…' },
          ]}
        />

        <div className="bento cad account-grid">
          <div className="cell cell--8 account-profile">
            <div className="account-avatar" aria-hidden="true">{initials(user.name)}</div>
            <div className="account-identity">
              <span className="cell-label">Profile</span>
              <div className="cell-title account-name">{user.name}</div>
              <div className="mono account-email">{user.email}</div>
              {memberSince && <p className="cell-hint">Member since {memberSince}</p>}
            </div>
          </div>
          <div className="cell cell--4 cell--muted account-stats">
            <span className="cell-label">Workspace</span>
            <div className="account-stat-grid">
              <Stat label="Projects" value={projects ? projects.length : '—'} />
              <Stat label="Files" value={formatNumber(totals.files)} />
              <Stat label="Nodes" value={formatNumber(totals.nodes)} />
              <Stat label="Links" value={formatNumber(totals.links)} />
            </div>
          </div>

          <NameForm user={user} onSaved={setUser} />
          <PasswordForm />
        </div>

        <div className="app-section-head">
          <div>
            <span className="app-section-index">[04] // DANGER_ZONE</span>
            <h2 className="app-heading account-section-title">Delete account</h2>
          </div>
        </div>
        <DeleteAccount
          projectCount={projects?.length ?? 0}
          onDeleted={() => { logout(); navigate('/', { replace: true }); }}
        />
      </main>
      <Footer />
    </motion.div>
  );
}

function Stat({ label, value }) {
  return (
    <div className="account-stat">
      <span className="account-stat__value">{value}</span>
      <span className="account-stat__label">{label}</span>
    </div>
  );
}

function NameForm({ user, onSaved }) {
  const [name, setName] = useState(user.name);
  const [state, setState] = useState({ busy: false, error: '', saved: false });

  const save = async (e) => {
    e.preventDefault();
    if (!name.trim()) { setState({ busy: false, error: 'Name is required', saved: false }); return; }
    setState({ busy: true, error: '', saved: false });
    try {
      const updated = await api.updateProfile(name.trim());
      onSaved((u) => ({ ...u, ...updated }));
      setState({ busy: false, error: '', saved: true });
    } catch (err) {
      setState({ busy: false, error: err.message, saved: false });
    }
  };

  return (
    <form className="cell cell--6 account-form" onSubmit={save} noValidate>
      <span className="cell-label">Display name</span>
      <p className="cell-hint">Shown in the navigation bar and on your dashboard.</p>
      <input className="app-input" value={name} maxLength={120} autoComplete="name"
        onChange={(e) => { setName(e.target.value); setState((s) => ({ ...s, saved: false, error: '' })); }} />
      {state.error && <p className="account-msg account-msg--error">{state.error}</p>}
      {state.saved && <p className="account-msg account-msg--ok">Name updated.</p>}
      <button type="submit" className="btn-line btn-line--blue account-submit"
        disabled={state.busy || name.trim() === user.name}>
        {state.busy ? 'Saving…' : 'Save name'}
      </button>
    </form>
  );
}

function PasswordForm() {
  const [form, setForm] = useState({ current: '', next: '', confirm: '' });
  const [state, setState] = useState({ busy: false, error: '', saved: false });

  const update = (e) => {
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));
    setState((s) => ({ ...s, error: '', saved: false }));
  };

  const save = async (e) => {
    e.preventDefault();
    if (form.next.length < 8) { setState({ busy: false, error: 'New password must be at least 8 characters', saved: false }); return; }
    if (form.next !== form.confirm) { setState({ busy: false, error: 'New passwords do not match', saved: false }); return; }
    setState({ busy: true, error: '', saved: false });
    try {
      await api.changePassword(form.current, form.next);
      setForm({ current: '', next: '', confirm: '' });
      setState({ busy: false, error: '', saved: true });
    } catch (err) {
      setState({ busy: false, error: err.message, saved: false });
    }
  };

  return (
    <form className="cell cell--6 account-form" onSubmit={save} noValidate>
      <span className="cell-label">Change password</span>
      <input className="app-input" type="password" name="current" placeholder="Current password"
        value={form.current} onChange={update} autoComplete="current-password" />
      <div className="account-pair">
        <input className="app-input" type="password" name="next" placeholder="New password (8+ chars)"
          value={form.next} onChange={update} autoComplete="new-password" />
        <input className="app-input" type="password" name="confirm" placeholder="Repeat new password"
          value={form.confirm} onChange={update} autoComplete="new-password" />
      </div>
      {state.error && <p className="account-msg account-msg--error">{state.error}</p>}
      {state.saved && <p className="account-msg account-msg--ok">Password changed.</p>}
      <button type="submit" className="btn-line btn-line--blue account-submit"
        disabled={state.busy || !form.current || !form.next}>
        {state.busy ? 'Updating…' : 'Update password'}
      </button>
    </form>
  );
}

function DeleteAccount({ projectCount, onDeleted }) {
  const [password, setPassword] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [state, setState] = useState({ busy: false, error: '' });

  const remove = async (e) => {
    e.preventDefault();
    setState({ busy: true, error: '' });
    try {
      await api.deleteAccount(password);
      onDeleted();
    } catch (err) {
      setState({ busy: false, error: err.message });
    }
  };

  return (
    <form className="bento cad account-danger" onSubmit={remove} noValidate>
      <div className="cell cell--7 account-danger__info">
        <span className="cell-label account-danger__label">Permanent action</span>
        <div className="cell-title">This deletes everything</div>
        <p className="cell-text">
          Your account and all {projectCount} project{projectCount === 1 ? '' : 's'} — knowledge graphs, AI summaries and
          generated documentation — are removed immediately. This cannot be undone.
        </p>
      </div>
      <div className="cell cell--5 account-form">
        <span className="cell-label account-danger__label">Confirm with your password</span>
        <input className="app-input" type="password" placeholder="Password" value={password}
          onChange={(e) => { setPassword(e.target.value); setState({ busy: false, error: '' }); }} autoComplete="current-password" />
        <label className="account-check">
          <input type="checkbox" checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)} />
          I understand my account and projects will be permanently deleted.
        </label>
        {state.error && <p className="account-msg account-msg--error">{state.error}</p>}
      </div>
      <div className="cell" style={{ padding: 0 }}>
        <button type="submit" className="btn-cell btn-cell--danger" disabled={!password || !confirmed || state.busy}>
          {state.busy ? 'Deleting account…' : 'Delete my account permanently'}
        </button>
      </div>
    </form>
  );
}
