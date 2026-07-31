package backend.dao;

import backend.db.Database;
import backend.model.PdfAnnotation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public final class PdfAnnotationDao {
    private final Database database;

    public PdfAnnotationDao(Database database) {
        this.database = database;
    }

    public List<PdfAnnotation> findByDocument(String documentId) throws Exception {
        String sql =
            "SELECT annotation_id, annotation_type, page_number, x_position, y_position, " +
            "annotation_width, annotation_height, comment_text, created_by, created_at " +
            "FROM pdf_annotations WHERE doc_id = ? ORDER BY created_at, annotation_id";
        List<PdfAnnotation> annotations = new ArrayList<>();

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    annotations.add(new PdfAnnotation(
                        result.getString("annotation_id"),
                        result.getString("annotation_type"),
                        result.getInt("page_number"),
                        result.getDouble("x_position"),
                        result.getDouble("y_position"),
                        nullableDouble(result, "annotation_width"),
                        nullableDouble(result, "annotation_height"),
                        result.getString("comment_text"),
                        result.getString("created_by"),
                        instantText(result.getTimestamp("created_at")),
                        true
                    ));
                }
            }
        }
        return annotations;
    }

    public boolean exists(Connection connection, String annotationId, String documentId) throws Exception {
        String sql = "SELECT 1 FROM pdf_annotations WHERE annotation_id = ? AND doc_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, annotationId);
            statement.setString(2, documentId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public void insertAll(
            Connection connection,
            String documentId,
            List<PdfAnnotation> annotations,
            String username) throws Exception {
        String sql =
            "INSERT INTO pdf_annotations " +
            "(annotation_id, doc_id, annotation_type, page_number, x_position, y_position, " +
            "annotation_width, annotation_height, comment_text, created_by) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (PdfAnnotation annotation : annotations) {
                statement.setString(1, annotation.getId());
                statement.setString(2, documentId);
                statement.setString(3, annotation.getType());
                statement.setInt(4, annotation.getPage());
                statement.setDouble(5, annotation.getX());
                statement.setDouble(6, annotation.getY());

                if ("highlight".equals(annotation.getType())) {
                    statement.setDouble(7, annotation.getWidth());
                    statement.setDouble(8, annotation.getHeight());
                    statement.setNull(9, Types.LONGVARCHAR);
                } else {
                    statement.setNull(7, Types.DOUBLE);
                    statement.setNull(8, Types.DOUBLE);
                    statement.setString(9, annotation.getText());
                }
                statement.setString(10, username);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private Double nullableDouble(ResultSet result, String column) throws Exception {
        double value = result.getDouble(column);
        return result.wasNull() ? null : value;
    }

    private String instantText(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }
}
