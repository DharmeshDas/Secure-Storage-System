// src/pages/Trash.jsx
import { useState, useEffect } from 'react';
import { filesApi } from '../services/api';
import toast from 'react-hot-toast';
import { Trash2, RotateCcw, AlertTriangle, X } from 'lucide-react';

const formatBytes = (b) => {
  if (!b) return '0 B';
  const k = 1024, s = ['B','KB','MB','GB'];
  const i = Math.floor(Math.log(b) / Math.log(k));
  return `${(b / Math.pow(k,i)).toFixed(1)} ${s[i]}`;
};

const daysLeft = (deletedAt) => {
  const expiry = new Date(deletedAt);
  expiry.setDate(expiry.getDate() + 30);
  const diff = Math.ceil((expiry - Date.now()) / 86400000);
  return diff > 0 ? diff : 0;
};

// ── Confirm dialog ────────────────────────────────────────────────────────────
function ConfirmDialog({ file, onConfirm, onCancel }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="bg-slate-800 border border-slate-700 rounded-2xl p-6 max-w-sm w-full mx-4 shadow-2xl">
        <div className="flex items-start gap-3 mb-4">
          <AlertTriangle className="text-red-400 mt-0.5 shrink-0" size={20} />
          <div>
            <h3 className="text-sm font-semibold text-slate-100 mb-1">Permanently delete?</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              <span className="text-slate-200 font-medium">{file.originalName}</span> will be
              removed from all storage nodes. This action cannot be undone.
            </p>
          </div>
        </div>
        <div className="flex gap-2 justify-end">
          <button
            onClick={onCancel}
            className="px-4 py-2 text-sm rounded-lg bg-slate-700 hover:bg-slate-600 text-slate-300 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            className="px-4 py-2 text-sm rounded-lg bg-red-600 hover:bg-red-500 text-white font-medium transition-colors"
          >
            Delete permanently
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Trash row ─────────────────────────────────────────────────────────────────
function TrashRow({ file, onRestore, onPermanentDelete }) {
  const days = daysLeft(file.deletedAt);
  const urgent = days <= 7;

  return (
    <div className="flex items-center gap-4 bg-slate-800/50 border border-slate-700/50 rounded-xl px-5 py-4 group hover:border-slate-600/60 transition-colors">
      <Trash2 size={16} className="text-slate-600 shrink-0" />

      <div className="flex-1 min-w-0">
        <p className="text-sm text-slate-200 font-medium truncate">{file.originalName}</p>
        <p className="text-xs text-slate-500 mt-0.5">
          {formatBytes(file.fileSize)} · Deleted {new Date(file.deletedAt).toLocaleDateString()}
        </p>
      </div>

      {/* Expiry countdown */}
      <div className={`text-xs font-medium px-2.5 py-1 rounded-full border ${
        urgent
          ? 'bg-red-500/10 text-red-400 border-red-500/30'
          : 'bg-slate-700/50 text-slate-400 border-slate-600/50'
      }`}>
        {days}d left
      </div>

      {/* Actions */}
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          onClick={() => onRestore(file)}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg bg-emerald-600/10 hover:bg-emerald-600/20 text-emerald-400 border border-emerald-600/20 transition-colors"
        >
          <RotateCcw size={12} />
          Restore
        </button>
        <button
          onClick={() => onPermanentDelete(file)}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg bg-red-600/10 hover:bg-red-600/20 text-red-400 border border-red-600/20 transition-colors"
        >
          <X size={12} />
          Delete
        </button>
      </div>
    </div>
  );
}

// ── Trash page ────────────────────────────────────────────────────────────────
export default function Trash() {
  const [files,   setFiles]   = useState([]);
  const [loading, setLoading] = useState(true);
  const [confirm, setConfirm] = useState(null); // file to confirm-delete

  useEffect(() => {
    filesApi.trash()
      .then(({ data }) => setFiles(data))
      .catch(() => toast.error('Failed to load trash'))
      .finally(() => setLoading(false));
  }, []);

  const handleRestore = async (file) => {
    try {
      await filesApi.restore(file.id);
      setFiles(prev => prev.filter(f => f.id !== file.id));
      toast.success(`${file.originalName} restored`);
    } catch {
      toast.error('Restore failed');
    }
  };

  const handlePermanentDelete = async () => {
    if (!confirm) return;
    try {
      await filesApi.permanentDelete(confirm.id);
      setFiles(prev => prev.filter(f => f.id !== confirm.id));
      toast.success('File permanently deleted');
    } catch {
      toast.error('Delete failed');
    } finally {
      setConfirm(null);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-sm font-semibold text-slate-200">Trash</h2>
          <p className="text-xs text-slate-500 mt-0.5">
            Files are permanently deleted 30 days after being trashed.
          </p>
        </div>
        <span className="text-xs text-slate-500 bg-slate-800/50 border border-slate-700/50 px-3 py-1.5 rounded-full">
          {files.length} item{files.length !== 1 ? 's' : ''}
        </span>
      </div>

      {loading ? (
        <div className="py-20 text-center text-slate-600 text-sm">Loading…</div>
      ) : files.length === 0 ? (
        <div className="py-24 text-center">
          <Trash2 size={40} className="mx-auto mb-3 text-slate-700" />
          <p className="text-slate-600 text-sm">Trash is empty</p>
        </div>
      ) : (
        <div className="space-y-2">
          {files.map(f => (
            <TrashRow
              key={f.id}
              file={f}
              onRestore={handleRestore}
              onPermanentDelete={() => setConfirm(f)}
            />
          ))}
        </div>
      )}

      {confirm && (
        <ConfirmDialog
          file={confirm}
          onConfirm={handlePermanentDelete}
          onCancel={() => setConfirm(null)}
        />
      )}
    </div>
  );
}
