import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import './index.css';

function Hello() {
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/login');
      return;
    }

    const fetchHello = async () => {
      try {
        const response = await fetch('http://localhost:8080/api/hello', {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        });
        
        if (response.ok) {
          const data = await response.json();
          setMessage(data.message);
        } else {
          // Token invalid or expired
          localStorage.removeItem('token');
          navigate('/login');
        }
      } catch (err) {
        setMessage('Error connecting to the backend server.');
      } finally {
        setLoading(false);
      }
    };

    fetchHello();
  }, [navigate]);

  const handleLogout = () => {
    localStorage.removeItem('token');
    navigate('/login');
  };

  if (loading) {
    return <div style={{ color: 'var(--text-main)', textAlign: 'center', marginTop: '20vh' }}>Loading Dashboard...</div>;
  }

  return (
    <div className="animate-fade-in" style={{ padding: '2rem', maxWidth: '800px', margin: '0 auto' }}>
      <div className="glass-panel" style={{ padding: '2rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 style={{ fontSize: '2rem', fontWeight: '700', color: 'var(--primary)', marginBottom: '0.5rem' }}>Dashboard</h1>
          <p style={{ fontSize: '1.2rem', color: 'var(--text-main)' }}>{message}</p>
        </div>
        <button 
          onClick={handleLogout}
          className="btn-primary" 
          style={{ width: 'auto', display: 'flex', alignItems: 'center', gap: '0.5rem', background: 'rgba(239, 68, 68, 0.2)', color: 'var(--error)' }}
        >
          <LogOut size={18} /> Logout
        </button>
      </div>
    </div>
  );
}

export default Hello;
