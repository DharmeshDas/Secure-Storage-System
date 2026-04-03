// src/pages/Dashboard.jsx
import { useState, useEffect, useCallback } from 'react';
import { useDropzone } from 'react-dropzone';
import { filesApi } from '../services/api';
import { useAuth } from '../hooks/useAuth.jsx';
import toast from 'react-hot-toast';
import {
  Upload, Download, Trash2, FolderOpen,
  FileText, Image, Film, Music, Archive,
  RefreshCw, AlertCircle, CheckCircle2
} from 'lucide-react';

// ── Helpers ───────────────────────────────────────────────────────────────────
const formatBytes = (bytes) => {
  if (bytes === 0) return '0 B';
  const k = 1024, sizes = ['B','KB','MB','GB','TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k,i)).toFixed(1))} ${sizes[i]}`;
};

const fileIcon = (mime = '') => {
  if (mime.startsWith('image/'))  return <Image  size={18} className="text-violet-400" />;
  if (mime.startsWith('video/'))  return <Film   size={18} className="text-blue-400"   />;
  if (mime.startsWith('audio/'))  return <Music  size={18} className="text-emerald-400"/>;
  if (mime.includes('zip') || mime.includes('tar')) return <Archive size={18} className="text-amber-400"/>;
  return <FileText size={18} className="text-slate-400" />;
};

const statusBadge = (status) => {
  const map = {
    COMPLETE:  { cls: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30', label: 'Ready'     },
    UPLOADING: { cls: 'bg-amber-500/15   text-amber-400   border-amber-500/30',   label: 'Uploading' },
    FAILED:    { cls: 'bg-red-500/15     text-red-400     border-red-500/30',      label: 'Failed'    },
  };
  const { cls, label } = map[status] || map.FAILED;
  return <span className={`text-xs px-2 py-0.5 rounded-full border font-medium ${cls}`}>{label}</span>;
};

// ── Upload row ────────────────────────────────────────────────────────────────
function UploadRow({ name, progress, done, error }) {
  return (
    <div className="flex items-center gap-3 bg-slate-800/60 border border-slate-700/50 rounded-lg px-4 py-3">
      <div className="flex-1 min-w-0">
        <p className="text-sm text-slate-200 truncate font-mono">{name}</p>
        <div className="mt-1.5 h-1.5 bg-slate-700 rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full transition-all duration-300 ${
              error ? 'bg-red-500' : done ? 'bg-emerald-500' : 'bg-cyan-500'
            }`}
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>
      <span className="text-xs tabular-nums w-10 text-right text-slate-400">
        {error ? '✗' : done ? <CheckCircle2 size={14} className="text-emerald-400 ml-auto" /> : `${progress}%`}
      </span>
    </div>
  );
}

// ── File row ──────────────────────────────────────────────────────────────────
function FileRow({ file, onDownload, onDelete }) {
  return (
    <tr className="border-b border-slate-800/60 hover:bg-slate-800/30 transition-colors group">
      <td className="py-3 px-4">
        <div className="flex items-center gap-2.5">
          {fileIcon(file.mimeType)}
          <span className="text-sm text-slate-200 font-medium truncate max-w-xs">
            {file.originalName}
          </span>
        </div>
      </td>
      <td className="py-3 px-4 text-sm text-slate-400 tabular-nums">
        {formatBytes(file.fileSize)}
      </td>
      <td className="py-3 px-4 text-sm text-slate-500 tabular-nums">
        {file.totalChunks} × 1 MB
      </td>
      <td className="py-3 px-4">
        {statusBadge(file.uploadStatus)}
      </td>
      <td className="py-3 px-4 text-sm text-slate-500">
        {new Date(file.createdAt).toLocaleDateString()}
      </td>
      <td className="py-3 px-4">
        <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          <button
            onClick={() => onDownload(file)}
            className="p-1.5 rounded hover:bg-slate-700 text-slate-400 hover:text-cyan-400 transition-colors"
            title="Download"
          >
            <Download size={15} />
          </button>
          <button
            onClick={() => onDelete(file)}
            className="p-1.5 rounded hover:bg-slate-700 text-slate-400 hover:text-red-400 transition-colors"
            title="Move to trash"
          >
            <Trash2 size={15} />
          </button>
        </div>
      </td>
    </tr>
  );
}

// ── Dashboard ─────────────────────────────────────────────────────────────────
export default function Dashboard() {
  const { user } = useAuth();
  const [files,   setFiles]   = useState([]);
  const [uploads, setUploads] = useState([]); // in-progress upload rows
  const [loading, setLoading] = useState(true);

  const fetchFiles = useCallback(async () => {
    try {
      const { data } = await filesApi.list();
      setFiles(data);
    } catch {
      toast.error('Failed to load files');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchFiles(); }, [fetchFiles]);

  // ── Dropzone upload ───────────────────────────────────────────────────────
  const onDrop = useCallback(async (accepted) => {
    for (const file of accepted) {
      const id = `${file.name}-${Date.now()}`;

      setUploads(prev => [...prev, { id, name: file.name, progress: 0, done: false, error: false }]);

      try {
        await filesApi.upload(file, (pct) => {
          setUploads(prev => prev.map(u => u.id === id ? { ...u, progress: pct } : u));
        });
        setUploads(prev => prev.map(u => u.id === id ? { ...u, progress: 100, done: true } : u));
        toast.success(`${file.name} uploaded`);
        fetchFiles();
      } catch (err) {
        setUploads(prev => prev.map(u => u.id === id ? { ...u, error: true } : u));
        toast.error(`Failed to upload ${file.name}`);
      }

      // Auto-clear finished/failed rows after 4 s
      setTimeout(() => setUploads(prev => prev.filter(u => u.id !== id)), 4000);
    }
  }, [fetchFiles]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    multiple: true,
  });

  // ── Actions ───────────────────────────────────────────────────────────────
  const handleDownload = async (file) => {
    try {
      await filesApi.download(file.id, file.originalName);
    } catch {
      toast.error('Download failed');
    }
  };

  const handleDelete = async (file) => {
    try {
      await filesApi.softDelete(file.id);
      setFiles(prev => prev.filter(f => f.id !== file.id));
      toast.success('Moved to trash');
    } catch {
      toast.error('Delete failed');
    }
  };

  // ── Storage usage bar ─────────────────────────────────────────────────────
  const usagePct = user
    ? Math.min(100, ((user.storageUsed || 0) / (user.storageQuota || 1)) * 100)
    : 0;

  return (
    <div className="space-y-6">

      {/* Storage usage */}
      <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-5">
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm font-medium text-slate-300">Storage Usage</span>
          <span className="text-xs text-slate-500 tabular-nums">
            {formatBytes(user?.storageUsed || 0)} / {formatBytes(user?.storageQuota || 0)}
          </span>
        </div>
        <div className="h-2 bg-slate-700 rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full transition-all duration-500 ${
              usagePct > 90 ? 'bg-red-500' : usagePct > 70 ? 'bg-amber-500' : 'bg-cyan-500'
            }`}
            style={{ width: `${usagePct}%` }}
          />
        </div>
      </div>

      {/* Drop zone */}
      <div
        {...getRootProps()}
        className={`
          border-2 border-dashed rounded-xl p-10 text-center cursor-pointer
          transition-all duration-200
          ${isDragActive
            ? 'border-cyan-500 bg-cyan-500/5'
            : 'border-slate-700 hover:border-slate-500 bg-slate-800/20'
          }
        `}
      >
        <input {...getInputProps()} />
        <Upload className="mx-auto mb-3 text-slate-500" size={32} />
        <p className="text-slate-300 font-medium">
          {isDragActive ? 'Drop files here…' : 'Drop files or click to upload'}
        </p>
        <p className="text-xs text-slate-500 mt-1">
          Files are split into 1 MB chunks and replicated across 2+ nodes
        </p>
      </div>

      {/* Active uploads */}
      {uploads.length > 0 && (
        <div className="space-y-2">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500">
            Uploading
          </h3>
          {uploads.map(u => (
            <UploadRow
              key={u.id}
              name={u.name}
              progress={u.progress}
              done={u.done}
              error={u.error}
            />
          ))}
        </div>
      )}

      {/* File table */}
      <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-700/50">
          <div className="flex items-center gap-2">
            <FolderOpen size={16} className="text-slate-400" />
            <h2 className="text-sm font-semibold text-slate-200">My Files</h2>
            <span className="text-xs text-slate-500 bg-slate-700/50 px-2 py-0.5 rounded-full">
              {files.length}
            </span>
          </div>
          <button
            onClick={fetchFiles}
            className="p-1.5 rounded hover:bg-slate-700 text-slate-500 hover:text-slate-300 transition-colors"
          >
            <RefreshCw size={14} />
          </button>
        </div>

        {loading ? (
          <div className="py-16 text-center text-slate-500">Loading…</div>
        ) : files.length === 0 ? (
          <div className="py-16 text-center text-slate-600">
            <FolderOpen size={36} className="mx-auto mb-3 opacity-30" />
            <p className="text-sm">No files yet. Upload something to get started.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-slate-700/50">
                  {['Name','Size','Chunks','Status','Uploaded',''].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {files.map(f => (
                  <FileRow
                    key={f.id}
                    file={f}
                    onDownload={handleDownload}
                    onDelete={handleDelete}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
