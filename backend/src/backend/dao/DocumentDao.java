package backend.dao;

import backend.db.Database;
import backend.model.DocumentRecord;
import backend.model.DocumentSummary;
import backend.model.PdfFile;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public final class DocumentDao {
    private final Database database;

    public DocumentDao(Database database) {
        this.database = database;
    }

    public List<DocumentSummary> findAll() throws Exception {
        String sql =
            "SELECT doc_id, title, doc_type, last_updated_by, last_updated_at " +
            "FROM documents ORDER BY last_updated_at DESC";
        List<DocumentSummary> documents = new ArrayList<>();

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String type = result.getString("doc_type");
                documents.add(new DocumentSummary(
                    result.getString("doc_id"),
                    result.getString("title"),
                    type == null ? "text" : type,
                    result.getString("last_updated_by"),
                    timestampText(result.getTimestamp("last_updated_at"))
                ));
            }
        }
        return documents;
    }

    public DocumentRecord findById(String documentId) throws Exception {
        String sql =
            "SELECT doc_id, title, content, doc_type, file_path, last_updated_by, last_updated_at " +
            "FROM documents WHERE doc_id = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? mapDocument(result) : null;
            }
        }
    }

    public void insertText(
            String documentId,
            String title,
            String content,
            String username) throws Exception {
        String sql =
            "INSERT INTO documents (doc_id, title, content, last_updated_by) " +
            "VALUES (?, ?, ?, ?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentId);
            statement.setString(2, title);
            statement.setString(3, content);
            statement.setString(4, username);
            statement.executeUpdate();
        }
    }

    public void insertPdf(
            String documentId,
            String title,
            String filePath,
            String username) throws Exception {
        String sql =
            "INSERT INTO documents " +
            "(doc_id, title, content, doc_type, file_path, last_updated_by) " +
            "VALUES (?, ?, ?, 'pdf', ?, ?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentId);
            statement.setString(2, title);
            statement.setString(3, "PDF Document");
            statement.setString(4, filePath);
            statement.setString(5, username);
            statement.executeUpdate();
        }
    }

    public DocumentRecord findTextForUpdate(Connection connection, String documentId) throws Exception {
        String sql =
            "SELECT doc_id, title, content, doc_type, file_path, last_updated_by, last_updated_at " +
            "FROM documents WHERE doc_id = ? AND (doc_type = 'text' OR doc_type IS NULL) FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? mapDocument(result) : null;
            }
        }
    }

    public DocumentRecord findPdfForUpdate(Connection connection, String documentId) throws Exception {
        String sql =
            "SELECT doc_id, title, content, doc_type, file_path, last_updated_by, last_updated_at " +
            "FROM documents WHERE doc_id = ? AND doc_type = 'pdf' FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? mapDocument(result) : null;
            }
        }
    }

    public int updateText(
            Connection connection,
            String documentId,
            String title,
            String content,
            String username) throws Exception {
        String sql =
            "UPDATE documents SET title = ?, content = ?, last_updated_by = ? " +
            "WHERE doc_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, title);
            statement.setString(2, content);
            statement.setString(3, username);
            statement.setString(4, documentId);
            return statement.executeUpdate();
        }
    }

    public int updateLastModified(
            Connection connection,
            String documentId,
            String username) throws Exception {
        String sql =
            "UPDATE documents SET last_updated_by = ?, last_updated_at = CURRENT_TIMESTAMP " +
            "WHERE doc_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, documentId);
            return statement.executeUpdate();
        }
    }

    public PdfFile findCurrentPdf(String documentId) throws Exception {
        String sql = "SELECT title, file_path FROM documents WHERE doc_id = ? AND doc_type = 'pdf'";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new PdfFile(
                    new File(result.getString("file_path")),
                    result.getString("title")
                );
            }
        }
    }

    private DocumentRecord mapDocument(ResultSet result) throws Exception {
        String type = result.getString("doc_type");
        return new DocumentRecord(
            result.getString("doc_id"),
            result.getString("title"),
            result.getString("content"),
            type == null ? "text" : type,
            result.getString("file_path"),
            result.getString("last_updated_by"),
            timestampText(result.getTimestamp("last_updated_at"))
        );
    }

    private String timestampText(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toString();
    }
}
