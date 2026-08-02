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
