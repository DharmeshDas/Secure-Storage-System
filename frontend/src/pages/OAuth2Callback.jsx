import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2 } from 'lucide-react';

/**
 * Landing page for Google OAuth2 redirect.
 *
 * After Google login, the backend redirects here:
 *   http://localhost:3000/oauth2/callback?token=xxx&username=yyy&email=zzz&role=www
 *
 * This page reads those params, saves them to localStorage
 * (matching the format useAuth.jsx expects), then redirects to /.
 */
export default function OAuth2Callback() {
  const navigate = useNavigate();

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const token    = params.get('token');
    const username = params.get('username');
    const email    = params.get('email');
    const role     = params.get('role');

    if (token && username) {
      // Store in the same format useAuth.jsx uses
      localStorage.setItem('dfs_token', token);
      localStorage.setItem('dfs_user', JSON.stringify({ token, username, email, role }));
      navigate('/', { replace: true });
    } else {
      // Something went wrong — go back to login
      navigate('/login', { replace: true });
    }
  }, [navigate]);

  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center">
      <div className="flex flex-col items-center gap-3">
        <Loader2 size={32} className="text-cyan-400 animate-spin" />
        <p className="text-slate-400 text-sm">Signing you in with Google…</p>
      </div>
    </div>
  );
}
