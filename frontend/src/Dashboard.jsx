import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut, FileText, Plus, Clock, Edit } from 'lucide-react';
import './index.css';

function Dashboard() {
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [username, setUsername] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/login');
      return;
    }

    // Decode token to get username (simple payload extraction, not verification)
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      setUsername(payload.sub);
    } catch (e) {
      console.error('Could not decode token payload');
    }

    // TODO: Replace with actual backend fetch once API is ready
    // Mock data for now
    setTimeout(() => {
      setDocuments([
        { doc_id: '1', title: 'Project Alpha - Draft', last_updated_by: 'admin', last_updated_at: new Date().toISOString() },
        { doc_id: '2', title: 'Q3 Financial Report', last_updated_by: 'jane_doe', last_updated_at: new Date(Date.now() - 86400000).toISOString() }
      ]);
      setLoading(false);
    }, 500);

    /* Actual Implementation will look like this:
    const fetchDocuments = async () => {
      try {
        const response = await fetch('http://localhost:8080/api/documents', {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (response.ok) {
          const data = await response.json();
          setDocuments(data);
        } else if (response.status === 401) {
          localStorage.removeItem('token');
          navigate('/login');
        } else {
          setError('Failed to fetch documents');
        }
      } catch (err) {
        setError('Error connecting to the backend server.');
      } finally {
        setLoading(false);
      }
    };
    fetchDocuments();
    */
  }, [navigate]);

  const handleLogout = () => {
    localStorage.removeItem('token');
    navigate('/login');
  };

  if (loading) {
    return <div style={{ color: 'var(--text-main)', textAlign: 'center', marginTop: '20vh' }}>Loading Dashboard...</div>;
  }

  return (
    <div className="animate-fade-in" style={{ padding: '2rem', maxWidth: '1000px', margin: '0 auto' }}>
      
      {/* Header Area */}
      <div className="glass-panel" style={{ padding: '1.5rem 2rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: '700', color: 'var(--primary)', marginBottom: '0.25rem' }}>
            Document Version Control
          </h1>
          <p style={{ fontSize: '1rem', color: 'var(--text-muted)' }}>Welcome back, {username}</p>
        </div>
        <button 
          onClick={handleLogout}
          className="btn-primary" 
          style={{ width: 'auto', display: 'flex', alignItems: 'center', gap: '0.5rem', background: 'rgba(239, 68, 68, 0.1)', color: 'var(--error)' }}
        >
          <LogOut size={18} /> Logout
        </button>
      </div>

      {/* Main Content Area */}
      <div className="glass-panel" style={{ padding: '2rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
          <h2 style={{ fontSize: '1.25rem', fontWeight: '600' }}>Your Documents</h2>
          <button 
            onClick={() => alert("Will navigate to /editor/new once implemented")}
            className="btn-primary" 
            style={{ width: 'auto', display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.5rem 1rem' }}
          >
            <Plus size={18} /> New Document
          </button>
        </div>

        {error && (
          <div style={{ color: 'var(--error)', background: 'rgba(239, 68, 68, 0.1)', padding: '0.75rem', borderRadius: '8px', marginBottom: '1.5rem' }}>
            {error}
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {documents.length === 0 ? (
            <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '2rem 0' }}>No documents found. Create one to get started!</p>
          ) : (
            documents.map(doc => (
              <div key={doc.doc_id} style={{ 
                background: 'rgba(255, 255, 255, 0.03)', 
                border: '1px solid rgba(255, 255, 255, 0.05)',
                borderRadius: '8px',
                padding: '1.25rem',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                transition: 'all 0.2s ease',
              }} className="document-card">
                <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                  <div style={{ padding: '0.75rem', background: 'rgba(59, 130, 246, 0.1)', borderRadius: '8px', color: 'var(--primary)' }}>
                    <FileText size={24} />
                  </div>
                  <div>
                    <h3 style={{ fontSize: '1.1rem', fontWeight: '500', marginBottom: '0.25rem' }}>{doc.title}</h3>
                    <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                      Last updated by <span style={{ color: 'var(--text-main)' }}>{doc.last_updated_by}</span> on {new Date(doc.last_updated_at).toLocaleDateString()}
                    </p>
                  </div>
                </div>
                <div style={{ display: 'flex', gap: '0.75rem' }}>
                   <button 
                    onClick={() => alert(`Will open history for ${doc.doc_id}`)}
                    style={{ background: 'transparent', border: '1px solid rgba(255,255,255,0.1)', color: 'var(--text-muted)', padding: '0.5rem', borderRadius: '6px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '0.5rem', transition: 'all 0.2s ease' }}
                    title="View History"
                  >
                    <Clock size={18} />
                  </button>
                  <button 
                    onClick={() => alert(`Will open editor for ${doc.doc_id}`)}
                    style={{ background: 'rgba(59, 130, 246, 0.1)', border: 'none', color: 'var(--primary)', padding: '0.5rem 1rem', borderRadius: '6px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: '500', transition: 'all 0.2s ease' }}
                  >
                    <Edit size={18} /> Edit
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

export default Dashboard;
