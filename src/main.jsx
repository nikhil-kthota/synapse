import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import Lenis from 'lenis';
import './styles/index.css';
import './styles/animations.css';
import App from './App.jsx';

// Smooth scroll with Lenis
const lenis = new Lenis({
  duration: 1.15,
  easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
  smoothWheel: true,
  wheelMultiplier: 1,
});

function raf(time) {
  lenis.raf(time);
  requestAnimationFrame(raf);
}
requestAnimationFrame(raf);

// Expose lenis for ScrollDotNav smooth anchor scrolling
window.__lenis = lenis;

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
