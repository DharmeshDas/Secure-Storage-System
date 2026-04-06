import { createContext, useContext, useState, useEffect } from 'react';
import { authApi } from '../services/api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user,    setUser]    = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    try {
      const token = localStorage.getItem('dfs_token');
      const saved = localStorage.getItem('dfs_user');
      if (token && saved) {
        const parsed = JSON.parse(saved);
        setUser(parsed);
      }
    } catch (e) {
      localStorage.removeItem('dfs_token');
      localStorage.removeItem('dfs_user');
    } finally {
      setLoading(false);
    }
  }, []);

  const login = async (username, password) => {
    const { data } = await authApi.login({ username, password });
    localStorage.setItem('dfs_token', data.token);
    localStorage.setItem('dfs_user',  JSON.stringify(data));
    setUser(data);
    return data;
  };

  const register = async (username, email, password) => {
    const { data } = await authApi.register({ username, email, password });
    localStorage.setItem('dfs_token', data.token);
    localStorage.setItem('dfs_user',  JSON.stringify(data));
    setUser(data);
    return data;
  };

  const logout = () => {
    localStorage.removeItem('dfs_token');
    localStorage.removeItem('dfs_user');
    setUser(null);
  };

  return (
      <AuthContext.Provider value={{ user, login, register, logout, loading }}>
        {children}
      </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);