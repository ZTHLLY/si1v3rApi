package com.si1v3r.si1v3rapigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.si1v3r.si1v3rapicommon.model.InvokeInterface;
import com.si1v3r.si1v3rapicommon.model.InvokeUser;
import com.si1v3r.si1v3rapicommon.service.*;
import com.si1v3r.si1v3rapigateway.support.GatewayAttrs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterfaceInvokeGlobalFilterTest {
    private InnerInterfaceInfoService interfaces;
    private InnerUserInterfaceInfoService users;
    private InterfaceInvokeGlobalFilter filter;
    private InvokeInterface info;
    private InvokeUser user;

    @BeforeEach void setup() {
        interfaces = mock(InnerInterfaceInfoService.class);
        users = mock(InnerUserInterfaceInfoService.class);
        filter = new InterfaceInvokeGlobalFilter(interfaces, users);
        info = new InvokeInterface(); info.setId(21L); info.setStatus(1);
        user = new InvokeUser(); user.setId(7L); user.setAssessKey("test-access"); user.setSecretKey("test-secret");
        when(interfaces.getInterfaceInfo(anyString(), anyString())).thenReturn(info);
        when(users.hasLeftNum(21L, 7L)).thenReturn(true);
        when(users.invokeCount(21L, 7L)).thenReturn(true);
    }
    private MockServerWebExchange exchange() {
        MockServerWebExchange e = MockServerWebExchange.from(MockServerHttpRequest.post("/api/user"));
        e.getAttributes().put(GatewayAttrs.INVOKE_USER, user); return e;
    }
    private void error(MockServerWebExchange e, int status, int code) throws Exception {
        filter.filter(e, ignored -> { fail("must not forward"); return Mono.empty(); }).block();
        assertEquals(status, e.getResponse().getRawStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, e.getResponse().getHeaders().getContentType());
        com.fasterxml.jackson.databind.JsonNode json = new ObjectMapper().readTree(e.getResponse().getBodyAsString().block());
        assertEquals(2, json.size()); assertEquals(code, json.get("code").asInt());
        assertFalse(json.get("message").asText().contains("private"));
        verify(users, never()).invokeCount(anyLong(), anyLong());
    }
    private void downstream(MockServerWebExchange e, int status) {
        filter.filter(e, ex -> {
            ex.getResponse().setRawStatusCode(status);
            return ex.getResponse().writeWith(Mono.just(ex.getResponse().bufferFactory().wrap("original".getBytes())));
        }).block();
        assertEquals(status, e.getResponse().getRawStatusCode());
        assertEquals("original", e.getResponse().getBodyAsString().block());
    }
    @Test void missingUser() throws Exception {
        MockServerWebExchange e = exchange(); e.getAttributes().clear(); error(e, 401, 40100);
        verifyNoInteractions(interfaces, users);
    }
    @Test void missingInterface() throws Exception {
        when(interfaces.getInterfaceInfo(anyString(), anyString())).thenReturn(null); error(exchange(), 404, 40400);
        verifyNoInteractions(users);
    }
    @Test void offlineInterface() throws Exception { info.setStatus(0); error(exchange(), 403, 40301); verifyNoInteractions(users); }
    @Test void nullStatusIsOffline() throws Exception { info.setStatus(null); error(exchange(), 403, 40301); }
    @Test void exhausted() throws Exception { when(users.hasLeftNum(21L, 7L)).thenReturn(false); error(exchange(), 403, 40302); }
    @Test void lookupFailure() throws Exception {
        when(interfaces.getInterfaceInfo(anyString(), anyString())).thenThrow(new RuntimeException("private"));
        error(exchange(), 500, 50000); verifyNoInteractions(users);
    }
    @Test void quotaFailure() throws Exception {
        when(users.hasLeftNum(21L, 7L)).thenThrow(new RuntimeException("private")); error(exchange(), 500, 50000);
    }
    @Test void successCountsExactlyOnceAfterResponse() {
        MockServerWebExchange e = exchange();
        when(users.invokeCount(21L, 7L)).thenAnswer(call -> { assertTrue(e.getResponse().isCommitted()); return true; });
        downstream(e, 200); verify(users, times(1)).invokeCount(21L, 7L); assertEquals(-50, filter.getOrder());
    }
    @Test void all2xxCount() { downstream(exchange(), 201); downstream(exchange(), 299); verify(users, times(2)).invokeCount(21L, 7L); }
    @Test void clientErrorDoesNotCount() { downstream(exchange(), 400); verify(users, never()).invokeCount(anyLong(), anyLong()); }
    @Test void serverErrorDoesNotCount() { downstream(exchange(), 500); verify(users, never()).invokeCount(anyLong(), anyLong()); }
    @Test void redirectDoesNotCount() { downstream(exchange(), 302); verify(users, never()).invokeCount(anyLong(), anyLong()); }
    @Test void countFalsePreservesResponse() { when(users.invokeCount(21L, 7L)).thenReturn(false); downstream(exchange(), 200); verify(users).invokeCount(21L, 7L); }
    @Test void countFailurePreservesResponse() { when(users.invokeCount(21L, 7L)).thenThrow(new RuntimeException("private")); downstream(exchange(), 200); verify(users).invokeCount(21L, 7L); }
    @Test void rawPathAndUppercaseMethod() {
        MockServerWebExchange e = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.POST, URI.create("/api/name%20x?q=a%20b")));
        e.getAttributes().put(GatewayAttrs.INVOKE_USER, user); downstream(e, 200);
        verify(interfaces).getInterfaceInfo("/api/name%20x", "POST");
    }
    @Test void downstreamFailureDoesNotCount() {
        assertThrows(IllegalStateException.class, () -> filter.filter(exchange(), e -> Mono.error(new IllegalStateException("downstream"))).block());
        verify(users, never()).invokeCount(anyLong(), anyLong());
    }
    @Test void authFailureNeverEntersInterfaceFilter() {
        InnerUserService authUsers = mock(InnerUserService.class);
        AuthGlobalFilter auth = new AuthGlobalFilter(authUsers, Clock.fixed(Instant.ofEpochSecond(1700000000), ZoneOffset.UTC));
        MockServerWebExchange e = exchange(); e.getAttributes().clear();
        auth.filter(e, ex -> { fail("interface filter must not be entered"); return filter.filter(ex, ignored -> Mono.empty()); }).block();
        assertEquals(401, e.getResponse().getRawStatusCode()); verifyNoInteractions(interfaces, users, authUsers);
    }
    @Test void signedAuthFailureNeverEntersInterfaceFilter() {
        InnerUserService authUsers = mock(InnerUserService.class); when(authUsers.getInvokeUser("test-access")).thenReturn(user);
        AuthGlobalFilter auth = new AuthGlobalFilter(authUsers, Clock.fixed(Instant.ofEpochSecond(1700000000), ZoneOffset.UTC));
        MockServerWebExchange e = MockServerWebExchange.from(MockServerHttpRequest.post("/api/user")
                .header("assessKey", "test-access").header("timestamp", "1700000000")
                .header("nonce", "0123456789abcdef0123456789abcdef").header("sign", "0".repeat(64)));
        auth.filter(e, ex -> { fail("interface filter must not be entered"); return filter.filter(ex, ignored -> Mono.empty()); }).block();
        assertEquals(401, e.getResponse().getRawStatusCode()); verifyNoInteractions(interfaces, users);
    }
    @Test void rpcsUseWorkerAndChainResumesOriginalNettyLoop() {
        AtomicReference<Thread> original = new AtomicReference<>(), downstream = new AtomicReference<>();
        when(interfaces.getInterfaceInfo(anyString(), anyString())).thenAnswer(call -> {
            assertTrue(Thread.currentThread().getName().contains("boundedElastic")); return info;
        });
        when(users.hasLeftNum(21L, 7L)).thenAnswer(call -> { assertTrue(Thread.currentThread().getName().contains("boundedElastic")); return true; });
        when(users.invokeCount(21L, 7L)).thenAnswer(call -> { assertTrue(Thread.currentThread().getName().contains("boundedElastic")); return true; });
        org.springframework.http.server.reactive.HttpHandler handler = org.springframework.web.server.adapter.WebHttpHandlerBuilder.webHandler(e -> {
            original.set(Thread.currentThread()); e.getAttributes().put(GatewayAttrs.INVOKE_USER, user);
            return filter.filter(e, ex -> { downstream.set(Thread.currentThread()); return ex.getResponse().setComplete(); });
        }).build();
        reactor.netty.DisposableServer server = reactor.netty.http.server.HttpServer.create().host("127.0.0.1").port(0)
                .handle(new org.springframework.http.server.reactive.ReactorHttpHandlerAdapter(handler)).bindNow();
        try {
            assertEquals(200, reactor.netty.http.client.HttpClient.create().get().uri("http://127.0.0.1:" + server.port() + "/api/user")
                    .responseSingle((r, bytes) -> bytes.thenReturn(r.status().code())).block());
            // HTTP completion can precede accounting completion; await the composed server operation.
            verify(users, timeout(3000).times(1)).invokeCount(21L, 7L);
            assertSame(original.get(), downstream.get());
        } finally { server.disposeNow(); }
    }
    @Test void clientCloseAfterResponseDoesNotInterruptAccounting() throws Exception {
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean interrupted = new java.util.concurrent.atomic.AtomicBoolean();
        when(users.invokeCount(21L, 7L)).thenAnswer(call -> {
            started.countDown();
            try { release.await(5, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException ex) { interrupted.set(true); }
            finally { finished.countDown(); }
            return true;
        });
        org.springframework.http.server.reactive.HttpHandler handler = org.springframework.web.server.adapter.WebHttpHandlerBuilder.webHandler(e -> {
            e.getAttributes().put(GatewayAttrs.INVOKE_USER, user);
            return filter.filter(e, ex -> ex.getResponse().writeWith(Mono.just(ex.getResponse().bufferFactory().wrap(new byte[]{1}))));
        }).build();
        reactor.netty.DisposableServer server = reactor.netty.http.server.HttpServer.create().host("127.0.0.1").port(0)
                .handle(new org.springframework.http.server.reactive.ReactorHttpHandlerAdapter(handler)).bindNow();
        try {
            assertEquals(200, reactor.netty.http.client.HttpClient.create().keepAlive(false).get()
                    .uri("http://127.0.0.1:" + server.port() + "/api/user")
                    .responseSingle((r, bytes) -> bytes.thenReturn(r.status().code())).block());
            assertTrue(started.await(3, java.util.concurrent.TimeUnit.SECONDS));
            release.countDown();
            assertTrue(finished.await(3, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(interrupted.get());
            verify(users, times(1)).invokeCount(21L, 7L);
        } finally { release.countDown(); server.disposeNow(); }
    }

    @Test void cancellationDuringAccountingDoesNotInterruptRpc() throws Exception {
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean interrupted = new java.util.concurrent.atomic.AtomicBoolean();
        when(users.invokeCount(21L, 7L)).thenAnswer(call -> {
            started.countDown();
            try { release.await(5, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException ex) { interrupted.set(true); }
            finally { finished.countDown(); }
            return true;
        });
        reactor.core.Disposable subscription = filter.filter(exchange(), e -> e.getResponse().setComplete()).subscribe();
        try {
            assertTrue(started.await(3, java.util.concurrent.TimeUnit.SECONDS));
            subscription.dispose();
            release.countDown();
            assertTrue(finished.await(3, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(interrupted.get());
            verify(users, times(1)).invokeCount(21L, 7L);
        } finally { release.countDown(); subscription.dispose(); }
    }

}
