package backend.controller;

import backend.exception.ApiException;
import backend.model.UserAccount;
import backend.security.JwtService;
import backend.service.AuthService;
import backend.util.HttpUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

public final class LoginController extends ApiHandler {
    private final AuthService authService;
    private final JwtService jwtService;
    private final ObjectMapper mapper;

    public LoginController(
            AuthService authService,
            JwtService jwtService,
            ObjectMapper mapper) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.mapper = mapper;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws Exception {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            throw new ApiException(405, "Method Not Allowed");
        }

        JsonNode request = mapper.readTree(HttpUtil.readRequestBody(exchange));
        String username = text(request, "username");
        String password = text(request, "password");
        UserAccount account = authService.authenticate(username, password);
        if (account == null) {
            throw new ApiException(401, "Invalid credentials");
        }

        String role = account.getRole() == null ? "USER" : account.getRole();
        String token = jwtService.generate(account.getUsername(), role);
        HttpUtil.sendJson(
            exchange,
            200,
            mapper.createObjectNode()
                .put("token", token)
                .put("role", role)
                .put("username", account.getUsername())
        );
    }

    private String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
