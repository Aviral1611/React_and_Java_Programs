USE react_java_auth;

CREATE TABLE IF NOT EXISTS documents (
    doc_id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    last_updated_by VARCHAR(100) NOT NULL,
    last_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document_history (
    history_id INT AUTO_INCREMENT PRIMARY KEY,
    doc_id VARCHAR(36) NOT NULL,
    old_title VARCHAR(255),
    old_content TEXT,
    changed_by VARCHAR(100),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (doc_id) REFERENCES documents(doc_id) ON DELETE CASCADE
);
