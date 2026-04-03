import axios from 'axios';

// In dev: uses Vite proxy (empty string = relative URLs → proxied to localhost:8080)
// In production: uses the Render gateway URL set via VITE_API_URL at build time
const BASE_URL = import.meta.env.VITE_API_URL || '';

const api = axios.create({ baseURL: BASE_URL });

// ── Inject JWT on every request ───────────────────────────────────────────────
api.interceptors.request.use(config => {
  const token = localStorage.getItem('dfs_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// ── Auto-logout on 401 ────────────────────────────────────────────────────────
api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('dfs_token');
      localStorage.removeItem('dfs_user');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

// ── Auth ──────────────────────────────────────────────────────────────────────
export const authApi = {
  login:    (data) => api.post('/api/auth/login',    data),
  register: (data) => api.post('/api/auth/register', data),
};

// ── Files ─────────────────────────────────────────────────────────────────────
export const filesApi = {
  list:  () => api.get('/api/files'),
  trash: () => api.get('/api/files/trash'),

  upload: (file, onProgress) => {
    const form = new FormData();
    form.append('file', file);
    return api.post('/api/files/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: e => {
        if (onProgress && e.total) {
          onProgress(Math.round((e.loaded / e.total) * 100));
        }
      },
    });
  },

  download: async (id, filename) => {
    const res = await api.get(`/api/files/${id}/download`, { responseType: 'blob' });
    const url = URL.createObjectURL(res.data);
    const a   = document.createElement('a');
    a.href = url; a.download = filename; a.click();
    URL.revokeObjectURL(url);
  },

  softDelete:      (id) => api.delete(`/api/files/${id}`),
  restore:         (id) => api.post(`/api/files/${id}/restore`),
  permanentDelete: (id) => api.delete(`/api/files/${id}/permanent`),
};

export const nodesApi = {
  list: () => api.get('/api/nodes'),
};

export default api;
