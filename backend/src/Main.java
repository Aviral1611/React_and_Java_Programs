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
import java.util.Date;
import java.util.Properties;
import javax.crypto.SecretKey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

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
        
        server.setExecutor(null); // creates a default executor
        System.out.println("Server is starting on port 8080...");
        server.start();
    }
    
    private static void loadProperties() {
        Properties prop = new Properties();
        // Load using ClassLoader so the file can just be placed in the src folder
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("Sorry, unable to find config.properties in the classpath (make sure it's in the 'src' folder)");
                return;
            }
            prop.load(input);
            DB_URL = prop.getProperty("db.url");
            DB_USER = prop.getProperty("db.user");
            DB_PASSWORD = prop.getProperty("db.password");
            JWT_SECRET = prop.getProperty("jwt.secret");
            JWT_EXPIRATION = Long.parseLong(prop.getProperty("jwt.expiration", "3600000"));
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Failed to load config.properties.");
        }
    }

    private static void setCorsHeaders(HttpExchange t) {
        t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        t.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

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
                t.sendResponseHeaders(401, -1);
                return;
            }
            
            String token = authHeader.substring(7);
            try {
                SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
                Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            } catch (Exception e) {
                // Invalid or expired token
                t.sendResponseHeaders(401, -1);
                return;
            }
            
            String response = "{\"message\": \"Hello from the protected Java Backend!\"}";
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
                        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
                        String token = Jwts.builder()
                                .setSubject(username)
                                .claim("role", "USER")
                                .setIssuedAt(new Date())
                                .setExpiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION))
                                .signWith(key)
                                .compact();
                        System.out.println("[LoginHandler] JWT generated successfully, length: " + token.length());
                                
                        String response = "{\"token\": \"" + token + "\", \"role\": \"USER\", \"username\": \"" + username + "\"}";
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        System.out.println("[LoginHandler] Response size: " + responseBytes.length + " bytes");
                        
                        t.getResponseHeaders().set("Content-Type", "application/json");
                        t.sendResponseHeaders(200, responseBytes.length);
                        System.out.println("[LoginHandler] Headers sent");
                        
                        OutputStream os = t.getResponseBody();
                        os.write(responseBytes);
                        os.flush();
                        os.close();
                        System.out.println("[LoginHandler] Response sent successfully!");
                    } else {
                        System.out.println("[LoginHandler] Authentication FAILED for user: " + username);
                        String response = "{\"error\": \"Invalid credentials\"}";
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        t.getResponseHeaders().set("Content-Type", "application/json");
                        t.sendResponseHeaders(401, responseBytes.length);
                        OutputStream os = t.getResponseBody();
                        os.write(responseBytes);
                        os.flush();
                        os.close();
                    }
                } catch (Exception e) {
                    System.err.println("[LoginHandler] CRASH during request handling:");
                    e.printStackTrace();
                    try {
                        String response = "{\"error\": \"Internal server error\"}";
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        t.getResponseHeaders().set("Content-Type", "application/json");
                        t.sendResponseHeaders(500, responseBytes.length);
                        OutputStream os = t.getResponseBody();
                        os.write(responseBytes);
                        os.flush();
                        os.close();
                    } catch (Exception ex) {
                        System.err.println("[LoginHandler] Could not send error response either:");
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
                e.printStackTrace();
            }
            return false;
        }
    }
}
