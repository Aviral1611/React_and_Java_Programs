package backend.controller;

import backend.exception.ApiException;
import backend.model.AnnotationSaveResult;
import backend.model.PdfAnnotation;
import backend.model.PdfFile;
import backend.security.JwtService;
import backend.service.DocumentService;
import backend.service.PdfAnnotationService;
import backend.util.HttpUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import java.util.ArrayList;
import java.util.List;

public final class DocumentController extends AuthenticatedHandler {
    private static final String BASE_PATH = "/api/documents";

    private final DocumentService documentService;
    private final PdfAnnotationService annotationService;
    private final ObjectMapper mapper;

    public DocumentController(
            JwtService jwtService,
            DocumentService documentService,
            PdfAnnotationService annotationService,
            ObjectMapper mapper) {
        super(jwtService);
        this.documentService = documentService;
        this.annotationService = annotationService;
        this.mapper = mapper;
    }

    @Override
    protected void handleAuthenticated(HttpExchange exchange, String username) throws Exception {
        String method = exchange.getRequestMethod().toUpperCase();
        String path = exchange.getRequestURI().getPath();

        if ("GET".equals(method) && BASE_PATH.equals(path)) {
            HttpUtil.sendJson(exchange, 200, documentService.listDocuments());
            return;
        }
        if ("POST".equals(method) && BASE_PATH.equals(path)) {
            createTextDocument(exchange, username);
            return;
        }
        if ("POST".equals(method) && (BASE_PATH + "/upload").equals(path)) {
            uploadPdf(exchange, username);
            return;
        }
        if ("GET".equals(method) && path.startsWith(BASE_PATH + "/")) {
            handleGetRoute(exchange, path.substring((BASE_PATH + "/").length()));
            return;
        }
        if ("PUT".equals(method) && path.startsWith(BASE_PATH + "/")) {
            updateTextDocument(
                exchange,
                path.substring((BASE_PATH + "/").length()),
                username
            );
            return;
        }
        if ("POST".equals(method)
                && path.startsWith(BASE_PATH + "/")
                && path.endsWith("/annotate")) {
            String documentId = path.substring(
                (BASE_PATH + "/").length(),
                path.length() - "/annotate".length()
            );
            annotatePdf(exchange, documentId, username);
            return;
        }

        throw new ApiException(404, "Not Found");
    }

    private void handleGetRoute(HttpExchange exchange, String suffix) throws Exception {
        String[] parts = suffix.split("/");
        if (parts.length == 4
                && "history".equals(parts[1])
                && "download".equals(parts[3])) {
            int historyId;
            try {
                historyId = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                throw new ApiException(400, "Invalid history version", e);
            }
            PdfFile pdf = documentService.getHistoryPdf(parts[0], historyId);
            HttpUtil.sendPdf(exchange, pdf.getFile(), pdf.getTitle(), false);
            return;
        }
        if (parts.length == 2 && "history".equals(parts[1])) {
            HttpUtil.sendJson(exchange, 200, documentService.getHistory(parts[0]));
            return;
        }
        if (parts.length == 2 && "download".equals(parts[1])) {
            PdfFile pdf = documentService.getCurrentPdf(parts[0]);
            HttpUtil.sendPdf(exchange, pdf.getFile(), pdf.getTitle(), true);
            return;
        }
        if (parts.length == 2 && "annotations".equals(parts[1])) {
            HttpUtil.sendJson(exchange, 200, documentService.getPdfAnnotations(parts[0]));
            return;
        }
        if (parts.length == 1 && !parts[0].isEmpty()) {
            HttpUtil.sendJson(exchange, 200, documentService.getDocument(parts[0]));
            return;
        }
        throw new ApiException(404, "Not Found");
    }

    private void createTextDocument(HttpExchange exchange, String username) throws Exception {
        JsonNode request = readJson(exchange);
        String documentId = documentService.createTextDocument(
            text(request, "title"),
            text(request, "content"),
            username
        );
        HttpUtil.sendJson(
            exchange,
            201,
            mapper.createObjectNode()
                .put("message", "Document created")
                .put("doc_id", documentId)
        );
    }

    private void updateTextDocument(
            HttpExchange exchange,
            String documentId,
            String username) throws Exception {
        JsonNode request = readJson(exchange);
        documentService.updateTextDocument(
            documentId,
            text(request, "title"),
            text(request, "content"),
            username
        );
        HttpUtil.sendJson(
            exchange,
            200,
            mapper.createObjectNode().put("message", "Document updated successfully")
        );
    }

    private void uploadPdf(HttpExchange exchange, String username) throws Exception {
        JsonNode request = readJson(exchange);
        String documentId = documentService.uploadPdf(
            text(request, "filename"),
            text(request, "fileData"),
            username
        );
        HttpUtil.sendJson(
            exchange,
            201,
            mapper.createObjectNode()
                .put("message", "PDF uploaded successfully")
                .put("doc_id", documentId)
        );
    }

    private void annotatePdf(
            HttpExchange exchange,
            String documentId,
            String username) throws Exception {
        JsonNode request = readJson(exchange);
        JsonNode annotationNodes = request == null ? null : request.get("annotations");
        if (annotationNodes == null || !annotationNodes.isArray()) {
            throw new ApiException(400, "No annotations provided");
        }

        List<PdfAnnotation> annotations = new ArrayList<>();
        for (JsonNode annotationNode : annotationNodes) {
            annotations.add(mapper.treeToValue(annotationNode, PdfAnnotation.class));
        }
        AnnotationSaveResult result = annotationService.annotate(
            documentId,
            username,
            annotations
        );
        HttpUtil.sendJson(exchange, 200, result);
    }

    private JsonNode readJson(HttpExchange exchange) throws Exception {
        String body = HttpUtil.readRequestBody(exchange);
        if (body == null || body.trim().isEmpty()) {
            throw new ApiException(400, "JSON request body is required");
        }
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new ApiException(400, "Invalid JSON request body", e);
        }
    }

    private String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
