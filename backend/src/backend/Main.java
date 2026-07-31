package backend;

import backend.config.AppConfig;
import backend.controller.DocumentController;
import backend.controller.HelloController;
import backend.controller.LoginController;
import backend.dao.DocumentDao;
import backend.dao.HistoryDao;
import backend.dao.PdfAnnotationDao;
import backend.dao.UserDao;
import backend.db.Database;
import backend.security.JwtService;
import backend.service.AuthService;
import backend.service.DocumentService;
import backend.service.PdfAnnotationService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application bootstrap only. Business logic belongs in services, SQL in DAOs,
 * and HTTP request handling in controllers.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load();
        ObjectMapper mapper = new ObjectMapper();
        Database database = new Database(config);

        UserDao userDao = new UserDao(database);
        DocumentDao documentDao = new DocumentDao(database);
        HistoryDao historyDao = new HistoryDao(database);
        PdfAnnotationDao annotationDao = new PdfAnnotationDao(database);

        AuthService authService = new AuthService(userDao);
        DocumentService documentService = new DocumentService(
            config,
            database,
            documentDao,
            historyDao,
            annotationDao
        );
        PdfAnnotationService annotationService = new PdfAnnotationService(
            database,
            documentDao,
            historyDao,
            annotationDao
        );
        JwtService jwtService = new JwtService(config, mapper);

        HttpServer server = HttpServer.create(
            new InetSocketAddress(config.getServerPort()),
            0
        );
        server.createContext("/api/hello", new HelloController(jwtService, mapper));
        server.createContext("/api/login", new LoginController(authService, jwtService, mapper));
        server.createContext(
            "/api/documents",
            new DocumentController(
                jwtService,
                documentService,
                annotationService,
                mapper
            )
        );

        int threadCount = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        server.setExecutor(executor);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            executor.shutdown();
        }));

        server.start();
        System.out.println("[Config] Properties loaded successfully.");
        System.out.println("[Server] Listening on http://localhost:" + config.getServerPort());
        System.out.println("[Server] PDF storage: " + config.getUploadDirectory());
    }
}
