package backend.controller;

import backend.exception.ApiException;
import backend.security.JwtService;

import com.sun.net.httpserver.HttpExchange;

public abstract class AuthenticatedHandler extends ApiHandler {
    private final JwtService jwtService;

    protected AuthenticatedHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected final void handleRequest(HttpExchange exchange) throws Exception {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(401, "Missing or invalid Authorization header");
        }

        String username = jwtService.validateAndGetUsername(authorization.substring(7));
        if (username == null) {
            throw new ApiException(401, "Invalid or expired token");
        }
        handleAuthenticated(exchange, username);
    }

    protected abstract void handleAuthenticated(HttpExchange exchange, String username) throws Exception;
}
