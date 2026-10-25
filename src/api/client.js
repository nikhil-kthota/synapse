// Thin wrapper around fetch for the Synapse backend (Spring Boot on :8080 by default).
export const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '');

const TOKEN_KEY = 'synapse-token';

export function getToken() {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}

export function setToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch { /* storage unavailable: session lasts until reload */ }
}

export class ApiError extends Error {
  constructor(message, status, fields) {
    super(message);
    this.status = status;
    this.fields = fields || {};
  }
}

async function request(path, { method = 'GET', body, form, raw, signal } = {}) {
  const headers = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      headers,
      body: form ?? (body !== undefined ? JSON.stringify(body) : undefined),
      signal,
    });
  } catch (err) {
    if (err.name === 'AbortError') throw err;
    throw new ApiError('Cannot reach the Synapse server. Is the backend running on ' + API_URL + '?', 0);
  }

  if (response.status === 401 && token) {
    setToken(null);
    window.dispatchEvent(new Event('synapse:unauthorized'));
  }
  if (!response.ok) {
    let data = {};
    try { data = await response.json(); } catch { /* not JSON */ }
    throw new ApiError(data.error || `Request failed (${response.status})`, response.status, data.fields);
  }
  if (raw) return response;
  if (response.status === 204) return null;
  return response.json();
}

export const api = {
  register: (data) => request('/api/auth/register', { method: 'POST', body: data }),
  login: (data) => request('/api/auth/login', { method: 'POST', body: data }),
  me: () => request('/api/auth/me'),
  updateProfile: (name) => request('/api/auth/me', { method: 'PATCH', body: { name } }),
  changePassword: (currentPassword, newPassword) =>
    request('/api/auth/me/password', { method: 'POST', body: { currentPassword, newPassword } }),
  deleteAccount: (password) => request('/api/auth/me', { method: 'DELETE', body: { password } }),
  systemStatus: () => request('/api/system/status'),

  projects: () => request('/api/projects'),
  project: (id) => request(`/api/projects/${id}`),
  analyzeGitHub: (data) => request('/api/projects/github', { method: 'POST', body: data }),
  analyzeZip: (file, name, depth) => {
    const form = new FormData();
    form.append('file', file);
    if (name) form.append('name', name);
    if (depth) form.append('depth', depth);
    return request('/api/projects/upload', { method: 'POST', form });
  },
  reanalyze: (id, token) => request(`/api/projects/${id}/reanalyze`, { method: 'POST', body: { token: token || null } }),
  deleteProject: (id) => request(`/api/projects/${id}`, { method: 'DELETE' }),

  graph: (id, kinds) => request(`/api/projects/${id}/graph${kinds ? `?kinds=${kinds.join(',')}` : ''}`),
  node: (id, nodeId) => request(`/api/projects/${id}/nodes/${nodeId}`),
  impact: (id, nodeId, depth = 3) => request(`/api/projects/${id}/nodes/${nodeId}/impact?depth=${depth}`),
  search: (id, q) => request(`/api/projects/${id}/search?q=${encodeURIComponent(q)}`),
  documents: (id) => request(`/api/projects/${id}/documents`),
  /** Streams a chat answer; onEvent receives {type: sources|waiting|token|done|error, ...}. */
  chat: async (id, body, onEvent, signal) => {
    const res = await request(`/api/projects/${id}/chat`, { method: 'POST', body, raw: true, signal });
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let newline;
      while ((newline = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, newline).trim();
        buffer = buffer.slice(newline + 1);
        if (line) onEvent(JSON.parse(line));
      }
    }
  },
  exportDocs: async (id) => {
    const res = await request(`/api/projects/${id}/documents/export`, { raw: true });
    const blob = await res.blob();
    const disposition = res.headers.get('Content-Disposition') || '';
    const match = disposition.match(/filename="?([^"]+)"?/);
    return { blob, fileName: match ? match[1] : 'documentation.zip' };
  },
};

export function downloadBlob(blob, fileName) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
