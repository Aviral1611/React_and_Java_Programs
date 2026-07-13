import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
// import com.sun.net.httpserver.HttpsServer;
// import com.sun.net.httpserver.HttpsConfigurator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Main {
    public static void main(String[] args) throws Exception {
        // Create an HTTP server listening on port 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        // Note: If you specifically need HTTPS, use HttpsServer instead and configure SSLContext
        // HttpsServer server = HttpsServer.create(new InetSocketAddress(8443), 0);
        // server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        
        server.createContext("/api/hello", new HelloHandler());
        server.setExecutor(null); // creates a default executor
        System.out.println("Server is starting on port 8080...");
        server.start();
    }

    static class HelloHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            // Enable CORS for frontend requests
            t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            
            String response = "{\"message\": \"Hello from the Java Backend!\"}";
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(200, response.getBytes().length);
            
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
