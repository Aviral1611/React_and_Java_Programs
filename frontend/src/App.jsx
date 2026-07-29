import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Login from './Login';
import Dashboard from './Dashboard';
import DocumentEditor from './DocumentEditor';
import DocumentHistory from './DocumentHistory';
import PdfAnnotator from './PdfAnnotator';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/login" element={<Login />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/editor/:id" element={<DocumentEditor />} />
        <Route path="/history/:id" element={<DocumentHistory />} />
        <Route path="/annotate/:id" element={<PdfAnnotator />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
