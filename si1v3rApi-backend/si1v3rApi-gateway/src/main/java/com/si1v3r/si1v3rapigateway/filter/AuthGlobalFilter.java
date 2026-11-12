package com.si1v3r.si1v3rapigateway.filter;

import com.si1v3r.si1v3rapiclientsdk.utils.SignUtils;
import com.si1v3r.si1v3rapicommon.model.InvokeUser;
import com.si1v3r.si1v3rapicommon.service.InnerUserService;
import com.si1v3r.si1v3rapigateway.support.GatewayAttrs;
import com.si1v3r.si1v3rapigateway.support.GatewayErrors;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    private static final Logger LOG = LoggerFactory.getLogger(AuthGlobalFilter.class);
    @DubboReference(check = false)
    private InnerUserService innerUserService;
    @Value("${si1v3r.gateway.max-body-bytes:1048576}")
    private int maxBodyBytes = 1048576;
    @Value("${si1v3r.gateway.nonce-per-key-max:10000}")
    private int noncePerKeyMax = 10000;
    @Value("${si1v3r.gateway.nonce-global-max:500000}")
    private int nonceGlobalMax = 500000;
    private final Clock clock;
    private final Map<String, Set<String>> nonces = new HashMap<>();
    private final PriorityQueue<NonceEntry> expiry = new PriorityQueue<>(Comparator.comparingLong(e -> e.expiresAt));

    public AuthGlobalFilter() { this.clock = Clock.systemUTC(); }
    AuthGlobalFilter(InnerUserService service, Clock clock) {
        this.innerUserService = service;
        this.clock = clock;
    }
    AuthGlobalFilter(InnerUserService service, Clock clock, int perKey, int global, int maxBody) {
        this(service, clock);
        this.noncePerKeyMax = perKey;
        this.nonceGlobalMax = global;
        this.maxBodyBytes = maxBody;
    }

    @Override
    public int getOrder() { return -100; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return Mono.defer(() -> {
            long started = System.nanoTime();
            ServerHttpRequest request = exchange.getRequest();
            String key = header(request, "assessKey");
            String timestamp = header(request, "timestamp");
            String nonce = header(request, "nonce");
            String signature = header(request, "sign");
            Mono<Void> result;
            if (!canonicalPath(request.getURI().getRawPath())) {
                result = error(exchange, HttpStatus.BAD_REQUEST);
            } else if (key == null || !key.matches("[\\x21-\\x7e]+")
                    || timestamp == null || !timestamp.matches("[1-9][0-9]{0,11}")
                    || nonce == null || !nonce.matches("[0-9a-f]{32}")
                    || signature == null || !signature.matches("[0-9a-f]{64}") || !fresh(timestamp)) {
                result = error(exchange, HttpStatus.UNAUTHORIZED);
            } else if (request.getHeaders().getContentLength() > maxBodyBytes) {
                result = error(exchange, HttpStatus.PAYLOAD_TOO_LARGE);
            } else {
                Scheduler original = originalScheduler(request);
                // Only the blocking RPC runs on boundedElastic. Resume the actual channel event loop.
                result = Mono.fromCallable(() -> lookup(key))
                        .subscribeOn(Schedulers.boundedElastic())
                        .publishOn(original)
                        .flatMap(lookup -> lookup.user == null ? error(exchange, lookup.error)
                                : readAndAuthenticate(exchange, chain, lookup.user, key, timestamp, nonce, signature));
            }
            return result.doFinally(signal -> LOG.info("requestId={} method={} path={} assessKey={} result={} elapsedMs={}",
                    request.getId(), request.getMethodValue(), request.getURI().getRawPath(),
                    key == null ? "" : key.replaceAll("[\\p{Cntrl}]", ""),
                    exchange.getResponse().getStatusCode() == null ? 200 : exchange.getResponse().getStatusCode().value(),
                    (System.nanoTime() - started) / 1000000));
        });
    }

    private Lookup lookup(String key) {
        try {
            InvokeUser user = innerUserService.getInvokeUser(key);
            return new Lookup(user, HttpStatus.UNAUTHORIZED);
        } catch (Exception e) {
            return new Lookup(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Mono<Void> readAndAuthenticate(ServerWebExchange exchange, GatewayFilterChain chain, InvokeUser user,
                                          String key, String timestamp, String nonce, String signature) {
        ServerHttpRequest request = exchange.getRequest();
        return DataBufferUtils.join(request.getBody(), maxBodyBytes).map(buffer -> {
            try {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                return bytes;
            } finally { DataBufferUtils.release(buffer); }
        }).defaultIfEmpty(new byte[0])
                .onErrorResume(e -> error(exchange, e instanceof DataBufferLimitException
                        ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.INTERNAL_SERVER_ERROR).then(Mono.empty()))
                .flatMap(body -> {
                    final String expected;
                    try {
                        expected = SignUtils.sign(request.getMethodValue(), request.getURI().getRawPath(),
                                request.getURI().getRawQuery(), body, key, timestamp, nonce, user.getSecretKey());
                    } catch (RuntimeException e) { return error(exchange, HttpStatus.INTERNAL_SERVER_ERROR); }
                    if (!fresh(timestamp) || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                            signature.getBytes(StandardCharsets.US_ASCII))) {
                        return error(exchange, HttpStatus.UNAUTHORIZED);
                    }
                    if ("ban".equals(user.getUserRole())) { return error(exchange, HttpStatus.FORBIDDEN); }
                    HttpStatus rejected = registerNonce(key, nonce, Long.parseLong(timestamp));
                    if (rejected != null) { return error(exchange, rejected); }
                    exchange.getAttributes().put(GatewayAttrs.INVOKE_USER, user);
                    ServerHttpRequestDecorator cached = new ServerHttpRequestDecorator(request) {
                        @Override
                        public HttpHeaders getHeaders() {
                            HttpHeaders headers = new HttpHeaders();
                            headers.putAll(super.getHeaders());
                            for (String name : new String[]{"assessKey", "timestamp", "nonce", "sign"}) headers.remove(name);
                            return HttpHeaders.readOnlyHttpHeaders(headers);
                        }
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(body)));
                        }
                    };
                    return chain.filter(exchange.mutate().request(cached).build());
                });
    }

    private synchronized HttpStatus registerNonce(String key, String nonce, long timestamp) {
        long now = clock.instant().getEpochSecond();
        // Heap cleanup touches only expired entries, never scans live nonces.
        while (!expiry.isEmpty() && expiry.peek().expiresAt <= now) {
            NonceEntry old = expiry.remove();
            Set<String> entries = nonces.get(old.key);
            entries.remove(old.nonce);
            if (entries.isEmpty()) nonces.remove(old.key);
        }
        Set<String> entries = nonces.get(key);
        if (entries != null && entries.contains(nonce)) return HttpStatus.UNAUTHORIZED;
        if (entries != null && entries.size() >= noncePerKeyMax) return HttpStatus.TOO_MANY_REQUESTS;
        if (noncePerKeyMax <= 0) return HttpStatus.TOO_MANY_REQUESTS;
        if (expiry.size() >= nonceGlobalMax) return HttpStatus.SERVICE_UNAVAILABLE;
        nonces.computeIfAbsent(key, ignored -> new HashSet<>()).add(nonce);
        expiry.add(new NonceEntry(key, nonce, Math.max(now, timestamp) + 301));
        return null;
    }

    private boolean fresh(String timestamp) {
        long ts = Long.parseLong(timestamp);
        long now = clock.instant().getEpochSecond();
        return ts >= now - 300 && ts <= now + 300;
    }

    private static boolean canonicalPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return !path.contains("//") && !path.contains("/./") && !path.contains("/../")
                && !path.endsWith("/.") && !path.endsWith("/..") && !path.contains(";") && !path.contains("\\")
                && !lower.contains("%2e") && !lower.contains("%2f") && !lower.contains("%5c") && !lower.contains("%00");
    }

    private static Scheduler originalScheduler(ServerHttpRequest request) {
        Object nativeRequest;
        try {
            nativeRequest = ServerHttpRequestDecorator.getNativeRequest(request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            nativeRequest = null;
        }
        if (nativeRequest instanceof reactor.netty.http.server.HttpServerRequest) {
            AtomicReference<Scheduler> scheduler = new AtomicReference<>();
            ((reactor.netty.http.server.HttpServerRequest) nativeRequest).withConnection(connection ->
                    scheduler.set(Schedulers.fromExecutor(connection.channel().eventLoop())));
            return scheduler.get();
        }
        // Mock/non-Netty exchanges have no transport event loop; never run their chain on the RPC pool.
        return Schedulers.parallel();
    }

    private static String header(ServerHttpRequest request, String name) {
        List<String> values = request.getHeaders().get(name);
        return values == null || values.size() != 1 ? null : values.get(0);
    }

    private static Mono<Void> error(ServerWebExchange exchange, HttpStatus status) {
        return GatewayErrors.write(exchange, status, status.value() * 100,
                status == HttpStatus.INTERNAL_SERVER_ERROR ? "Internal server error" : status.getReasonPhrase());
    }

    private static final class Lookup {
        final InvokeUser user;
        final HttpStatus error;
        Lookup(InvokeUser user, HttpStatus error) { this.user = user; this.error = error; }
    }
    private static final class NonceEntry {
        final String key;
        final String nonce;
        final long expiresAt;
        NonceEntry(String key, String nonce, long expiresAt) { this.key = key; this.nonce = nonce; this.expiresAt = expiresAt; }
    }
}
