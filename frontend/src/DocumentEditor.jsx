import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Save, Loader2, Edit3, Eye } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import './index.css';

function DocumentEditor() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isNew = id === 'new';

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (isNew) return;

    const fetchDocument = async () => {
      const token = localStorage.getItem('token');
      try {
        const response = await fetch(`http://localhost:8080/api/documents/${id}`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        
        if (response.ok) {
          const data = await response.json();
          setTitle(data.title);
          setContent(data.content);
        } else {
          setError('Failed to load document. It may have been deleted.');
        }
      } catch (err) {
        setError('Error connecting to server.');
      } finally {
        setLoading(false);
      }
    };

    fetchDocument();
  }, [id, isNew]);

  const handleSave = async () => {
    if (!title.trim() || !content.trim()) {
      setError('Title and content are required.');
      return;
    }

    setSaving(true);
    setError('');
    const token = localStorage.getItem('token');
    
    const url = isNew 
      ? 'http://localhost:8080/api/documents' 
      : `http://localhost:8080/api/documents/${id}`;
      
    const method = isNew ? 'POST' : 'PUT';

    try {
      const response = await fetch(url, {
        method,
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ title, content })
      });

      if (response.ok) {
        navigate('/dashboard');
      } else {
        const data = await response.json();
        setError(data.error || 'Failed to save document.');
      }
    } catch (err) {
      setError('Error connecting to server while saving.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div style={{ textAlign: 'center', marginTop: '20vh', color: 'var(--text-main)' }}>Loading document...</div>;
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
        
        <button 
          onClick={handleSave}
          disabled={saving}
          className="btn-primary"
          style={{ width: 'auto', display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.5rem 1.5rem', opacity: saving ? 0.7 : 1 }}
        >
          {saving ? <Loader2 size={18} className="animate-spin" /> : <Save size={18} />}
          {saving ? 'Saving...' : 'Save Document'}
        </button>
      </div>

      {error && (
        <div style={{ color: 'var(--error)', background: 'rgba(239, 68, 68, 0.1)', padding: '1rem', borderRadius: '8px', marginBottom: '1.5rem' }}>
          {error}
        </div>
      )}

      {/* Editor Area */}
      <div style={{ display: 'flex', gap: '1.5rem', height: '600px' }}>
        
        {/* Left: Input */}
        <div className="glass-panel" style={{ flex: 1, padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--text-muted)', marginBottom: '0.5rem' }}>
             <Edit3 size={18} />
             <span style={{ fontSize: '0.9rem', fontWeight: '500' }}>Markdown Editor</span>
          </div>
          
          <div>
            <input 
              type="text" 
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Document Title"
              style={{ 
                width: '100%', padding: '0.75rem', 
                background: 'rgba(0,0,0,0.2)', border: '1px solid rgba(255,255,255,0.1)', 
                borderRadius: '8px', color: 'white', fontSize: '1.25rem', fontWeight: '500' 
              }}
            />
          </div>
          
          <textarea 
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="Use Markdown to format your text (e.g. # Heading, **bold**)..."
            style={{ 
              width: '100%', flexGrow: 1, padding: '1rem', 
              background: 'rgba(0,0,0,0.2)', border: '1px solid rgba(255,255,255,0.1)', 
              borderRadius: '8px', color: 'white', fontSize: '1rem', fontFamily: 'monospace',
              resize: 'none', lineHeight: '1.5'
            }}
          />
        </div>

        {/* Right: Live Preview */}
        <div className="glass-panel" style={{ flex: 1, padding: '1.5rem', display: 'flex', flexDirection: 'column', overflowY: 'auto' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--primary)', marginBottom: '1.5rem', borderBottom: '1px solid rgba(255,255,255,0.1)', paddingBottom: '1rem' }}>
             <Eye size={18} />
             <span style={{ fontSize: '0.9rem', fontWeight: '500' }}>Live Preview</span>
          </div>

          <div style={{ padding: '0 0.5rem' }} className="markdown-preview">
            <h1 style={{ fontSize: '2rem', marginBottom: '1rem', color: 'var(--text-main)' }}>
              {title || <span style={{ color: 'var(--text-muted)' }}>Untitled Document</span>}
            </h1>
            
            {content ? (
              <ReactMarkdown>{content}</ReactMarkdown>
            ) : (
              <p style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>Start typing on the left to see the preview here...</p>
            )}
          </div>
        </div>

      </div>
    </div>
  );
}

export default DocumentEditor;
