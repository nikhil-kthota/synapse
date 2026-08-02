import { HashRouter, Routes, Route, useLocation } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import { ThemeProvider } from './context/ThemeContext';
import Landing from './pages/Landing/Landing';
import SignUp from './pages/SignUp/SignUp';
import Login from './pages/Login/Login';

function AnimatedRoutes() {
  const location = useLocation();
  return (
    <AnimatePresence mode="wait">
      <Routes location={location} key={location.pathname}>
        <Route path="/"       element={<Landing />} />
        <Route path="/signup" element={<SignUp />} />
        <Route path="/login"  element={<Login />} />
      </Routes>
    </AnimatePresence>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <HashRouter>
        <AnimatedRoutes />
      </HashRouter>
    </ThemeProvider>
  );
}
