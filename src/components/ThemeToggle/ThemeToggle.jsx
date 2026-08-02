import { useTheme } from '../../context/ThemeContext';
import { motion } from 'framer-motion';
import './ThemeToggle.css';

export default function ThemeToggle() {
  const { theme, toggle } = useTheme();
  const isDark = theme === 'dark';

  return (
    <button
      className="theme-toggle"
      onClick={toggle}
      aria-label={`Switch to ${isDark ? 'light' : 'dark'} mode`}
      title={`Switch to ${isDark ? 'light' : 'dark'} mode`}
    >
      <div className="theme-toggle__box">
        {/* Background stippled dot-matrix pattern */}
        <div className="theme-toggle__dots-bg" />

        {/* Solid smooth sliding thumb panel */}
        <motion.div
          className="theme-toggle__panel"
          initial={false}
          animate={{ x: isDark ? 0 : '100%' }}
          transition={{ type: 'spring', stiffness: 450, damping: 32 }}
        />
      </div>
    </button>
  );
}
