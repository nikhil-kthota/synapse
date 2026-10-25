import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import Navbar from '../../components/Navbar/Navbar';
import ScrollDotNav from '../../components/ScrollDotNav/ScrollDotNav';
import Footer from '../../components/Footer/Footer';
import Hero from './Hero';
import HowItWorks from './HowItWorks';
import Features from './Features';
import Preview from './Preview';

const pageVariants = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: { duration: 0.5 } },
  exit:    { opacity: 0, transition: { duration: 0.35 } },
};

export default function Landing() {
  const location = useLocation();
  const navigate = useNavigate();

  // Footer/menu links from other pages arrive with the section to show.
  useEffect(() => {
    const id = location.state?.scrollTo;
    if (!id) return undefined;
    const timer = setTimeout(() => {
      const el = document.getElementById(id);
      if (el && window.__lenis) window.__lenis.scrollTo(el, { offset: -80, duration: 1.1 });
      else el?.scrollIntoView({ behavior: 'smooth' });
      navigate('.', { replace: true, state: null });
    }, 450);
    return () => clearTimeout(timer);
  }, [location.state, navigate]);

  return (
    <motion.div
      variants={pageVariants}
      initial="initial"
      animate="animate"
      exit="exit"
    >
      <Navbar />
      <ScrollDotNav />
      <main>
        <Hero />
        <HowItWorks />
        <Features />
        <Preview />
      </main>
      <Footer />
    </motion.div>
  );
}
