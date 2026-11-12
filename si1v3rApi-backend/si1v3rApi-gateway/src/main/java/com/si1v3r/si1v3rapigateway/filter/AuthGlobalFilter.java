package com.si1v3r.si1v3rapigateway.filter;

import com.si1v3r.si1v3rapiclientsdk.utils.SignUtils;
import com.si1v3r.si1v3rapicommon.model.InvokeUser;
import com.si1v3r.si1v3rapicommon.service.InnerUserService;
import com.si1v3r.si1v3rapigateway.support.GatewayAttrs;
import com.si1v3r.si1v3rapigateway.support.GatewayErrors;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    private static final Logger LOG = LoggerFactory.getLogger(AuthGlobalFilter.class);
    private static final int NONCE_LIMIT = 100000;
    @DubboReference(check = false)
    private InnerUserService innerUserService;
    private final Clock clock;
    private final int nonceLimit;
    private final Map<String, Long> nonces = new HashMap<>();

    public AuthGlobalFilter() { this.clock = Clock.systemUTC(); this.nonceLimit = NONCE_LIMIT; }
    AuthGlobalFilter(InnerUserService service, Clock clock) {
        this(service, clock, NONCE_LIMIT);
    }
    AuthGlobalFilter(InnerUserService service, Clock clock, int nonceLimit) {
        this.nonceLimit = nonceLimit;
        this.innerUserService = service;
        this.clock = clock;
    }

    @Override
    public int getOrder() { return -100; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long started = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();
        String key = header(request, "assessKey");
        String timestamp = header(request, "timestamp");
        String nonce = header(request, "nonce");
        String signature = header(request, "sign");
        Mono<Void> result;
        if (key == null || key.isEmpty() || !key.matches("[\\x21-\\x7e]+")
                || timestamp == null || !timestamp.matches("[0-9]+")
                || nonce == null || !nonce.matches("[0-9a-f]{32}")
                || signature == null || !signature.matches("[0-9a-f]{64}") || !fresh(timestamp)) {
            result = unauthorized(exchange);
        } else {
            result = DataBufferUtils.join(request.getBody()).map(buffer -> {
                try {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return bytes;
                } finally { DataBufferUtils.release(buffer); }
            }).defaultIfEmpty(new byte[0])
                    .onErrorResume(error -> GatewayErrors.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR,
                            50000, "Internal server error").then(Mono.empty()))
                    .flatMap(body -> Mono.fromCallable(() -> authenticate(request, body, key, timestamp, nonce, signature))
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorReturn(HttpStatus.INTERNAL_SERVER_ERROR)
                            .flatMap(status -> {
                                if (status instanceof AuthSuccess) {
                                    exchange.getAttributes().put(GatewayAttrs.INVOKE_USER, ((AuthSuccess) status).user);
                                    ServerHttpRequestDecorator cached = new ServerHttpRequestDecorator(request) {
                                        @Override
                                        public Flux<DataBuffer> getBody() {
                                            return Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(body)));
                                        }
                                    };
                                    return chain.filter(exchange.mutate().request(cached).build());
                                }
                                HttpStatus error = (HttpStatus) status;
                                return GatewayErrors.write(exchange, error, error.value() * 100,
                                        error == HttpStatus.FORBIDDEN ? "Forbidden" : error == HttpStatus.UNAUTHORIZED
                                                ? "Unauthorized" : "Internal server error");
                            }));
        }
        return result.doFinally(signal -> LOG.info("requestId={} method={} path={} assessKey={} result={} elapsedMs={}",
                request.getId(), request.getMethodValue(), request.getURI().getRawPath(),
                key == null ? "" : key.replaceAll("[\\p{Cntrl}]", ""),
                exchange.getResponse().getStatusCode() == null ? 200 : exchange.getResponse().getStatusCode().value(),
                (System.nanoTime() - started) / 1000000));
    }

    private Object authenticate(ServerHttpRequest request, byte[] body, String key, String timestamp,
                                String nonce, String signature) {
        InvokeUser user = innerUserService.getInvokeUser(key);
        if (user == null) { return HttpStatus.UNAUTHORIZED; }
        String expected = SignUtils.sign(request.getMethodValue(), request.getURI().getRawPath(),
                request.getURI().getRawQuery(), body, key, timestamp, nonce, user.getSecretKey());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII))) {
            return HttpStatus.UNAUTHORIZED;
        }
        if ("ban".equals(user.getUserRole())) { return HttpStatus.FORBIDDEN; }
        synchronized (nonces) {
            long now = clock.instant().getEpochSecond();
            nonces.entrySet().removeIf(entry -> entry.getValue() <= now);
            String cacheKey = key + ":" + nonce;
            if (nonces.containsKey(cacheKey)) { return HttpStatus.UNAUTHORIZED; }
            // Never evict a live nonce: fail closed when capacity is exhausted.
            if (nonces.size() >= nonceLimit) { return HttpStatus.INTERNAL_SERVER_ERROR; }
            nonces.put(cacheKey, now + 300);
        }
        return new AuthSuccess(user);
    }

    private boolean fresh(String timestamp) {
        try {
            long ts = Long.parseLong(timestamp);
            long now = clock.instant().getEpochSecond();
            return ts >= now - 300 && ts <= now + 300;
        } catch (NumberFormatException e) { return false; }
    }

    private static String header(ServerHttpRequest request, String name) {
        List<String> values = request.getHeaders().get(name);
        return values == null || values.size() != 1 ? null : values.get(0);
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        return GatewayErrors.write(exchange, HttpStatus.UNAUTHORIZED, 40100, "Unauthorized");
    }

    private static final class AuthSuccess {
        private final InvokeUser user;
        private AuthSuccess(InvokeUser user) { this.user = user; }
    }
}
