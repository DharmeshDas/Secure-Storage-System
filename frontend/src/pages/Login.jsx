import { useState } from 'react';
import { useNavigate, Link, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth.jsx';
import toast from 'react-hot-toast';
import { HardDrive, Loader2 } from 'lucide-react';

// Google "G" SVG logo
function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 48 48">
      <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
      <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
      <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
      <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
    </svg>
  );
}

export default function Login() {
  const { login, register } = useAuth();
  const navigate  = useNavigate();
  const location  = useLocation();
  const isRegister = location.pathname === '/register';

  const [form, setForm] = useState({ username: '', email: '', password: '' });
  const [busy, setBusy]  = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      if (isRegister) {
        if (!form.email)              { toast.error('Email is required'); setBusy(false); return; }
        if (form.password.length < 8) { toast.error('Password must be at least 8 characters'); setBusy(false); return; }
        await register(form.username, form.email, form.password);
        toast.success('Account created! Welcome.');
      } else {
        await login(form.username, form.password);
      }
      navigate('/');
    } catch (err) {
      const msg = err.response?.data?.error
               || err.response?.data?.message
               || (isRegister ? 'Registration failed' : 'Invalid credentials');
      toast.error(msg);
    } finally {
      setBusy(false);
    }
  };

  // Redirect the browser to the Spring OAuth2 authorization endpoint.
  // Spring Security will forward to Google's login page.
  const handleGoogleLogin = () => {
    window.location.href = 'http://localhost:8080/oauth2/authorization/google';
  };

  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center px-4">
      <div className="w-full max-w-sm">

        {/* Logo */}
        <div className="flex items-center justify-center gap-2.5 mb-8">
          <div className="p-2 rounded-xl bg-cyan-500/10 border border-cyan-500/20">
            <HardDrive size={22} className="text-cyan-400" />
          </div>
          <span className="text-xl font-bold text-slate-100 tracking-tight">DistFS</span>
        </div>

        {/* Card */}
        <div className="bg-slate-800/50 border border-slate-700/50 rounded-2xl p-8 shadow-xl">
          <h1 className="text-lg font-semibold text-slate-100 mb-1">
            {isRegister ? 'Create account' : 'Sign in'}
          </h1>
          <p className="text-sm text-slate-500 mb-6">
            {isRegister ? 'Set up your distributed storage account' : 'Access your distributed storage'}
          </p>

          {/* ── Google button ── */}
          <button
            type="button"
            onClick={handleGoogleLogin}
            className="w-full flex items-center justify-center gap-3 py-2.5 px-4 rounded-lg bg-white hover:bg-gray-50 border border-gray-300 text-gray-700 text-sm font-medium transition-colors mb-5 shadow-sm"
          >
            <GoogleIcon />
            {isRegister ? 'Sign up with Google' : 'Sign in with Google'}
          </button>

          {/* ── Divider ── */}
          <div className="flex items-center gap-3 mb-5">
            <div className="flex-1 h-px bg-slate-700" />
            <span className="text-xs text-slate-500">or continue with email</span>
            <div className="flex-1 h-px bg-slate-700" />
          </div>

          {/* ── Email/password form ── */}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1.5">Username</label>
              <input
                type="text"
                required
                value={form.username}
                onChange={e => setForm(p => ({ ...p, username: e.target.value }))}
                className="w-full bg-slate-900/60 border border-slate-700 rounded-lg px-3.5 py-2.5 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/30 transition-colors"
                placeholder="your_username"
              />
            </div>

            {isRegister && (
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">Email</label>
                <input
                  type="email"
                  required
                  value={form.email}
                  onChange={e => setForm(p => ({ ...p, email: e.target.value }))}
                  className="w-full bg-slate-900/60 border border-slate-700 rounded-lg px-3.5 py-2.5 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/30 transition-colors"
                  placeholder="you@example.com"
                />
              </div>
            )}

            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1.5">Password</label>
              <input
                type="password"
                required
                value={form.password}
                onChange={e => setForm(p => ({ ...p, password: e.target.value }))}
                className="w-full bg-slate-900/60 border border-slate-700 rounded-lg px-3.5 py-2.5 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/30 transition-colors"
                placeholder={isRegister ? 'Min. 8 characters' : '••••••••'}
              />
            </div>

            <button
              type="submit"
              disabled={busy}
              className="w-full py-2.5 rounded-lg bg-cyan-600 hover:bg-cyan-500 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-semibold transition-colors flex items-center justify-center gap-2"
            >
              {busy && <Loader2 size={15} className="animate-spin" />}
              {busy
                ? (isRegister ? 'Creating...' : 'Signing in…')
                : (isRegister ? 'Create account' : 'Sign in')}
            </button>
          </form>

          {/* Toggle */}
          <p className="mt-5 text-center text-xs text-slate-500">
            {isRegister ? (
              <>Already have an account?{' '}
                <Link to="/login" className="text-cyan-400 hover:text-cyan-300 transition-colors">Sign in</Link>
              </>
            ) : (
              <>No account?{' '}
                <Link to="/register" className="text-cyan-400 hover:text-cyan-300 transition-colors">Create one</Link>
              </>
            )}
          </p>
        </div>
      </div>
    </div>
  );
}
