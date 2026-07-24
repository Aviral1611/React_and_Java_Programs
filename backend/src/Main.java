package backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

public class Main {
    private static String DB_URL;
    private static String DB_USER;
    private static String DB_PASSWORD;
    private static String JWT_SECRET;
    private static long JWT_EXPIRATION;

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
                // Route: POST /api/documents (Create new document)
                else if (method.equals("POST") && path.equals("/api/documents")) {
                    handlePostDocument(t, username);
                }
                // Route: GET /api/documents/{id} or /api/documents/{id}/history
                else if (method.equals("GET") && path.startsWith("/api/documents/")) {
                    String suffix = path.substring("/api/documents/".length());
                    if (suffix.endsWith("/history")) {
                        String id = suffix.substring(0, suffix.length() - "/history".length());
                        handleGetHistory(t, id);
                    } else {
                        handleGetDocument(t, suffix);
                    }
                }
                // Route: PUT /api/documents/{id} (Update specific document)
                else if (method.equals("PUT") && path.startsWith("/api/documents/")) {
                    String id = path.substring("/api/documents/".length());
                    handlePutDocument(t, id, username);
                }
                else {
                    // We will add /history here later
                    sendJsonResponse(t, 404, "{\"error\": \"Not Found\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonResponse(t, 500, "{\"error\": \"Internal server error\"}");
            }
        }

        private void handleGetDocuments(HttpExchange t) throws Exception {
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("[");
            
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query = "SELECT doc_id, title, last_updated_by, last_updated_at FROM documents ORDER BY last_updated_at DESC";
                try (PreparedStatement stmt = conn.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {
                    
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) {
                            jsonBuilder.append(",");
                        }
                        first = false;
                        jsonBuilder.append("{");
                        jsonBuilder.append("\"doc_id\":\"").append(rs.getString("doc_id")).append("\",");
                        jsonBuilder.append("\"title\":\"").append(rs.getString("title").replace("\"", "\\\"")).append("\",");
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
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("[");
            
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query = "SELECT old_title, old_content, changed_by, changed_at FROM document_history WHERE doc_id = ? ORDER BY changed_at DESC";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, docId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        boolean first = true;
                        while (rs.next()) {
                            if (!first) {
                                jsonBuilder.append(",");
                            }
                            first = false;
                            jsonBuilder.append("{");
                            jsonBuilder.append("\"old_title\":\"").append(rs.getString("old_title").replace("\"", "\\\"")).append("\",");
                            jsonBuilder.append("\"old_content\":\"").append(rs.getString("old_content").replace("\"", "\\\"").replace("\n", "\\n")).append("\",");
                            jsonBuilder.append("\"changed_by\":\"").append(rs.getString("changed_by")).append("\",");
                            jsonBuilder.append("\"changed_at\":\"").append(rs.getTimestamp("changed_at")).append("\"");
                            jsonBuilder.append("}");
                        }
                    }
                }
            }
            jsonBuilder.append("]");
            sendJsonResponse(t, 200, jsonBuilder.toString());
        }
    }
}
