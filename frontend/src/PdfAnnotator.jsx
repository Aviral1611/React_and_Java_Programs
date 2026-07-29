import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Save, MessageSquare, Highlighter, Loader2, MousePointer } from 'lucide-react';
import * as pdfjsLib from 'pdfjs-dist';
import './index.css';

// Set the worker source for pdf.js
pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.mjs',
  import.meta.url
).toString();

function PdfAnnotator() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [pdfDoc, setPdfDoc] = useState(null);
  const [numPages, setNumPages] = useState(0);
  const [scale, setScale] = useState(1.5);
  const [annotations, setAnnotations] = useState([]);
  const [mode, setMode] = useState('select'); // 'select', 'comment', 'highlight'
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [commentPopup, setCommentPopup] = useState(null); // {page, x, y}
  const [commentText, setCommentText] = useState('');
  const [highlightStart, setHighlightStart] = useState(null);

  const canvasContainerRef = useRef(null);
  const pageCanvasRefs = useRef({});
  const overlayRefs = useRef({});
  const renderTasks = useRef({});

  // Load the PDF
  useEffect(() => {
    const loadPdf = async () => {
      const token = localStorage.getItem('token');
      try {
        const response = await fetch(`http://localhost:8080/api/documents/${id}/download`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!response.ok) {
          setError('Failed to load PDF.');
          setLoading(false);
          return;
        }
        const blob = await response.blob();
        const arrayBuffer = await blob.arrayBuffer();
        const pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;
        setPdfDoc(pdf);
        setNumPages(pdf.numPages);
        setLoading(false);
      } catch (err) {
        setError('Error loading PDF: ' + err.message);
        setLoading(false);
      }
    };
    loadPdf();
  }, [id]);

  // Render all pages when PDF is loaded
  useEffect(() => {
    if (!pdfDoc) return;

    const renderPages = async () => {
      for (let pageNum = 1; pageNum <= pdfDoc.numPages; pageNum++) {
        const page = await pdfDoc.getPage(pageNum);
        const viewport = page.getViewport({ scale });
        const canvas = pageCanvasRefs.current[pageNum];
        if (!canvas) continue;

        canvas.width = viewport.width;
        canvas.height = viewport.height;

        const ctx = canvas.getContext('2d');
        
        if (renderTasks.current[pageNum]) {
          renderTasks.current[pageNum].cancel();
        }
        
        const renderTask = page.render({ canvasContext: ctx, viewport });
        renderTasks.current[pageNum] = renderTask;
        
        try {
          await renderTask.promise;
        } catch (err) {
          // Ignore cancelled renders
        }

        // Size the overlay to match
        const overlay = overlayRefs.current[pageNum];
        if (overlay) {
          overlay.style.width = viewport.width + 'px';
          overlay.style.height = viewport.height + 'px';
        }
      }
    };
    renderPages();
  }, [pdfDoc, scale]);

  // Handle clicks on the PDF overlay
  const handleOverlayClick = (e, pageNum) => {
    if (mode === 'select') return;

    const overlay = overlayRefs.current[pageNum];
    const rect = overlay.getBoundingClientRect();
    const x = (e.clientX - rect.left) / scale;
    const y = (e.clientY - rect.top) / scale;

    if (mode === 'comment') {
      setCommentPopup({ page: pageNum, x, y, screenX: e.clientX, screenY: e.clientY });
      setCommentText('');
    }
  };

  // Handle highlight drawing
  const handleOverlayMouseDown = (e, pageNum) => {
    if (mode !== 'highlight') return;
    const overlay = overlayRefs.current[pageNum];
    const rect = overlay.getBoundingClientRect();
    const x = (e.clientX - rect.left) / scale;
    const y = (e.clientY - rect.top) / scale;
    setHighlightStart({ page: pageNum, x, y });
  };

  const handleOverlayMouseUp = (e, pageNum) => {
    if (mode !== 'highlight' || !highlightStart || highlightStart.page !== pageNum) return;
    const overlay = overlayRefs.current[pageNum];
    const rect = overlay.getBoundingClientRect();
    const endX = (e.clientX - rect.left) / scale;
    const endY = (e.clientY - rect.top) / scale;

    const width = Math.abs(endX - highlightStart.x);
    const height = Math.abs(endY - highlightStart.y);

    if (width > 5 && height > 3) { // Minimum size to avoid accidental clicks
      setAnnotations(prev => [...prev, {
        type: 'highlight',
        page: pageNum,
        x: Math.min(highlightStart.x, endX),
        y: Math.min(highlightStart.y, endY),
        width,
        height
      }]);
    }
    setHighlightStart(null);
  };

  // Save a comment
  const saveComment = () => {
    if (!commentText.trim() || !commentPopup) return;
    setAnnotations(prev => [...prev, {
      type: 'comment',
      page: commentPopup.page,
      x: commentPopup.x,
      y: commentPopup.y,
      text: commentText.trim()
    }]);
    setCommentPopup(null);
    setCommentText('');
  };

  // Save annotations to backend
  const handleSave = async () => {
    if (annotations.length === 0) {
      setError('Add at least one annotation before saving.');
      return;
    }

    setSaving(true);
    setError('');
    const token = localStorage.getItem('token');

    try {
      const response = await fetch(`http://localhost:8080/api/documents/${id}/annotate`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ annotations })
      });

      if (response.ok) {
        navigate('/dashboard');
      } else {
        const data = await response.json();
        setError(data.error || 'Failed to save annotations.');
      }
    } catch (err) {
      setError('Error connecting to server.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div style={{ textAlign: 'center', marginTop: '20vh', color: 'var(--text-main)' }}>Loading PDF...</div>;
  }

  return (
    <div className="animate-fade-in" style={{ padding: '1rem', maxWidth: '1200px', margin: '0 auto' }}>
      
      {/* Top Bar */}
      <div className="glass-panel" style={{ padding: '1rem 1.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <button 
          onClick={() => navigate('/dashboard')}
          style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontSize: '0.95rem' }}
        >
          <ArrowLeft size={18} /> Back
        </button>

        {/* Toolbar */}
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button
            onClick={() => setMode('select')}
            style={{
              padding: '0.5rem 1rem', borderRadius: '6px', cursor: 'pointer',
              display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.85rem',
              background: mode === 'select' ? 'rgba(59, 130, 246, 0.2)' : 'transparent',
              border: mode === 'select' ? '1px solid var(--primary)' : '1px solid rgba(255,255,255,0.1)',
              color: mode === 'select' ? 'var(--primary)' : 'var(--text-muted)'
            }}
          >
            <MousePointer size={16} /> Select
          </button>
          <button
            onClick={() => setMode('comment')}
            style={{
              padding: '0.5rem 1rem', borderRadius: '6px', cursor: 'pointer',
              display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.85rem',
              background: mode === 'comment' ? 'rgba(246, 173, 59, 0.2)' : 'transparent',
              border: mode === 'comment' ? '1px solid #f6ad3b' : '1px solid rgba(255,255,255,0.1)',
              color: mode === 'comment' ? '#f6ad3b' : 'var(--text-muted)'
            }}
          >
            <MessageSquare size={16} /> Comment
          </button>
          <button
            onClick={() => setMode('highlight')}
            style={{
              padding: '0.5rem 1rem', borderRadius: '6px', cursor: 'pointer',
              display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.85rem',
              background: mode === 'highlight' ? 'rgba(250, 204, 21, 0.2)' : 'transparent',
              border: mode === 'highlight' ? '1px solid #facc15' : '1px solid rgba(255,255,255,0.1)',
              color: mode === 'highlight' ? '#facc15' : 'var(--text-muted)'
            }}
          >
            <Highlighter size={16} /> Highlight
          </button>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            {annotations.length} annotation{annotations.length !== 1 ? 's' : ''}
          </span>
          <button 
            onClick={handleSave}
            disabled={saving}
            className="btn-primary"
            style={{ width: 'auto', display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.5rem 1.25rem', opacity: saving ? 0.7 : 1 }}
          >
            {saving ? <Loader2 size={16} /> : <Save size={16} />}
            {saving ? 'Saving...' : 'Save Annotations'}
          </button>
        </div>
      </div>

      {error && (
        <div style={{ color: 'var(--error)', background: 'rgba(239, 68, 68, 0.1)', padding: '0.75rem', borderRadius: '8px', marginBottom: '1rem' }}>
          {error}
        </div>
      )}

      {/* PDF Viewer */}
      <div ref={canvasContainerRef} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '1rem' }}>
        {Array.from({ length: numPages }, (_, i) => i + 1).map(pageNum => (
          <div key={pageNum} style={{ position: 'relative', boxShadow: '0 4px 20px rgba(0,0,0,0.4)', borderRadius: '4px', overflow: 'hidden' }}>
            <canvas ref={el => pageCanvasRefs.current[pageNum] = el} />
            
            {/* Annotation overlay */}
            <div 
              ref={el => overlayRefs.current[pageNum] = el}
              onClick={(e) => handleOverlayClick(e, pageNum)}
              onMouseDown={(e) => handleOverlayMouseDown(e, pageNum)}
              onMouseUp={(e) => handleOverlayMouseUp(e, pageNum)}
              style={{ 
                position: 'absolute', top: 0, left: 0, 
                cursor: mode === 'comment' ? 'crosshair' : mode === 'highlight' ? 'crosshair' : 'default',
                pointerEvents: mode === 'select' ? 'none' : 'auto'
              }}
            >
              {/* Render annotation markers for this page */}
              {annotations.filter(a => a.page === pageNum).map((ann, idx) => {
                if (ann.type === 'comment') {
                  return (
                    <div key={idx} title={ann.text} style={{
                      position: 'absolute',
                      left: ann.x * scale - 10,
                      top: ann.y * scale - 10,
                      width: '20px', height: '20px',
                      background: '#f6ad3b',
                      borderRadius: '50%',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: '12px', color: '#000', fontWeight: 'bold',
                      boxShadow: '0 2px 6px rgba(0,0,0,0.3)',
                      cursor: 'default', pointerEvents: 'none'
                    }}>
                      💬
                    </div>
                  );
                } else if (ann.type === 'highlight') {
                  return (
                    <div key={idx} style={{
                      position: 'absolute',
                      left: ann.x * scale,
                      top: ann.y * scale,
                      width: ann.width * scale,
                      height: ann.height * scale,
                      background: 'rgba(250, 204, 21, 0.35)',
                      border: '1px solid rgba(250, 204, 21, 0.5)',
                      pointerEvents: 'none'
                    }} />
                  );
                }
                return null;
              })}
            </div>

            {/* Page number label */}
            <div style={{ position: 'absolute', bottom: '8px', right: '12px', fontSize: '0.75rem', color: 'var(--text-muted)', background: 'rgba(0,0,0,0.5)', padding: '2px 8px', borderRadius: '4px' }}>
              Page {pageNum} / {numPages}
            </div>
          </div>
        ))}
      </div>

      {/* Comment Popup */}
      {commentPopup && (
        <div style={{
          position: 'fixed',
          top: commentPopup.screenY + 10,
          left: commentPopup.screenX + 10,
          background: '#1e293b', border: '1px solid rgba(255,255,255,0.15)',
          borderRadius: '8px', padding: '1rem',
          boxShadow: '0 10px 30px rgba(0,0,0,0.5)',
          zIndex: 1000, width: '280px'
        }}>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '0.5rem' }}>Add Comment</p>
          <textarea
            value={commentText}
            onChange={(e) => setCommentText(e.target.value)}
            placeholder="Type your comment..."
            autoFocus
            style={{
              width: '100%', height: '80px', padding: '0.5rem',
              background: 'rgba(0,0,0,0.3)', border: '1px solid rgba(255,255,255,0.1)',
              borderRadius: '6px', color: 'white', fontSize: '0.9rem',
              resize: 'none', fontFamily: 'inherit'
            }}
          />
          <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem' }}>
            <button
              onClick={saveComment}
              style={{ flex: 1, padding: '0.4rem', background: 'var(--primary)', border: 'none', color: 'white', borderRadius: '6px', cursor: 'pointer', fontWeight: '500' }}
            >
              Add
            </button>
            <button
              onClick={() => setCommentPopup(null)}
              style={{ flex: 1, padding: '0.4rem', background: 'transparent', border: '1px solid rgba(255,255,255,0.1)', color: 'var(--text-muted)', borderRadius: '6px', cursor: 'pointer' }}
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Annotation List Sidebar */}
      {annotations.length > 0 && (
        <div className="glass-panel" style={{ position: 'fixed', right: '1rem', top: '50%', transform: 'translateY(-50%)', width: '250px', maxHeight: '400px', overflowY: 'auto', padding: '1rem', zIndex: 100 }}>
          <h3 style={{ fontSize: '0.9rem', fontWeight: '600', marginBottom: '0.75rem', color: 'var(--primary)' }}>Annotations ({annotations.length})</h3>
          {annotations.map((ann, idx) => (
            <div key={idx} style={{ 
              padding: '0.5rem', marginBottom: '0.5rem', 
              background: 'rgba(0,0,0,0.2)', borderRadius: '6px',
              display: 'flex', justifyContent: 'space-between', alignItems: 'center'
            }}>
              <div>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  Page {ann.page} — {ann.type === 'comment' ? '💬 ' + ann.text.substring(0, 30) : '🟡 Highlight'}
                </span>
              </div>
              <button 
                onClick={() => setAnnotations(prev => prev.filter((_, i) => i !== idx))}
                style={{ background: 'transparent', border: 'none', color: 'var(--error)', cursor: 'pointer', fontSize: '0.8rem' }}
              >
                ✕
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default PdfAnnotator;
