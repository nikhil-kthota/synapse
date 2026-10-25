import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function RequireAuth({ children }) {
  const { user, checking } = useAuth();
  const location = useLocation();

  if (checking) {
    return <div className="app-loading"><span className="app-spinner" /> Restoring your session…</div>;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return children;
}
