package backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class HttpUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpUtil() {
    }

    public static void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    public static String readRequestBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    public static void sendJson(HttpExchange exchange, int statusCode, Object value) throws IOException {
        sendJsonText(exchange, statusCode, MAPPER.writeValueAsString(value));
    }

    public static void sendJsonText(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    public static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        sendJson(exchange, statusCode, MAPPER.createObjectNode().put("error", message));
    }

    public static void sendNoContent(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    public static void sendPdf(HttpExchange exchange, File file, String title, boolean inline) throws IOException {
        String safeTitle = sanitizeFilename(title);
        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.getResponseHeaders().set(
            "Content-Disposition",
            (inline ? "inline" : "attachment") + "; filename=\"" + safeTitle + "\""
        );
        exchange.sendResponseHeaders(200, file.length());

        try (FileInputStream input = new FileInputStream(file);
             OutputStream output = exchange.getResponseBody()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
    }

    private static String sanitizeFilename(String title) {
        String safe = title == null || title.trim().isEmpty() ? "document.pdf" : title.trim();
        safe = safe.replace("\"", "").replace("\r", "").replace("\n", "");
        if (!safe.toLowerCase().endsWith(".pdf")) {
            safe += ".pdf";
        }
        return safe;
    }
}
