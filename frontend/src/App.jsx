import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Login from './Login';
import Dashboard from './Dashboard';
import DocumentEditor from './DocumentEditor';
import DocumentHistory from './DocumentHistory';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/login" element={<Login />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/editor/:id" element={<DocumentEditor />} />
        <Route path="/history/:id" element={<DocumentHistory />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
