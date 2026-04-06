import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2 } from 'lucide-react';

/**
 * Handles the Google OAuth2 redirect.
 * URL looks like:
 *   /oauth2/callback?token=xxx&username=yyy&email=zzz&role=www
 *
 * Saves JWT + user to localStorage then does a hard redirect
 * (window.location.href instead of navigate) so React re-reads
 * localStorage fresh and the Protected wrapper sees the user.
 */
export default function OAuth2Callback() {
  const navigate = useNavigate();

  useEffect(() => {
    const params   = new URLSearchParams(window.location.search);
    const token    = params.get('token');
    const username = params.get('username');
    const email    = params.get('email');
    const role     = params.get('role');

    if (token && username) {
      // Save exactly the same shape useAuth.jsx expects
      const userData = { token, username, email, role };
      localStorage.setItem('dfs_token', token);
      localStorage.setItem('dfs_user',  JSON.stringify(userData));

      // Hard redirect so the entire React app re-mounts and
      // reads localStorage from scratch — fixes redirect loop
      window.location.href = '/';
    } else {
      // Missing params — go back to login
      navigate('/login', { replace: true });
    }
  }, [navigate]);

  return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <div className="flex flex-col items-center gap-3">
          <Loader2 size={32} className="text-cyan-400 animate-spin" />
          <p className="text-slate-400 text-sm">Signing you in with Google...</p>
        </div>
      </div>
  );
}