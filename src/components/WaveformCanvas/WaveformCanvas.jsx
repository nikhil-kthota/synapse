import { useEffect, useRef } from 'react';

const NUM_BARS = 52;

export default function WaveformCanvas({ opacity = 0.15, fullIntensity = false, height = '100%' }) {
  const canvasRef = useRef(null);
  const rafRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    const resize = () => {
      canvas.width  = canvas.offsetWidth;
      canvas.height = canvas.offsetHeight;
    };
    resize();
    const ro = new ResizeObserver(resize);
    ro.observe(canvas);

    const startTime = performance.now();

    const draw = (now) => {
      const t = (now - startTime) / 1000;
      const { width: W, height: H } = canvas;
      ctx.clearRect(0, 0, W, H);
      ctx.globalCompositeOperation = 'screen';

      const barW = W / NUM_BARS;
      const maxH = H * 0.82;

      for (let i = 0; i < NUM_BARS; i++) {
        const x = i / (NUM_BARS - 1); // 0→1

        // Morphing shape: arch → slope → hourglass → mirror arch
        const wave1 = Math.sin(x * Math.PI);                           // arch
        const wave2 = Math.sin(x * Math.PI + t * 0.6);                 // rolling arch
        const wave3 = Math.abs(Math.sin(x * Math.PI * 2));             // hourglass / 2 humps
        const wave4 = Math.cos(t * 0.4) * wave1 + Math.sin(t * 0.55) * wave2
                    + Math.cos(t * 0.75) * wave3;
        const hf = Math.max(0.04, (wave4 + 1.5) / 3.5);
        const barH = hf * maxH;

        const px = i * barW + barW * 0.15;
        const bw = barW * 0.7;
        const y  = H - barH;

        // ── Bar gradient: white center → blue edge ──────────────────
        const grad = ctx.createLinearGradient(px + bw / 2, y, px + bw / 2, H);
        const a = fullIntensity ? 1 : 0.65;
        grad.addColorStop(0,    `rgba(255,255,255,${a})`);
        grad.addColorStop(0.18, `rgba(74,158,255,${a * 0.9})`);
        grad.addColorStop(0.6,  `rgba(74,158,255,${a * 0.45})`);
        grad.addColorStop(1,    `rgba(232,116,42,0.0)`);

        ctx.fillStyle = grad;
        ctx.fillRect(px, y, bw, barH);

        // Orange tint on peak bars
        if (hf > 0.78) {
          const og = ctx.createLinearGradient(px + bw / 2, y, px + bw / 2, y + 28);
          og.addColorStop(0, `rgba(245,160,80,${(hf - 0.78) * 2.2 * a})`);
          og.addColorStop(1, 'rgba(245,160,80,0)');
          ctx.fillStyle = og;
          ctx.fillRect(px, y, bw, 28);
        }

        // Downward reflection streak
        const rg = ctx.createLinearGradient(px + bw / 2, H, px + bw / 2, H + barH * 0.28);
        rg.addColorStop(0,   `rgba(74,158,255,${fullIntensity ? 0.18 : 0.07})`);
        rg.addColorStop(1,   'rgba(74,158,255,0)');
        ctx.fillStyle = rg;
        ctx.fillRect(px, H, bw, barH * 0.28);

        // Soft glow halo
        ctx.shadowBlur  = fullIntensity ? 18 : 10;
        ctx.shadowColor = `rgba(74,158,255,${fullIntensity ? 0.55 : 0.25})`;
        ctx.fillStyle   = `rgba(74,158,255,${fullIntensity ? 0.08 : 0.03})`;
        ctx.fillRect(px - 2, y, bw + 4, barH);
        ctx.shadowBlur  = 0;
      }

      ctx.globalCompositeOperation = 'source-over';
      rafRef.current = requestAnimationFrame(draw);
    };

    rafRef.current = requestAnimationFrame(draw);
    return () => {
      cancelAnimationFrame(rafRef.current);
      ro.disconnect();
    };
  }, [fullIntensity]);

  return (
    <canvas
      ref={canvasRef}
      style={{ width: '100%', height, display: 'block', opacity }}
    />
  );
}
