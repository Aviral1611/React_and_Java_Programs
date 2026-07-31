package backend.dao;

import backend.db.Database;
import backend.model.HistoryRecord;
import backend.model.PdfFile;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public final class HistoryDao {
    private final Database database;

    public HistoryDao(Database database) {
        this.database = database;
    }

    public void insert(
            Connection connection,
            String documentId,
            String oldTitle,
            String oldContent,
            String username) throws Exception {
        String sql =
            "INSERT INTO document_history (doc_id, old_title, old_content, changed_by) " +
            "VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentId);
            statement.setString(2, oldTitle);
            statement.setString(3, oldContent);
            statement.setString(4, username);
            statement.executeUpdate();
        }
    }

    public List<HistoryRecord> findByDocument(String documentId) throws Exception {
        String sql =
            "SELECT h.history_id, h.old_title, h.old_content, h.changed_by, h.changed_at, d.doc_type " +
            "FROM document_history h JOIN documents d ON d.doc_id = h.doc_id " +
            "WHERE h.doc_id = ? ORDER BY h.changed_at DESC, h.history_id DESC";
        List<HistoryRecord> history = new ArrayList<>();

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String type = result.getString("doc_type");
                    history.add(new HistoryRecord(
                        result.getInt("history_id"),
                        result.getString("old_title"),
                        "pdf".equals(type) ? null : result.getString("old_content"),
                        result.getString("changed_by"),
                        instantText(result.getTimestamp("changed_at")),
                        type == null ? "text" : type
                    ));
                }
            }
        }
        return history;
    }

    public PdfFile findPdfVersion(String documentId, int historyId) throws Exception {
        String sql =
            "SELECT h.old_title, h.old_content FROM document_history h " +
            "JOIN documents d ON d.doc_id = h.doc_id " +
            "WHERE h.history_id = ? AND h.doc_id = ? AND d.doc_type = 'pdf'";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, historyId);
            statement.setString(2, documentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new PdfFile(
                    new File(result.getString("old_content")),
                    result.getString("old_title")
                );
            }
        }
    }

    private String instantText(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }
}
