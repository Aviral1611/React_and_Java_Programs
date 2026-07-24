import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Clock } from 'lucide-react';
import './index.css';

function DocumentHistory() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchHistory = async () => {
      const token = localStorage.getItem('token');
      try {
        const response = await fetch(`http://localhost:8080/api/documents/${id}/history`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        
        if (response.ok) {
          const data = await response.json();
          setHistory(data);
        } else {
          setError('Failed to load document history.');
        }
      } catch (err) {
        setError('Error connecting to server.');
      } finally {
        setLoading(false);
      }
    };

    fetchHistory();
  }, [id]);

  if (loading) {
    return <div style={{ textAlign: 'center', marginTop: '20vh', color: 'var(--text-main)' }}>Loading history...</div>;
  }

  return (
    <div className="animate-fade-in" style={{ padding: '2rem', maxWidth: '800px', margin: '0 auto' }}>
      
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <button 
          onClick={() => navigate('/dashboard')}
          style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontSize: '1rem' }}
        >
          <ArrowLeft size={20} /> Back to Dashboard
        </button>
      </div>

      {error && (
        <div style={{ color: 'var(--error)', background: 'rgba(239, 68, 68, 0.1)', padding: '1rem', borderRadius: '8px', marginBottom: '1.5rem' }}>
          {error}
        </div>
      )}

      {/* History Timeline */}
      <div className="glass-panel" style={{ padding: '2rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '2rem' }}>
          <Clock size={24} color="var(--primary)" />
          <h2 style={{ fontSize: '1.5rem', fontWeight: '600' }}>Version History</h2>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', position: 'relative' }}>
          {/* Vertical line connecting timeline items */}
          {history.length > 1 && (
             <div style={{ position: 'absolute', left: '11px', top: '24px', bottom: '24px', width: '2px', background: 'rgba(255,255,255,0.1)' }}></div>
          )}

          {history.length === 0 ? (
            <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '2rem 0' }}>No previous edits found. This is the first version.</p>
          ) : (
            history.map((entry, index) => (
              <div key={index} style={{ display: 'flex', gap: '1.5rem', position: 'relative', zIndex: 1 }}>
                <div style={{ 
                  minWidth: '24px', height: '24px', 
                  background: 'var(--background)', border: '2px solid var(--primary)', 
                  borderRadius: '50%', marginTop: '4px' 
                }}></div>
                
                <div style={{ 
                  background: 'rgba(255, 255, 255, 0.03)', 
                  border: '1px solid rgba(255, 255, 255, 0.05)',
                  borderRadius: '8px',
                  padding: '1.25rem',
                  flexGrow: 1
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem', marginBottom: '0.75rem' }}>
                    <h3 style={{ fontSize: '1.1rem', fontWeight: '500', margin: 0 }}>
                      {entry.version_number ? `[v${entry.version_number}] ` : ''}{entry.old_title}
                    </h3>
                    <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                      {new Date(entry.changed_at).toLocaleString()}
                    </span>
                  </div>
                  
                  {/* Display the old content that was missing */}
                  <div style={{ 
                    background: 'rgba(0, 0, 0, 0.2)', 
                    padding: '1rem', 
                    borderRadius: '6px', 
                    marginBottom: '1rem',
                    fontSize: '0.95rem',
                    color: 'var(--text-main)',
                    whiteSpace: 'pre-wrap'
                  }}>
                    {entry.old_content}
                  </div>

                  <p style={{ fontSize: '0.9rem', color: 'var(--text-muted)', margin: 0 }}>
                    Saved by <span style={{ color: 'var(--text-main)', fontWeight: '500' }}>{entry.changed_by}</span>
                  </p>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

export default DocumentHistory;
