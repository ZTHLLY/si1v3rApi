package com.si1v3r.si1v3rapigateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.si1v3r.si1v3rapiclientsdk.utils.SignUtils;
import com.si1v3r.si1v3rapicommon.model.InvokeUser;
import com.si1v3r.si1v3rapicommon.service.InnerUserService;
import com.si1v3r.si1v3rapigateway.support.GatewayAttrs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthGlobalFilterTest {
    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private static final String TS = "1700000000";
    private static final byte[] BODY = "{\"name\":\"你好\"}".getBytes(StandardCharsets.UTF_8);
    private InnerUserService service;
    private InvokeUser user;
    private AuthGlobalFilter filter;

    @BeforeEach void setup() {
        service = mock(InnerUserService.class);
        user = new InvokeUser(); user.setId(1L); user.setAssessKey("test-access");
        user.setSecretKey("test-secret"); user.setUserRole("user");
        when(service.getInvokeUser("test-access")).thenReturn(user);
        filter = new AuthGlobalFilter(service, Clock.fixed(Instant.ofEpochSecond(1700000000), ZoneOffset.UTC));
    }
    private String sign(String method, String path, String query, byte[] body, String timestamp) {
        return SignUtils.sign(method, path, query, body, "test-access", timestamp, NONCE, "test-secret");
    }
    private MockServerWebExchange request(String method, String path, String query, byte[] body,
                                           String timestamp, String nonce, String signature, String missing) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(org.springframework.http.HttpMethod.valueOf(method),
                URI.create("http://localhost" + path + (query == null ? "" : "?" + query)));
        String[][] headers = {{"assessKey", "test-access"}, {"timestamp", timestamp}, {"nonce", nonce}, {"sign", signature}};
        for (String[] header : headers) if (!header[0].equals(missing)) builder.header(header[0], header[1]);
        return MockServerWebExchange.from(((MockServerHttpRequest.BodyBuilder) builder).body(
                Mono.just(new org.springframework.core.io.buffer.DefaultDataBufferFactory().wrap(body))));
    }
    private MockServerWebExchange valid() {
        return request("POST", "/api/user", "x=%2F&x=2", BODY, TS, NONCE,
                sign("POST", "/api/user", "x=%2F&x=2", BODY, TS), null);
    }
    private void error(MockServerWebExchange exchange, int status) throws Exception {
        filter.filter(exchange, e -> { fail("must not forward"); return Mono.empty(); }).block();
        assertEquals(status, exchange.getResponse().getRawStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
        JsonNode json = new ObjectMapper().readTree(exchange.getResponse().getBodyAsString().block());
        assertEquals(2, json.size()); assertEquals(status * 100, json.get("code").asInt());
        assertEquals(status == 403 ? "Forbidden" : status == 500 ? "Internal server error" : "Unauthorized",
                json.get("message").asText());
    }
    @Test void forwardsExactBodyAndUser() {
        AtomicBoolean forwarded = new AtomicBoolean();
        filter.filter(valid(), e -> {
            forwarded.set(true); assertSame(user, e.getAttribute(GatewayAttrs.INVOKE_USER));
            return DataBufferUtils.join(e.getRequest().getBody()).doOnNext(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()]; buffer.read(bytes); DataBufferUtils.release(buffer);
                assertArrayEquals(BODY, bytes);
            }).then();
        }).block();
        assertTrue(forwarded.get()); assertEquals(-100, filter.getOrder());
    }
    @Test void emptyGetBody() {
        MockServerWebExchange exchange = request("GET", "/api/", null, new byte[0], TS, NONCE,
                sign("GET", "/api/", null, null, TS), null);
        filter.filter(exchange, e -> { assertSame(user, e.getAttribute(GatewayAttrs.INVOKE_USER)); return Mono.empty(); }).block();
        MockServerWebExchange noPublisher = MockServerWebExchange.from(MockServerHttpRequest.get("/api/")
                .header("assessKey", "test-access").header("timestamp", TS).header("nonce", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .header("sign", SignUtils.sign("GET", "/api/", null, null, "test-access", TS,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "test-secret")).build());
        filter.filter(noPublisher, e -> { assertSame(user, e.getAttribute(GatewayAttrs.INVOKE_USER)); return Mono.empty(); }).block();
    }
    @Test void missingEachHeader() throws Exception {
        for (String name : new String[]{"assessKey", "timestamp", "nonce", "sign"})
            error(request("POST", "/api/user", null, BODY, TS, NONCE, sign("POST", "/api/user", null, BODY, TS), name), 401);
    }
    @Test void malformedHeaders() throws Exception {
        for (String nonce : new String[]{"1234", "ABCDEF0123456789abcdef0123456789ab", ""})
            error(request("POST", "/api/user", null, BODY, TS, nonce, sign("POST", "/api/user", null, BODY, TS), null), 401);
        for (String ts : new String[]{"abc", "-1", "999999999999999999999999999"})
            error(request("POST", "/api/user", null, BODY, ts, NONCE, "0000000000000000000000000000000000000000000000000000000000000000", null), 401);
        error(request("POST", "/api/user", null, BODY, TS, NONCE, "bad", null), 401);
    }
    @Test void signatureAndTampering() throws Exception {
        String signature = sign("POST", "/api/user", "x=1", BODY, TS);
        error(request("POST", "/api/user", "x=1", BODY, TS, NONCE, "0000000000000000000000000000000000000000000000000000000000000000", null), 401);
        error(request("POST", "/api/user", "x=1", new byte[]{1}, TS, NONCE, signature, null), 401);
        error(request("POST", "/api/other", "x=1", BODY, TS, NONCE, signature, null), 401);
        error(request("POST", "/api/user", "x=2", BODY, TS, NONCE, signature, null), 401);
    }
    @Test void expiredAndFuture() throws Exception {
        for (String ts : new String[]{"1699999699", "1700000301"})
            error(request("POST", "/api/user", null, BODY, ts, NONCE, sign("POST", "/api/user", null, BODY, ts), null), 401);
    }
    @Test void replay() throws Exception {
        filter.filter(valid(), e -> Mono.empty()).block(); error(valid(), 401);
    }
    @Test void invalidSignatureDoesNotConsumeNonce() throws Exception {
        error(request("POST", "/api/user", "x=%2F&x=2", BODY, TS, NONCE, "0000000000000000000000000000000000000000000000000000000000000000", null), 401);
        AtomicBoolean forwarded = new AtomicBoolean();
        filter.filter(valid(), e -> { forwarded.set(true); return Mono.empty(); }).block(); assertTrue(forwarded.get());
    }
    @Test void unknownUser() throws Exception { when(service.getInvokeUser(anyString())).thenReturn(null); error(valid(), 401); }
    @Test void banned() throws Exception { user.setUserRole("ban"); error(valid(), 403); }
    @Test void rpcFailure() throws Exception { when(service.getInvokeUser(anyString())).thenThrow(new RuntimeException("private")); error(valid(), 500); }

    @Test void nonceCapacityAndExpiry() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(1700000000));
        filter = new AuthGlobalFilter(service, clock, 1);
        filter.filter(valid(), e -> Mono.empty()).block();
        String nextNonce = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String signature = SignUtils.sign("POST", "/api/user", null, BODY, "test-access", TS, nextNonce, "test-secret");
        error(request("POST", "/api/user", null, BODY, TS, nextNonce, signature, null), 500);
        // Full caches must not evict live entries and permit replays.
        error(valid(), 401);
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(1700000300));
        AtomicBoolean forwarded = new AtomicBoolean();
        filter.filter(valid(), e -> { forwarded.set(true); return Mono.empty(); }).block();
        assertTrue(forwarded.get());
    }
    @Test void concurrentReplayOnlyForwardsOnce() {
        java.util.concurrent.atomic.AtomicInteger forwarded = new java.util.concurrent.atomic.AtomicInteger();
        reactor.core.publisher.Flux.range(0, 16).flatMap(i -> filter.filter(valid(), e -> {
            forwarded.incrementAndGet(); return Mono.empty();
        })).blockLast();
        assertEquals(1, forwarded.get());
    }
    @Test void requestReadFailureIsJson500() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/user")
                .header("assessKey", "test-access").header("timestamp", TS).header("nonce", NONCE)
                .header("sign", sign("POST", "/api/user", null, BODY, TS))
                .body(reactor.core.publisher.Flux.error(new IllegalStateException("private"))));
        error(exchange, 500);
    }
}
