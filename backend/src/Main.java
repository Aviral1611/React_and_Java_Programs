package backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;

public class Main {
    private static String DB_URL;
    private static String DB_USER;
    private static String DB_PASSWORD;
    private static String JWT_SECRET;
    private static long JWT_EXPIRATION;
    private static final String UPLOAD_DIR = System.getProperty("user.dir") + File.separator + "uploads";

    public static void main(String[] args) throws Exception {
        loadProperties();
        
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        server.createContext("/api/hello", new HelloHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/documents", new DocumentHandler());
        
        server.setExecutor(null);
        System.out.println("Server is starting on port 8080...");
        server.start();
    }
    
    private static void loadProperties() {
        Properties prop = new Properties();
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("Unable to find config.properties in the classpath (make sure it's in the 'src' folder)");
                return;
            }
            prop.load(input);
            DB_URL = prop.getProperty("db.url");
            DB_USER = prop.getProperty("db.user");
            DB_PASSWORD = prop.getProperty("db.password");
            JWT_SECRET = prop.getProperty("jwt.secret");
            JWT_EXPIRATION = Long.parseLong(prop.getProperty("jwt.expiration", "3600000"));
            System.out.println("[Config] Properties loaded successfully.");
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Failed to load config.properties.");
        }
    }

    // ===== JWT Utility Methods (using built-in Java crypto, no external JARs) =====

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    private static String generateJwt(String username, String role) throws Exception {
        // Header
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String encodedHeader = base64UrlEncode(header.getBytes(StandardCharsets.UTF_8));

        // Payload
        long now = System.currentTimeMillis() / 1000;
        long exp = now + (JWT_EXPIRATION / 1000);
        String payload = "{\"sub\":\"" + username + "\",\"role\":\"" + role + "\",\"iat\":" + now + ",\"exp\":" + exp + "}";
        String encodedPayload = base64UrlEncode(payload.getBytes(StandardCharsets.UTF_8));

        // Signature
        String content = encodedHeader + "." + encodedPayload;
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        String signature = base64UrlEncode(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));

        return content + "." + signature;
    }

    private static String validateJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            // Verify signature
            String content = parts[0] + "." + parts[1];
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            String expectedSignature = base64UrlEncode(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));

            if (!expectedSignature.equals(parts[2])) {
                System.out.println("[JWT] Signature mismatch");
                return null;
            }

            // Decode payload and check expiry
            String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode payload = mapper.readTree(payloadJson);

            long exp = payload.get("exp").asLong();
            if (System.currentTimeMillis() / 1000 > exp) {
                System.out.println("[JWT] Token expired");
                return null;
            }

            return payload.get("sub").asText(); // returns the username
        } catch (Exception e) {
            System.err.println("[JWT] Validation error:");
            e.printStackTrace();
            return null;
        }
    }

    // ===== Helper to send JSON responses =====

    private static void sendJsonResponse(HttpExchange t, int statusCode, String json) throws IOException {
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        t.getResponseHeaders().set("Content-Type", "application/json");
        t.sendResponseHeaders(statusCode, responseBytes.length);
        OutputStream os = t.getResponseBody();
        os.write(responseBytes);
        os.flush();
        os.close();
    }

    // ===== CORS =====

    private static void setCorsHeaders(HttpExchange t) {
        t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        t.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    // ===== Handlers =====

    static class HelloHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            setCorsHeaders(t);
            if ("OPTIONS".equals(t.getRequestMethod())) {
                t.sendResponseHeaders(204, -1);
                return;
            }
            
            // Validate JWT Token
            String authHeader = t.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendJsonResponse(t, 401, "{\"error\": \"Missing or invalid Authorization header\"}");
                return;
            }
            
            String token = authHeader.substring(7);
            String username = validateJwt(token);
            if (username == null) {
                sendJsonResponse(t, 401, "{\"error\": \"Invalid or expired token\"}");
                return;
            }
            
            sendJsonResponse(t, 200, "{\"message\": \"Hello from the protected Java Backend!\", \"user\": \"" + username + "\"}");
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println("[LoginHandler] Received " + t.getRequestMethod() + " request");
            setCorsHeaders(t);
            if ("OPTIONS".equals(t.getRequestMethod())) {
                t.sendResponseHeaders(204, -1);
                System.out.println("[LoginHandler] Responded to OPTIONS preflight");
                return;
            }

            if ("POST".equals(t.getRequestMethod())) {
                try {
                    InputStream is = t.getRequestBody();
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    System.out.println("[LoginHandler] Request body: " + body);
                    
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonNode = mapper.readTree(body);
                    String username = jsonNode.has("username") ? jsonNode.get("username").asText() : null;
                    String password = jsonNode.has("password") ? jsonNode.get("password").asText() : null;
                    System.out.println("[LoginHandler] Attempting login for user: " + username);

                    if (username != null && password != null && authenticateUser(username, password)) {
                        System.out.println("[LoginHandler] Authentication SUCCESS for user: " + username);
                        
                        System.out.println("[LoginHandler] Generating JWT...");
                        String token = generateJwt(username, "USER");
                        System.out.println("[LoginHandler] JWT generated, length: " + token.length());
                                
                        String response = "{\"token\": \"" + token + "\", \"role\": \"USER\", \"username\": \"" + username + "\"}";
                        sendJsonResponse(t, 200, response);
                        System.out.println("[LoginHandler] Response sent successfully!");
                    } else {
                        System.out.println("[LoginHandler] Authentication FAILED for user: " + username);
                        sendJsonResponse(t, 401, "{\"error\": \"Invalid credentials\"}");
                    }
                } catch (Throwable e) {
                    System.err.println("[LoginHandler] CRASH:");
                    e.printStackTrace();
                    try {
                        sendJsonResponse(t, 500, "{\"error\": \"Internal server error\"}");
                    } catch (Exception ex) {
                        System.err.println("[LoginHandler] Could not send error response:");
                        ex.printStackTrace();
                    }
                }
            } else {
                t.sendResponseHeaders(405, -1);
            }
        }

        private boolean authenticateUser(String username, String password) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query = "SELECT password FROM users WHERE username = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, username);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return password.equals(rs.getString("password"));
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[DB] Authentication error:");
                e.printStackTrace();
            }
            return false;
        }
    }
    static class DocumentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            setCorsHeaders(t);
            if ("OPTIONS".equals(t.getRequestMethod())) {
                t.sendResponseHeaders(204, -1);
                return;
            }

            // 1. Validate JWT Token for all /api/documents routes
            String authHeader = t.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendJsonResponse(t, 401, "{\"error\": \"Missing or invalid Authorization header\"}");
                return;
            }
            
            String token = authHeader.substring(7);
            String username = validateJwt(token);
            if (username == null) {
                sendJsonResponse(t, 401, "{\"error\": \"Invalid or expired token\"}");
                return;
            }

            String path = t.getRequestURI().getPath();
            String method = t.getRequestMethod();

            try {
                // Route: GET /api/documents (List all documents)
                if (method.equals("GET") && path.equals("/api/documents")) {
                    handleGetDocuments(t);
                } 
                // Route: POST /api/documents (Create new text document)
                else if (method.equals("POST") && path.equals("/api/documents")) {
                    handlePostDocument(t, username);
                }
                // Route: POST /api/documents/upload (Upload PDF)
                else if (method.equals("POST") && path.equals("/api/documents/upload")) {
                    handleUploadPdf(t, username);
                }
                // Route: GET /api/documents/{id} or /api/documents/{id}/history or /api/documents/{id}/download
                else if (method.equals("GET") && path.startsWith("/api/documents/")) {
                    String suffix = path.substring("/api/documents/".length());
                    String[] pathParts = suffix.split("/");
                    if (pathParts.length == 4
                            && "history".equals(pathParts[1])
                            && "download".equals(pathParts[3])) {
                        handleDownloadPdfHistory(t, pathParts[0], pathParts[2]);
                    } else if (suffix.endsWith("/history")) {
                        String id = suffix.substring(0, suffix.length() - "/history".length());
                        handleGetHistory(t, id);
                    } else if (suffix.endsWith("/download")) {
                        String id = suffix.substring(0, suffix.length() - "/download".length());
                        handleDownloadPdf(t, id);
                    } else if (suffix.endsWith("/annotations")) {
                        String id = suffix.substring(0, suffix.length() - "/annotations".length());
                        handleGetPdfAnnotations(t, id);
                    } else {
                        handleGetDocument(t, suffix);
                    }
                }
                // Route: PUT /api/documents/{id} (Update specific document)
                else if (method.equals("PUT") && path.startsWith("/api/documents/")) {
                    String id = path.substring("/api/documents/".length());
                    handlePutDocument(t, id, username);
                }
                // Route: POST /api/documents/{id}/annotate (Annotate PDF)
                else if (method.equals("POST") && path.startsWith("/api/documents/") && path.endsWith("/annotate")) {
                    String id = path.substring("/api/documents/".length(), path.length() - "/annotate".length());
                    handleAnnotatePdf(t, id, username);
                }
                else {
                    sendJsonResponse(t, 404, "{\"error\": \"Not Found\"}");
                }
            } catch (LinkageError e) {
                System.err.println("[DocumentHandler] PDF/runtime dependency error while handling " + method + " " + path);
                e.printStackTrace();
                sendJsonResponse(
                    t,
                    500,
                    "{\"error\": \"Backend PDF library is incomplete or has conflicting JAR versions. Check the Eclipse console.\"}"
                );
            } catch (Exception e) {
                System.err.println("[DocumentHandler] Request failed while handling " + method + " " + path);
                e.printStackTrace();
                sendJsonResponse(t, 500, "{\"error\": \"Internal server error\"}");
            }
        }

        private void handleGetDocuments(HttpExchange t) throws Exception {
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("[");
            
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query = "SELECT doc_id, title, doc_type, last_updated_by, last_updated_at FROM documents ORDER BY last_updated_at DESC";
                try (PreparedStatement stmt = conn.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {
                    
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) {
                            jsonBuilder.append(",");
                        }
                        first = false;
                        String docType = rs.getString("doc_type");
                        if (docType == null) docType = "text";
                        jsonBuilder.append("{");
                        jsonBuilder.append("\"doc_id\":\"").append(rs.getString("doc_id")).append("\",");
                        jsonBuilder.append("\"title\":\"").append(rs.getString("title").replace("\"", "\\\"")).append("\",");
                        jsonBuilder.append("\"doc_type\":\"").append(docType).append("\",");
                        jsonBuilder.append("\"last_updated_by\":\"").append(rs.getString("last_updated_by")).append("\",");
                        jsonBuilder.append("\"last_updated_at\":\"").append(rs.getTimestamp("last_updated_at")).append("\"");
                        jsonBuilder.append("}");
                    }
                }
            }
            jsonBuilder.append("]");
            
            sendJsonResponse(t, 200, jsonBuilder.toString());
        }

        private void handlePostDocument(HttpExchange t, String username) throws Exception {
            InputStream is = t.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(body);
            String title = jsonNode.has("title") ? jsonNode.get("title").asText() : "";
            String content = jsonNode.has("content") ? jsonNode.get("content").asText() : "";
            
            if (title.trim().isEmpty() || content.trim().isEmpty()) {
                sendJsonResponse(t, 400, "{\"error\": \"Title and content are required\"}");
                return;
            }

            String docId = UUID.randomUUID().toString();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sql = "INSERT INTO documents (doc_id, title, content, last_updated_by) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, docId);
                    pstmt.setString(2, title);
                    pstmt.setString(3, content);
                    pstmt.setString(4, username);
                    pstmt.executeUpdate();
                }
            }

            sendJsonResponse(t, 201, "{\"message\": \"Document created\", \"doc_id\": \"" + docId + "\"}");
        }

        private void handleGetDocument(HttpExchange t, String docId) throws Exception {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query = "SELECT * FROM documents WHERE doc_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, docId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            StringBuilder jsonBuilder = new StringBuilder();
                            jsonBuilder.append("{");
                            jsonBuilder.append("\"doc_id\":\"").append(rs.getString("doc_id")).append("\",");
                            jsonBuilder.append("\"title\":\"").append(rs.getString("title").replace("\"", "\\\"").replace("\n", "\\n")).append("\",");
                            jsonBuilder.append("\"content\":\"").append(rs.getString("content").replace("\"", "\\\"").replace("\n", "\\n")).append("\",");
                            jsonBuilder.append("\"last_updated_by\":\"").append(rs.getString("last_updated_by")).append("\",");
                            jsonBuilder.append("\"last_updated_at\":\"").append(rs.getTimestamp("last_updated_at")).append("\"");
                            jsonBuilder.append("}");
                            sendJsonResponse(t, 200, jsonBuilder.toString());
                        } else {
                            sendJsonResponse(t, 404, "{\"error\": \"Document not found\"}");
                        }
                    }
                }
            }
        }

        private void handlePutDocument(HttpExchange t, String docId, String username) throws Exception {
            InputStream is = t.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(body);
            String newTitle = jsonNode.has("title") ? jsonNode.get("title").asText() : "";
            String newContent = jsonNode.has("content") ? jsonNode.get("content").asText() : "";
            
            if (newTitle.trim().isEmpty() || newContent.trim().isEmpty()) {
                sendJsonResponse(t, 400, "{\"error\": \"Title and content are required\"}");
                return;
            }

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                conn.setAutoCommit(false); // Start transaction for audit trail
                try {
                    // 1. Fetch current version and lock row
                    String selectSql = "SELECT title, content FROM documents WHERE doc_id = ? FOR UPDATE";
                    String oldTitle = null;
                    String oldContent = null;
                    try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                        selectStmt.setString(1, docId);
                        try (ResultSet rs = selectStmt.executeQuery()) {
                            if (rs.next()) {
                                oldTitle = rs.getString("title");
                                oldContent = rs.getString("content");
                            } else {
                                conn.rollback();
                                sendJsonResponse(t, 404, "{\"error\": \"Document not found\"}");
                                return;
                            }
                        }
                    }

                    // 2. Save old version to history
                    String insertHistorySql = "INSERT INTO document_history (doc_id, old_title, old_content, changed_by) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement historyStmt = conn.prepareStatement(insertHistorySql)) {
                        historyStmt.setString(1, docId);
                        historyStmt.setString(2, oldTitle);
                        historyStmt.setString(3, oldContent);
                        historyStmt.setString(4, username);
                        historyStmt.executeUpdate();
                    }

                    // 3. Update the document
                    String updateDocSql = "UPDATE documents SET title = ?, content = ?, last_updated_by = ? WHERE doc_id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateDocSql)) {
                        updateStmt.setString(1, newTitle);
                        updateStmt.setString(2, newContent);
                        updateStmt.setString(3, username);
                        updateStmt.setString(4, docId);
                        updateStmt.executeUpdate();
                    }

                    conn.commit(); // Commit transaction
                    sendJsonResponse(t, 200, "{\"message\": \"Document updated successfully\"}");

                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        }

        private void handleGetHistory(HttpExchange t, String docId) throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            ArrayNode result = mapper.createArrayNode();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query =
                    "SELECT h.history_id, h.old_title, h.old_content, h.changed_by, h.changed_at, d.doc_type " +
                    "FROM document_history h JOIN documents d ON d.doc_id = h.doc_id " +
                    "WHERE h.doc_id = ? ORDER BY h.changed_at DESC, h.history_id DESC";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, docId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ObjectNode entry = result.addObject();
                            String docType = rs.getString("doc_type");
                            entry.put("history_id", rs.getInt("history_id"));
                            entry.put("old_title", rs.getString("old_title"));
                            entry.put("doc_type", docType == null ? "text" : docType);
                            if (!"pdf".equals(docType)) {
                                entry.put("old_content", rs.getString("old_content"));
                            }
                            entry.put("changed_by", rs.getString("changed_by"));
                            entry.put("changed_at", rs.getTimestamp("changed_at").toInstant().toString());
                        }
                    }
                }
            }
            sendJsonResponse(t, 200, mapper.writeValueAsString(result));
        }

        private void handleGetPdfAnnotations(HttpExchange t, String docId) throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            ArrayNode result = mapper.createArrayNode();

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query =
                    "SELECT annotation_id, annotation_type, page_number, x_position, y_position, " +
                    "annotation_width, annotation_height, comment_text, created_by, created_at " +
                    "FROM pdf_annotations WHERE doc_id = ? ORDER BY created_at, annotation_id";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, docId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ObjectNode annotation = result.addObject();
                            annotation.put("id", rs.getString("annotation_id"));
                            annotation.put("type", rs.getString("annotation_type"));
                            annotation.put("page", rs.getInt("page_number"));
                            annotation.put("x", rs.getDouble("x_position"));
                            annotation.put("y", rs.getDouble("y_position"));

                            double width = rs.getDouble("annotation_width");
                            if (!rs.wasNull()) {
                                annotation.put("width", width);
                            }
                            double height = rs.getDouble("annotation_height");
                            if (!rs.wasNull()) {
                                annotation.put("height", height);
                            }

                            String commentText = rs.getString("comment_text");
                            if (commentText != null) {
                                annotation.put("text", commentText);
                            }
                            annotation.put("createdBy", rs.getString("created_by"));
                            annotation.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                            annotation.put("saved", true);
                        }
                    }
                }
            }

            sendJsonResponse(t, 200, mapper.writeValueAsString(result));
        }

        private void handleUploadPdf(HttpExchange t, String username) throws Exception {
            InputStream is = t.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(body);
            String filename = jsonNode.has("filename") ? jsonNode.get("filename").asText() : "";
            String fileData = jsonNode.has("fileData") ? jsonNode.get("fileData").asText() : "";
            
            if (filename.trim().isEmpty() || fileData.trim().isEmpty()) {
                sendJsonResponse(t, 400, "{\"error\": \"Filename and file data are required\"}");
                return;
            }

            // Create uploads directory if it doesn't exist
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            String docId = UUID.randomUUID().toString();
            String savedFilename = docId + ".pdf";
            File outputFile = new File(uploadDir, savedFilename);

            // Decode Base64 and save to filesystem
            byte[] fileBytes = Base64.getDecoder().decode(fileData);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(fileBytes);
            }
            System.out.println("[Upload] Saved PDF: " + outputFile.getAbsolutePath() + " (" + fileBytes.length + " bytes)");

            // Save metadata to database
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sql = "INSERT INTO documents (doc_id, title, content, doc_type, file_path, last_updated_by) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, docId);
                    pstmt.setString(2, filename);
                    pstmt.setString(3, "PDF Document");
                    pstmt.setString(4, "pdf");
                    pstmt.setString(5, outputFile.getAbsolutePath());
                    pstmt.setString(6, username);
                    pstmt.executeUpdate();
                }
            }

            sendJsonResponse(t, 201, "{\"message\": \"PDF uploaded successfully\", \"doc_id\": \"" + docId + "\"}");
        }

        private void handleDownloadPdf(HttpExchange t, String docId) throws Exception {
            String filePath = null;
            String title = null;

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query = "SELECT title, file_path FROM documents WHERE doc_id = ? AND doc_type = 'pdf'";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, docId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            title = rs.getString("title");
                            filePath = rs.getString("file_path");
                        } else {
                            sendJsonResponse(t, 404, "{\"error\": \"PDF not found\"}");
                            return;
                        }
                    }
                }
            }

            File file = new File(filePath);
            if (!file.exists()) {
                sendJsonResponse(t, 404, "{\"error\": \"File not found on disk\"}");
                return;
            }

            // Send the PDF file as binary response
            t.getResponseHeaders().set("Content-Type", "application/pdf");
            t.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + title + "\"");
            t.sendResponseHeaders(200, file.length());

            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = t.getResponseBody()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
            }
        }

        private void handleDownloadPdfHistory(HttpExchange t, String docId, String historyIdText) throws Exception {
            int historyId;
            try {
                historyId = Integer.parseInt(historyIdText);
            } catch (NumberFormatException e) {
                sendJsonResponse(t, 400, "{\"error\": \"Invalid history version\"}");
                return;
            }

            String filePath = null;
            String title = null;
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query =
                    "SELECT h.old_title, h.old_content FROM document_history h " +
                    "JOIN documents d ON d.doc_id = h.doc_id " +
                    "WHERE h.history_id = ? AND h.doc_id = ? AND d.doc_type = 'pdf'";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setInt(1, historyId);
                    stmt.setString(2, docId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            title = rs.getString("old_title");
                            filePath = rs.getString("old_content");
                        } else {
                            sendJsonResponse(t, 404, "{\"error\": \"PDF history version not found\"}");
                            return;
                        }
                    }
                }
            }

            File file = new File(filePath);
            if (!file.isFile()) {
                sendJsonResponse(t, 404, "{\"error\": \"PDF history file not found on disk\"}");
                return;
            }

            String downloadTitle = title == null || title.trim().isEmpty() ? "previous-version.pdf" : title;
            if (!downloadTitle.toLowerCase().endsWith(".pdf")) {
                downloadTitle += ".pdf";
            }
            t.getResponseHeaders().set("Content-Type", "application/pdf");
            t.getResponseHeaders().set(
                "Content-Disposition",
                "attachment; filename=\"" + downloadTitle.replace("\"", "") + "\""
            );
            t.sendResponseHeaders(200, file.length());

            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = t.getResponseBody()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
            }
        }

        private void handleAnnotatePdf(HttpExchange t, String docId, String username) throws Exception {
            // 1. Read annotation data from request
            InputStream is = t.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(body);
            JsonNode annotations = jsonNode.get("annotations");

            if (annotations == null || !annotations.isArray() || annotations.size() == 0) {
                sendJsonResponse(t, 400, "{\"error\": \"No annotations provided\"}");
                return;
            }

            Path temporaryFile = null;
            Path backupFile = null;
            Path currentPath = null;
            boolean originalWasReplaced = false;
            boolean saveCommitted = false;

            // Keep the document row locked until both the PDF and its history row
            // are safely updated. This also prevents two annotation requests from
            // overwriting one another.
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                conn.setAutoCommit(false);
                try {
                    // 2. Fetch and lock the current PDF record.
                    String filePath = null;
                    String title = null;
                    String query = "SELECT title, file_path FROM documents WHERE doc_id = ? AND doc_type = 'pdf' FOR UPDATE";
                    try (PreparedStatement stmt = conn.prepareStatement(query)) {
                        stmt.setString(1, docId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                title = rs.getString("title");
                                filePath = rs.getString("file_path");
                            } else {
                                conn.rollback();
                                sendJsonResponse(t, 404, "{\"error\": \"PDF document not found\"}");
                                return;
                            }
                        }
                    }

                    if (filePath == null || filePath.trim().isEmpty()) {
                        conn.rollback();
                        sendJsonResponse(t, 404, "{\"error\": \"PDF file path is missing\"}");
                        return;
                    }

                    File currentFile = new File(filePath);
                    if (!currentFile.isFile()) {
                        conn.rollback();
                        sendJsonResponse(t, 404, "{\"error\": \"PDF file not found on disk\"}");
                        return;
                    }
                    currentPath = currentFile.toPath();
                    Path parentDirectory = currentPath.toAbsolutePath().getParent();
                    if (parentDirectory == null) {
                        throw new IOException("Could not determine the PDF directory");
                    }

                    // Give every annotation a stable ID. If a successful response
                    // was lost and the client retries the same request, return
                    // success without embedding or storing the annotations twice.
                    boolean anyAnnotationAlreadySaved = false;
                    boolean allAnnotationsAlreadySaved = true;
                    String annotationExistsSql =
                        "SELECT 1 FROM pdf_annotations WHERE annotation_id = ? AND doc_id = ?";
                    try (PreparedStatement existsStmt = conn.prepareStatement(annotationExistsSql)) {
                        for (JsonNode ann : annotations) {
                            String annotationId = normalizeAnnotationId(ann);
                            existsStmt.setString(1, annotationId);
                            existsStmt.setString(2, docId);
                            try (ResultSet rs = existsStmt.executeQuery()) {
                                boolean exists = rs.next();
                                anyAnnotationAlreadySaved |= exists;
                                allAnnotationsAlreadySaved &= exists;
                            }
                        }
                    }
                    if (allAnnotationsAlreadySaved) {
                        conn.rollback();
                        sendJsonResponse(t, 200, "{\"message\": \"Annotations were already saved\", \"alreadySaved\": true}");
                        return;
                    }
                    if (anyAnnotationAlreadySaved) {
                        throw new IllegalStateException("Annotation request contains a mixture of saved and unsaved IDs");
                    }

                    // 3. Build the complete annotated PDF in a separate file.
                    // PDFBox 3 must not save back to the same file it loaded.
                    temporaryFile = Files.createTempFile(parentDirectory, docId + "_annotated_", ".pdf.tmp");
                    try (PDDocument document = Loader.loadPDF(currentFile)) {
                        for (JsonNode ann : annotations) {
                            String type = ann.hasNonNull("type") ? ann.get("type").asText() : "";
                            int pageNum = ann.hasNonNull("page") ? ann.get("page").asInt() - 1 : -1;

                            if (pageNum < 0 || pageNum >= document.getNumberOfPages()) {
                                throw new IllegalArgumentException("Invalid annotation page: " + (pageNum + 1));
                            }

                            PDPage page = document.getPage(pageNum);
                            PDRectangle mediaBox = page.getMediaBox();
                            float pageHeight = mediaBox.getHeight();

                            if ("comment".equals(type)) {
                                float x = requireNumber(ann, "x");
                                float y = requireNumber(ann, "y");
                                String text = ann.hasNonNull("text") ? ann.get("text").asText().trim() : "";
                                if (text.isEmpty()) {
                                    throw new IllegalArgumentException("Comment text is required");
                                }

                                // Convert from top-left origin (browser) to bottom-left origin (PDF).
                                float pdfY = pageHeight - y;

                                PDAnnotationText comment = new PDAnnotationText();
                                comment.setContents(text);
                                comment.setRectangle(new PDRectangle(x, pdfY - 20, 20, 20));
                                comment.setName(PDAnnotationText.NAME_COMMENT);
                                comment.setTitlePopup(username);
                                page.getAnnotations().add(comment);
                                comment.constructAppearances(document);

                            } else if ("highlight".equals(type)) {
                                float x = requireNumber(ann, "x");
                                float y = requireNumber(ann, "y");
                                float width = requireNumber(ann, "width");
                                float height = requireNumber(ann, "height");
                                if (width <= 0 || height <= 0) {
                                    throw new IllegalArgumentException("Highlight dimensions must be positive");
                                }

                                // Convert from top-left origin (browser) to bottom-left origin (PDF).
                                float pdfY = pageHeight - y - height;

                                PDAnnotationHighlight highlight = new PDAnnotationHighlight();
                                PDRectangle rect = new PDRectangle(x, pdfY, width, height);
                                highlight.setRectangle(rect);
                                highlight.setQuadPoints(new float[] {
                                    rect.getLowerLeftX(), rect.getUpperRightY(),
                                    rect.getUpperRightX(), rect.getUpperRightY(),
                                    rect.getLowerLeftX(), rect.getLowerLeftY(),
                                    rect.getUpperRightX(), rect.getLowerLeftY()
                                });
                                highlight.setColor(new PDColor(new float[]{1, 1, 0}, PDDeviceRGB.INSTANCE));
                                highlight.setConstantOpacity(0.3f);
                                highlight.setContents("Highlight");
                                page.getAnnotations().add(highlight);
                                highlight.constructAppearances(document);
                            } else {
                                throw new IllegalArgumentException("Unsupported annotation type: " + type);
                            }
                        }
                        document.save(temporaryFile.toFile());
                    }

                    // Reopen the generated file before touching the original. A corrupt
                    // or incomplete output therefore cannot become the current version.
                    try (PDDocument validationDocument = Loader.loadPDF(temporaryFile.toFile())) {
                        if (validationDocument.getNumberOfPages() == 0) {
                            throw new IOException("Generated PDF contains no pages");
                        }
                    }

                    // 4. Only now create one restorable history version.
                    String backupName = docId + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID() + ".pdf";
                    backupFile = parentDirectory.resolve(backupName);
                    Files.copy(currentPath, backupFile);

                    String insertHistory = "INSERT INTO document_history (doc_id, old_title, old_content, changed_by) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(insertHistory)) {
                        pstmt.setString(1, docId);
                        pstmt.setString(2, title);
                        pstmt.setString(3, backupFile.toAbsolutePath().toString());
                        pstmt.setString(4, username);
                        pstmt.executeUpdate();
                    }

                    // 5. Persist the same annotations that were embedded in the PDF.
                    String insertAnnotation =
                        "INSERT INTO pdf_annotations " +
                        "(annotation_id, doc_id, annotation_type, page_number, x_position, y_position, " +
                        "annotation_width, annotation_height, comment_text, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(insertAnnotation)) {
                        for (JsonNode ann : annotations) {
                            String annotationType = ann.get("type").asText();
                            pstmt.setString(1, ann.get("id").asText());
                            pstmt.setString(2, docId);
                            pstmt.setString(3, annotationType);
                            pstmt.setInt(4, ann.get("page").asInt());
                            pstmt.setDouble(5, ann.get("x").asDouble());
                            pstmt.setDouble(6, ann.get("y").asDouble());

                            if ("highlight".equals(annotationType)) {
                                pstmt.setDouble(7, ann.get("width").asDouble());
                                pstmt.setDouble(8, ann.get("height").asDouble());
                                pstmt.setNull(9, java.sql.Types.LONGVARCHAR);
                            } else {
                                pstmt.setNull(7, java.sql.Types.DOUBLE);
                                pstmt.setNull(8, java.sql.Types.DOUBLE);
                                pstmt.setString(9, ann.get("text").asText());
                            }
                            pstmt.setString(10, username);
                            pstmt.addBatch();
                        }
                        pstmt.executeBatch();
                    }

                    // 6. Atomically replace the current PDF when the filesystem allows it.
                    moveReplacing(temporaryFile, currentPath);
                    originalWasReplaced = true;
                    temporaryFile = null;

                    String updateSql = "UPDATE documents SET last_updated_by = ?, last_updated_at = CURRENT_TIMESTAMP WHERE doc_id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                        pstmt.setString(1, username);
                        pstmt.setString(2, docId);
                        if (pstmt.executeUpdate() != 1) {
                            throw new IOException("PDF document metadata was not updated");
                        }
                    }

                    conn.commit();
                    saveCommitted = true;
                    System.out.println("[Annotate] Saved annotated PDF: " + currentPath.toAbsolutePath());
                } catch (Exception e) {
                    try {
                        conn.rollback();
                    } catch (Exception rollbackError) {
                        e.addSuppressed(rollbackError);
                    }

                    // If replacement happened but the database operation failed, put
                    // the exact previous version back before reporting the failure.
                    if (originalWasReplaced && backupFile != null && currentPath != null && Files.exists(backupFile)) {
                        try {
                            Files.copy(backupFile, currentPath, StandardCopyOption.REPLACE_EXISTING);
                            originalWasReplaced = false;
                        } catch (Exception restoreError) {
                            e.addSuppressed(restoreError);
                        }
                    }
                    throw e;
                } finally {
                    try {
                        conn.setAutoCommit(true);
                    } catch (Exception ignored) {
                        // The connection is about to close.
                    }
                }
            } finally {
                if (temporaryFile != null) {
                    Files.deleteIfExists(temporaryFile);
                }
                if (!saveCommitted && backupFile != null) {
                    Files.deleteIfExists(backupFile);
                }
            }

            sendJsonResponse(t, 200, "{\"message\": \"PDF annotated successfully\"}");
        }

        private float requireNumber(JsonNode annotation, String fieldName) {
            JsonNode value = annotation.get(fieldName);
            if (value == null || !value.isNumber()) {
                throw new IllegalArgumentException("Annotation field '" + fieldName + "' must be a number");
            }
            return (float) value.asDouble();
        }

        private String normalizeAnnotationId(JsonNode annotation) {
            String annotationId = annotation.hasNonNull("id")
                ? annotation.get("id").asText().trim()
                : UUID.randomUUID().toString();
            try {
                annotationId = UUID.fromString(annotationId).toString();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Annotation ID must be a UUID");
            }
            if (annotation instanceof ObjectNode) {
                ((ObjectNode) annotation).put("id", annotationId);
            }
            return annotationId;
        }

        private void moveReplacing(Path source, Path target) throws IOException {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
