package backend.service;

import backend.config.AppConfig;
import backend.dao.DocumentDao;
import backend.dao.HistoryDao;
import backend.dao.PdfAnnotationDao;
import backend.db.Database;
import backend.exception.ApiException;
import backend.model.DocumentRecord;
import backend.model.DocumentSummary;
import backend.model.HistoryRecord;
import backend.model.PdfAnnotation;
import backend.model.PdfFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public final class DocumentService {
    private final Database database;
    private final DocumentDao documentDao;
    private final HistoryDao historyDao;
    private final PdfAnnotationDao annotationDao;
    private final Path uploadDirectory;

    public DocumentService(
            AppConfig config,
            Database database,
            DocumentDao documentDao,
            HistoryDao historyDao,
            PdfAnnotationDao annotationDao) {
        this.database = database;
        this.documentDao = documentDao;
        this.historyDao = historyDao;
        this.annotationDao = annotationDao;
        this.uploadDirectory = config.getUploadDirectory();
    }

    public List<DocumentSummary> listDocuments() throws Exception {
        return documentDao.findAll();
    }

    public String createTextDocument(String title, String content, String username) throws Exception {
        requireText(title, content);
        String documentId = UUID.randomUUID().toString();
        documentDao.insertText(documentId, title.trim(), content, username);
        return documentId;
    }

    public DocumentRecord getDocument(String documentId) throws Exception {
        DocumentRecord document = documentDao.findById(documentId);
        if (document == null) {
            throw new ApiException(404, "Document not found");
        }
        return document;
    }

    public void updateTextDocument(
            String documentId,
            String title,
            String content,
            String username) throws Exception {
        requireText(title, content);

        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                DocumentRecord current = documentDao.findTextForUpdate(connection, documentId);
                if (current == null) {
                    throw new ApiException(404, "Text document not found");
                }

                historyDao.insert(
                    connection,
                    documentId,
                    current.getTitle(),
                    current.getContent(),
                    username
                );
                if (documentDao.updateText(
                        connection,
                        documentId,
                        title.trim(),
                        content,
                        username) != 1) {
                    throw new IllegalStateException("Text document update did not affect one row");
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                resetAutoCommit(connection);
            }
        }
    }

    public List<HistoryRecord> getHistory(String documentId) throws Exception {
        return historyDao.findByDocument(documentId);
    }

    public List<PdfAnnotation> getPdfAnnotations(String documentId) throws Exception {
        return annotationDao.findByDocument(documentId);
    }

    public String uploadPdf(String filename, String base64Data, String username) throws Exception {
        if (filename == null || filename.trim().isEmpty()
                || base64Data == null || base64Data.trim().isEmpty()) {
            throw new ApiException(400, "Filename and file data are required");
        }

        String safeTitle = new File(filename).getName();
        if (!safeTitle.toLowerCase().endsWith(".pdf")) {
            throw new ApiException(400, "Only PDF files are supported");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Invalid Base64 PDF data", e);
        }
        if (!hasPdfHeader(bytes)) {
            throw new ApiException(400, "Uploaded file is not a valid PDF");
        }

        Files.createDirectories(uploadDirectory);
        String documentId = UUID.randomUUID().toString();
        Path output = uploadDirectory.resolve(documentId + ".pdf").normalize();
        if (!output.startsWith(uploadDirectory)) {
            throw new IllegalStateException("Resolved upload path left the upload directory");
        }

        Files.write(output, bytes);
        boolean databaseSaved = false;
        try {
            documentDao.insertPdf(
                documentId,
                safeTitle,
                output.toAbsolutePath().toString(),
                username
            );
            databaseSaved = true;
            return documentId;
        } finally {
            if (!databaseSaved) {
                Files.deleteIfExists(output);
            }
        }
    }

    public PdfFile getCurrentPdf(String documentId) throws Exception {
        PdfFile pdf = documentDao.findCurrentPdf(documentId);
        if (pdf == null) {
            throw new ApiException(404, "PDF not found");
        }
        if (!pdf.getFile().isFile()) {
            throw new ApiException(404, "File not found on disk");
        }
        return pdf;
    }

    public PdfFile getHistoryPdf(String documentId, int historyId) throws Exception {
        PdfFile pdf = historyDao.findPdfVersion(documentId, historyId);
        if (pdf == null) {
            throw new ApiException(404, "PDF history version not found");
        }
        if (!pdf.getFile().isFile()) {
            throw new ApiException(404, "PDF history file not found on disk");
        }
        return pdf;
    }

    private void requireText(String title, String content) throws ApiException {
        if (title == null || title.trim().isEmpty()
                || content == null || content.trim().isEmpty()) {
            throw new ApiException(400, "Title and content are required");
        }
    }

    private boolean hasPdfHeader(byte[] bytes) {
        return bytes.length >= 5
            && bytes[0] == '%'
            && bytes[1] == 'P'
            && bytes[2] == 'D'
            && bytes[3] == 'F'
            && bytes[4] == '-';
    }

    private void resetAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (Exception ignored) {
            // Connection is about to close.
        }
    }
}
