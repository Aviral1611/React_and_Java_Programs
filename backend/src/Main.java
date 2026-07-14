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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Main {
    // Database configuration
    private static final String DB_URL = "jdbc:mysql://localhost:3306/react_java_auth";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        server.createContext("/api/hello", new HelloHandler());
        server.createContext("/api/login", new LoginHandler());
        
        server.setExecutor(null); // creates a default executor
        System.out.println("Server is starting on port 8080...");
        server.start();
    }
    
    // Utility method to add CORS headers
    private static void setCorsHeaders(HttpExchange t) {
        t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        t.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    static class HelloHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            setCorsHeaders(t);
            if ("OPTIONS".equals(t.getRequestMethod())) {
                t.sendResponseHeaders(204, -1);
                return;
            }
            
            String response = "{\"message\": \"Hello from the Java Backend!\"}";
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(200, response.getBytes().length);
            
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            setCorsHeaders(t);
            if ("OPTIONS".equals(t.getRequestMethod())) {
                t.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equals(t.getRequestMethod())) {
                InputStream is = t.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                // Use Jackson to parse JSON
                ObjectMapper mapper = new ObjectMapper();
                JsonNode jsonNode = mapper.readTree(body);
                String username = jsonNode.has("username") ? jsonNode.get("username").asText() : null;
                String password = jsonNode.has("password") ? jsonNode.get("password").asText() : null;

                if (username != null && password != null && authenticateUser(username, password)) {
                    String response = "{\"token\": \"mock-jwt-token-123\", \"role\": \"USER\", \"username\": \"" + username + "\"}";
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(200, response.getBytes().length);
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } else {
                    String response = "{\"error\": \"Invalid credentials\"}";
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(401, response.getBytes().length);
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            } else {
                t.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
        

        private boolean authenticateUser(String username, String password) {
            // NOTE: Ensure mysql-connector-j.jar is in Eclipse build path
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query = "SELECT password FROM users WHERE username = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, username);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String dbPassword = rs.getString("password");
                            // Direct string comparison since DB stores plain text currently as per setup.sql
                            return password.equals(dbPassword);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    }
}
