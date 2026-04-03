import { BrowserRouter, Routes, Route, Navigate, NavLink } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider, useAuth } from './hooks/useAuth.jsx';
import Dashboard    from './pages/Dashboard.jsx';
import Trash        from './pages/Trash.jsx';
import Login        from './pages/Login.jsx';
import OAuth2Callback from './pages/OAuth2Callback.jsx';
import {
  HardDrive, LayoutDashboard, Trash2,
  LogOut, User, Server
} from 'lucide-react';

function Protected({ children }) {
  const { user, loading } = useAuth();
  if (loading) return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center">
      <div className="w-5 h-5 border-2 border-cyan-500 border-t-transparent rounded-full animate-spin" />
    </div>
  );
  return user ? children : <Navigate to="/login" replace />;
}

function SideLink({ to, icon: Icon, label }) {
  return (
    <NavLink
      to={to}
      end
      className={({ isActive }) =>
        `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
          isActive
            ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20'
            : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
        }`
      }
    >
      <Icon size={16} />
      {label}
    </NavLink>
  );
}

function AppShell() {
  const { user, logout } = useAuth();
  return (
    <div className="min-h-screen bg-slate-950 flex">
      <aside className="w-56 shrink-0 border-r border-slate-800/60 flex flex-col">
        <div className="flex items-center gap-2.5 px-4 h-16 border-b border-slate-800/60">
          <div className="p-1.5 rounded-lg bg-cyan-500/10 border border-cyan-500/20">
            <HardDrive size={16} className="text-cyan-400" />
          </div>
          <span className="font-bold text-slate-100 tracking-tight">DistFS</span>
        </div>
        <nav className="flex-1 px-3 py-4 space-y-1">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-600 px-3 mb-2">Storage</p>
          <SideLink to="/"      icon={LayoutDashboard} label="My Files" />
          <SideLink to="/trash" icon={Trash2}           label="Trash"    />
        </nav>
        <div className="border-t border-slate-800/60 p-3 space-y-1">
          <div className="flex items-center gap-2.5 px-3 py-2 rounded-lg bg-slate-800/40">
            <div className="w-7 h-7 rounded-full bg-cyan-500/20 border border-cyan-500/30 flex items-center justify-center">
              <User size={13} className="text-cyan-400" />
            </div>
            <div className="min-w-0">
              <p className="text-xs font-medium text-slate-200 truncate">{user?.username}</p>
              <p className="text-xs text-slate-500 truncate">{user?.email || user?.role?.replace('ROLE_','')}</p>
            </div>
          </div>
          <button
            onClick={logout}
            className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm text-slate-500 hover:text-slate-300 hover:bg-slate-800/60 transition-colors"
          >
            <LogOut size={14} />
            Sign out
          </button>
        </div>
      </aside>
      <main className="flex-1 min-w-0 flex flex-col">
        <header className="h-16 border-b border-slate-800/60 flex items-center px-6 gap-3">
          <Server size={14} className="text-slate-600" />
          <span className="text-xs text-slate-600">Replication Factor: 2 · Chunk Size: 1 MB</span>
        </header>
        <div className="flex-1 p-6 overflow-y-auto">
          <Routes>
            <Route path="/"      element={<Dashboard />} />
            <Route path="/trash" element={<Trash />}     />
          </Routes>
        </div>
      </main>
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Toaster
          position="bottom-right"
          toastOptions={{
            style: {
              background: '#1e293b',
              color: '#e2e8f0',
              border: '1px solid rgba(148,163,184,0.15)',
              fontSize: '13px',
            },
          }}
        />
        <Routes>
          <Route path="/login"           element={<Login />} />
          <Route path="/register"        element={<Login />} />
          <Route path="/oauth2/callback" element={<OAuth2Callback />} />
          <Route path="/*" element={
            <Protected>
              <AppShell />
            </Protected>
          } />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
