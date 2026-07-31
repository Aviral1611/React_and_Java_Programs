package backend.controller;

import backend.security.JwtService;
import backend.util.HttpUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

public final class HelloController extends AuthenticatedHandler {
    private final ObjectMapper mapper;

    public HelloController(JwtService jwtService, ObjectMapper mapper) {
        super(jwtService);
        this.mapper = mapper;
    }

    @Override
    protected void handleAuthenticated(HttpExchange exchange, String username) throws Exception {
        HttpUtil.sendJson(
            exchange,
            200,
            mapper.createObjectNode()
                .put("message", "Hello from the protected Java Backend!")
                .put("user", username)
        );
    }
}
