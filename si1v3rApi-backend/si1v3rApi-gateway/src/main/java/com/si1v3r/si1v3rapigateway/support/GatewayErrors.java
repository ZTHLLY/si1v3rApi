package com.si1v3r.si1v3rapigateway.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GatewayErrors {
    private static final ObjectMapper JSON = new ObjectMapper();
    private GatewayErrors() { }

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        final byte[] bytes;
        try {
            bytes = JSON.writeValueAsBytes(error);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}
