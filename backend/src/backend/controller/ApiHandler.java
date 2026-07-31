package backend.controller;

import backend.exception.ApiException;
import backend.util.HttpUtil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * Common HTTP lifecycle: CORS, preflight handling, and consistent errors.
 */
public abstract class ApiHandler implements HttpHandler {
    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        HttpUtil.setCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendNoContent(exchange);
            return;
        }

        try {
            handleRequest(exchange);
        } catch (ApiException e) {
            HttpUtil.sendError(exchange, e.getStatusCode(), e.getMessage());
        } catch (LinkageError e) {
            System.err.println(
                "[API] Runtime dependency error while handling " +
                exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
            );
            e.printStackTrace();
            HttpUtil.sendError(
                exchange,
                500,
                "Backend PDF library is incomplete or has conflicting JAR versions. " +
                "Check the Eclipse console."
            );
        } catch (Exception e) {
            System.err.println(
                "[API] Request failed while handling " +
                exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
            );
            e.printStackTrace();
            HttpUtil.sendError(exchange, 500, "Internal server error");
        }
    }

    protected abstract void handleRequest(HttpExchange exchange) throws Exception;
}
