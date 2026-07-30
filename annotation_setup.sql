USE react_java_auth;

CREATE TABLE IF NOT EXISTS pdf_annotations (
    annotation_id VARCHAR(36) PRIMARY KEY,
    doc_id VARCHAR(36) NOT NULL,
    annotation_type VARCHAR(20) NOT NULL,
    page_number INT NOT NULL,
    x_position DOUBLE NOT NULL,
    y_position DOUBLE NOT NULL,
    annotation_width DOUBLE DEFAULT NULL,
    annotation_height DOUBLE DEFAULT NULL,
    comment_text TEXT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pdf_annotations_document (doc_id, created_at),
    FOREIGN KEY (doc_id) REFERENCES documents(doc_id) ON DELETE CASCADE
);
