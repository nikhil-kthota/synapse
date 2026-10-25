import { HashRouter, Routes, Route, useLocation, Navigate } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import { ThemeProvider } from './context/ThemeContext';
import { AuthProvider, useAuth } from './context/AuthContext';
import RequireAuth from './components/RequireAuth';
import Landing from './pages/Landing/Landing';
import SignUp from './pages/SignUp/SignUp';
import Login from './pages/Login/Login';
import Dashboard from './pages/Dashboard/Dashboard';
import Project from './pages/Project/Project';
import Account from './pages/Account/Account';

/** Signed-in users skip the login and signup screens. */
function GuestOnly({ children }) {
  const { user } = useAuth();
  return user ? <Navigate to="/app" replace /> : children;
}

function AnimatedRoutes() {
  const location = useLocation();
  return (
    <AnimatePresence mode="wait">
      <Routes location={location} key={location.pathname}>
        <Route path="/"       element={<Landing />} />
        <Route path="/signup" element={<GuestOnly><SignUp /></GuestOnly>} />
        <Route path="/login"  element={<GuestOnly><Login /></GuestOnly>} />
        <Route path="/app"    element={<RequireAuth><Dashboard /></RequireAuth>} />
        <Route path="/app/projects/:id" element={<RequireAuth><Project /></RequireAuth>} />
        <Route path="/app/account" element={<RequireAuth><Account /></RequireAuth>} />
        <Route path="*"       element={<Navigate to="/" replace />} />
      </Routes>
    </AnimatePresence>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <HashRouter>
          <AnimatedRoutes />
        </HashRouter>
      </AuthProvider>
    </ThemeProvider>
  );
}
