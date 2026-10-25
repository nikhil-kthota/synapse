const RUNNING = new Set(['QUEUED', 'FETCHING', 'SCANNING', 'PARSING', 'BUILDING_GRAPH', 'GENERATING_DOCS']);

export const isRunning = (status) => RUNNING.has(status);

/** The graph is saved before AI documentation starts, so it can be explored during that stage. */
export const graphReady = (status) => status === 'COMPLETED' || status === 'GENERATING_DOCS';

export function statusChip(status) {
  switch (status) {
    case 'COMPLETED': return { label: 'Ready', className: 'chip--ok' };
    case 'FAILED': return { label: 'Failed', className: 'chip--err' };
    case 'QUEUED': return { label: 'Queued', className: 'chip--warn' };
    case 'GENERATING_DOCS': return { label: 'Writing docs', className: 'chip--blue' };
    default: return { label: 'Analyzing', className: 'chip--blue' };
  }
}

export const PIPELINE = [
  { key: 'FETCHING', label: 'Fetch' },
  { key: 'SCANNING', label: 'Scan' },
  { key: 'PARSING', label: 'Parse' },
  { key: 'BUILDING_GRAPH', label: 'Graph' },
  { key: 'GENERATING_DOCS', label: 'AI Docs' },
];

export function formatNumber(n) {
  if (n === undefined || n === null) return '—';
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 10_000) return `${Math.round(n / 1000)}k`;
  return n.toLocaleString('en-US');
}

export function timeAgo(iso) {
  if (!iso) return '';
  const seconds = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return 'just now';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.round(hours / 24);
  if (days < 30) return `${days} d ago`;
  return new Date(iso).toLocaleDateString();
}
