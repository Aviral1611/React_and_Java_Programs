package backend.service;

import backend.dao.DocumentDao;
import backend.dao.HistoryDao;
import backend.dao.PdfAnnotationDao;
import backend.db.Database;
import backend.exception.ApiException;
import backend.model.AnnotationSaveResult;
import backend.model.DocumentRecord;
import backend.model.PdfAnnotation;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

public final class PdfAnnotationService {
    private final Database database;
    private final DocumentDao documentDao;
    private final HistoryDao historyDao;
    private final PdfAnnotationDao annotationDao;

    public PdfAnnotationService(
            Database database,
            DocumentDao documentDao,
            HistoryDao historyDao,
            PdfAnnotationDao annotationDao) {
        this.database = database;
        this.documentDao = documentDao;
        this.historyDao = historyDao;
        this.annotationDao = annotationDao;
    }

    public AnnotationSaveResult annotate(
            String documentId,
            String username,
            List<PdfAnnotation> annotations) throws Exception {
        if (annotations == null || annotations.isEmpty()) {
            throw new ApiException(400, "No annotations provided");
        }
        normalizeAndValidate(annotations);

        Path temporaryFile = null;
        Path backupFile = null;
        Path currentPath = null;
        boolean originalWasReplaced = false;
        boolean saveCommitted = false;

        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                DocumentRecord document = documentDao.findPdfForUpdate(connection, documentId);
                if (document == null) {
                    throw new ApiException(404, "PDF document not found");
                }
                if (document.getFilePath() == null || document.getFilePath().trim().isEmpty()) {
                    throw new ApiException(404, "PDF file path is missing");
                }

                File currentFile = new File(document.getFilePath());
                if (!currentFile.isFile()) {
                    throw new ApiException(404, "PDF file not found on disk");
                }
                currentPath = currentFile.toPath();
                Path parentDirectory = currentPath.toAbsolutePath().getParent();
                if (parentDirectory == null) {
                    throw new IOException("Could not determine the PDF directory");
                }

                AnnotationSaveResult duplicateResult = checkForDuplicateRequest(
                    connection,
                    documentId,
                    annotations
                );
                if (duplicateResult != null) {
                    connection.rollback();
                    return duplicateResult;
                }

                temporaryFile = Files.createTempFile(
                    parentDirectory,
                    documentId + "_annotated_",
                    ".pdf.tmp"
                );
                writeAnnotatedPdf(currentFile, temporaryFile, username, annotations);
                validateGeneratedPdf(temporaryFile);

                String backupName =
                    documentId + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID() + ".pdf";
                backupFile = parentDirectory.resolve(backupName);
                Files.copy(currentPath, backupFile);

                historyDao.insert(
                    connection,
                    documentId,
                    document.getTitle(),
                    backupFile.toAbsolutePath().toString(),
                    username
                );
                annotationDao.insertAll(connection, documentId, annotations, username);

                moveReplacing(temporaryFile, currentPath);
                originalWasReplaced = true;
                temporaryFile = null;

                if (documentDao.updateLastModified(connection, documentId, username) != 1) {
                    throw new IOException("PDF document metadata was not updated");
                }

                connection.commit();
                saveCommitted = true;
                System.out.println("[Annotate] Saved annotated PDF: " + currentPath.toAbsolutePath());
                return new AnnotationSaveResult("PDF annotated successfully", false);
            } catch (Exception e) {
                rollback(connection, e);
                if (originalWasReplaced && backupFile != null && currentPath != null
                        && Files.exists(backupFile)) {
                    try {
                        Files.copy(backupFile, currentPath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception restoreError) {
                        e.addSuppressed(restoreError);
                    }
                }
                throw e;
            } finally {
                resetAutoCommit(connection);
            }
        } finally {
            if (temporaryFile != null) {
                Files.deleteIfExists(temporaryFile);
            }
            if (!saveCommitted && backupFile != null) {
                Files.deleteIfExists(backupFile);
            }
        }
    }

    private AnnotationSaveResult checkForDuplicateRequest(
            Connection connection,
            String documentId,
            List<PdfAnnotation> annotations) throws Exception {
        boolean anySaved = false;
        boolean allSaved = true;
        for (PdfAnnotation annotation : annotations) {
            boolean exists = annotationDao.exists(connection, annotation.getId(), documentId);
            anySaved |= exists;
            allSaved &= exists;
        }

        if (allSaved) {
            return new AnnotationSaveResult("Annotations were already saved", true);
        }
        if (anySaved) {
            throw new ApiException(
                409,
                "Annotation request contains a mixture of saved and unsaved IDs"
            );
        }
        return null;
    }

    private void writeAnnotatedPdf(
            File currentFile,
            Path output,
            String username,
            List<PdfAnnotation> annotations) throws Exception {
        try (PDDocument pdf = Loader.loadPDF(currentFile)) {
            for (PdfAnnotation annotation : annotations) {
                int pageIndex = annotation.getPage() - 1;
                if (pageIndex < 0 || pageIndex >= pdf.getNumberOfPages()) {
                    throw new ApiException(400, "Invalid annotation page: " + annotation.getPage());
                }

                PDPage page = pdf.getPage(pageIndex);
                float pageHeight = page.getMediaBox().getHeight();
                float x = annotation.getX().floatValue();
                float y = annotation.getY().floatValue();

                if ("comment".equals(annotation.getType())) {
                    addComment(pdf, page, username, annotation, x, pageHeight - y);
                } else {
                    addHighlight(pdf, page, annotation, x, y, pageHeight);
                }
            }
            pdf.save(output.toFile());
        }
    }

    private void addComment(
            PDDocument pdf,
            PDPage page,
            String username,
            PdfAnnotation annotation,
            float x,
            float pdfY) throws Exception {
        PDAnnotationText comment = new PDAnnotationText();
        comment.setContents(annotation.getText().trim());
        comment.setRectangle(new PDRectangle(x, pdfY - 20, 20, 20));
        comment.setName(PDAnnotationText.NAME_COMMENT);
        comment.setTitlePopup(username);
        page.getAnnotations().add(comment);
        comment.constructAppearances(pdf);
    }

    private void addHighlight(
            PDDocument pdf,
            PDPage page,
            PdfAnnotation annotation,
            float x,
            float browserY,
            float pageHeight) throws Exception {
        float width = annotation.getWidth().floatValue();
        float height = annotation.getHeight().floatValue();
        float pdfY = pageHeight - browserY - height;
        PDRectangle rectangle = new PDRectangle(x, pdfY, width, height);

        PDAnnotationHighlight highlight = new PDAnnotationHighlight();
        highlight.setRectangle(rectangle);
        highlight.setQuadPoints(new float[] {
            rectangle.getLowerLeftX(), rectangle.getUpperRightY(),
            rectangle.getUpperRightX(), rectangle.getUpperRightY(),
            rectangle.getLowerLeftX(), rectangle.getLowerLeftY(),
            rectangle.getUpperRightX(), rectangle.getLowerLeftY()
        });
        highlight.setColor(new PDColor(new float[] {1, 1, 0}, PDDeviceRGB.INSTANCE));
        highlight.setConstantOpacity(0.3f);
        highlight.setContents("Highlight");
        page.getAnnotations().add(highlight);
        highlight.constructAppearances(pdf);
    }

    private void validateGeneratedPdf(Path file) throws Exception {
        try (PDDocument validation = Loader.loadPDF(file.toFile())) {
            if (validation.getNumberOfPages() == 0) {
                throw new IOException("Generated PDF contains no pages");
            }
        }
    }

    private void normalizeAndValidate(List<PdfAnnotation> annotations) throws ApiException {
        for (PdfAnnotation annotation : annotations) {
            if (annotation == null) {
                throw new ApiException(400, "Annotation entry is required");
            }

            String annotationId = annotation.getId();
            if (annotationId == null || annotationId.trim().isEmpty()) {
                annotationId = UUID.randomUUID().toString();
            }
            try {
                annotation.setId(UUID.fromString(annotationId.trim()).toString());
            } catch (IllegalArgumentException e) {
                throw new ApiException(400, "Annotation ID must be a UUID", e);
            }

            if (!"comment".equals(annotation.getType())
                    && !"highlight".equals(annotation.getType())) {
                throw new ApiException(400, "Unsupported annotation type: " + annotation.getType());
            }
            if (annotation.getPage() == null || annotation.getPage() < 1) {
                throw new ApiException(400, "Annotation page must be a positive number");
            }
            requireFinite(annotation.getX(), "x");
            requireFinite(annotation.getY(), "y");

            if ("comment".equals(annotation.getType())) {
                if (annotation.getText() == null || annotation.getText().trim().isEmpty()) {
                    throw new ApiException(400, "Comment text is required");
                }
            } else {
                requireFinite(annotation.getWidth(), "width");
                requireFinite(annotation.getHeight(), "height");
                if (annotation.getWidth() <= 0 || annotation.getHeight() <= 0) {
                    throw new ApiException(400, "Highlight dimensions must be positive");
                }
            }
        }
    }

    private void requireFinite(Double value, String field) throws ApiException {
        if (value == null || value.isNaN() || value.isInfinite()) {
            throw new ApiException(400, "Annotation field '" + field + "' must be a number");
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (Exception rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    private void resetAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (Exception ignored) {
            // Connection is about to close.
        }
    }
}
