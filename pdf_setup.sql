USE react_java_auth;

-- Add columns to support PDF uploads alongside text documents
ALTER TABLE documents ADD COLUMN doc_type VARCHAR(10) DEFAULT 'text';
ALTER TABLE documents ADD COLUMN file_path VARCHAR(500) DEFAULT NULL;

-- doc_type: 'text' for markdown documents, 'pdf' for uploaded PDFs
-- file_path: path to the uploaded file on disk (only for PDFs)
