package com.si1v3r.si1v3rapiclientsdk.client;

import com.si1v3r.si1v3rapiclientsdk.model.ApiResponse;
import com.si1v3r.si1v3rapiclientsdk.model.User;
import com.si1v3r.si1v3rapiclientsdk.utils.SignUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class Si1v3rApiClientTest {
    private HttpServer server;
    private String host;
    private Si1v3rApiClient client;
    private final BlockingQueue<Received> requests = new LinkedBlockingQueue<>();
    private int status = 200;
    private String response = "你好";

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new Received(exchange));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
            exchange.close();
        });
        server.start();
        host = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new Si1v3rApiClient("test-access", "test-secret", host + "///");
    }
    @AfterEach void stop() { server.stop(0); }

    private Received receive() throws InterruptedException {
        Received r = requests.poll(5, TimeUnit.SECONDS);
        assertNotNull(r);
        assertEquals(SignUtils.sign(r.method, r.path, r.query, r.body, r.key, r.timestamp, r.nonce,
                "test-secret"), r.sign);
        assertNull(r.oldBody);
        return r;
    }

    @Test void specialQueryAndRawPathAreNotReencoded() throws Exception {
        Map<String, String> query = new HashMap<>();
        query.put("z", "a b中文&=+/%"); query.put("a &中文", "=+/%");
        ApiResponse result = client.invoke("post", "/api/%E4%B8%AD/%7e", query, "{\"name\":\"中文\"}\n");
        assertEquals(200, result.getStatus()); assertEquals("你好", result.getBody());
        Received r = receive();
        assertEquals("POST", r.method); assertEquals("/api/%E4%B8%AD/%7e", r.path);
        assertEquals("a%20%26%E4%B8%AD%E6%96%87=%3D%2B%2F%25&z=a%20b%E4%B8%AD%E6%96%87%26%3D%2B%2F%25", r.query);
        assertArrayEquals("{\"name\":\"中文\"}\n".getBytes(StandardCharsets.UTF_8), r.body);
        assertEquals("application/json", r.contentType);
    }
    @Test void nullQueryValuesAreNeitherSentNorSigned() throws Exception {
        Map<String, String> query = new HashMap<>();
        query.put("a", null); query.put("b", "kept"); query.put("c", null); query.put("d", "");
        client.invoke("GET", "/api/", query, null);
        assertEquals("b=kept&d=", receive().query);
        client.invoke("GET", "/api/", Collections.singletonMap("omitted", null), null);
        assertNull(receive().query);
    }
    @Test void configToStringExcludesSecret() {
        com.si1v3r.si1v3rapiclientsdk.si1v3rApiClientConfig config =
                new com.si1v3r.si1v3rapiclientsdk.si1v3rApiClientConfig();
        config.setSecretKey("unique-config-secret-value");
        assertFalse(config.toString().contains("unique-config-secret-value"));
        assertFalse(config.toString().contains("secretKey"));
        assertFalse(client.toString().contains("test-secret"));
    }
    @Test void readTimeoutPreservesOriginalCauseAndType() throws Exception {
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server.createContext("/slow", exchange -> {
            requests.add(new Received(exchange));
            try {
                if (releaseResponse.await(5, TimeUnit.SECONDS)) exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        client.setReadTimeoutMillis(1000);
        try {
            UncheckedIOException error = assertThrows(UncheckedIOException.class,
                    () -> client.invoke("GET", "/slow", null, null));
            assertInstanceOf(SocketTimeoutException.class, error.getCause());
            assertTrue(error.getMessage().contains("SocketTimeoutException"));
            assertFalse(error.getMessage().contains("test-secret"));
            StringWriter trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace));
            assertFalse(trace.toString().contains("test-secret"));
            assertEquals("/slow", receive().path);
        } finally {
            releaseResponse.countDown();
        }
    }
    @Test void noBodyNonceAndTimestamp() throws Exception {
        client.invoke("get", "/api/", null, null); Received a = receive();
        client.invoke("GET", "/api/", Collections.emptyMap(), null); Received b = receive();
        assertNull(a.query); assertNull(b.query); assertEquals(0, a.body.length);
        assertNull(a.contentType); assertEquals("GET", a.method);
        assertTrue(a.nonce.matches("[0-9a-f]{32}")); assertTrue(b.nonce.matches("[0-9a-f]{32}"));
        assertNotEquals(a.nonce, b.nonce);
        assertTrue(Math.abs(Instant.now().getEpochSecond() - Long.parseLong(a.timestamp)) <= 5);
    }
    @Test void errorAndRedirectStatusesAreReturned() throws Exception {
        for (int code : new int[]{401, 500, 302}) {
            status = code; response = "error";
            ApiResponse r = client.invoke("GET", "/api/", null, null);
            assertEquals(code, r.getStatus()); assertEquals("error", r.getBody()); receive();
        }
    }
    @Test void legacyMethods() throws Exception {
        assertEquals("你好", client.getNameByGet("a b")); Received get = receive();
        assertEquals("GET", get.method); assertEquals("/api/", get.path); assertEquals("name=a%20b", get.query);
        client.getNameByPost("a b"); Received post = receive();
        assertEquals("POST", post.method); assertEquals("/api/name", post.path); assertEquals("name=a%20b", post.query);
        assertEquals(0, post.body.length);
        User user = new User(); user.setName("T12"); client.getUsernameByPost(user); Received json = receive();
        assertEquals("POST", json.method); assertEquals("/api/user", json.path);
        assertEquals("{\"name\":\"T12\"}", new String(json.body, StandardCharsets.UTF_8));
    }
    @Test void fixedProtocolVectorThroughTransport() throws Exception {
        SecureRandom fixed = new SecureRandom() {
            @Override public void nextBytes(byte[] bytes) {
                byte[] vector = {1,35,69,103,(byte)137,(byte)171,(byte)205,(byte)239};
                for (int i = 0; i < bytes.length; i++) bytes[i] = vector[i % 8];
            }
        };
        client = new Si1v3rApiClient("test-access", "test-secret", host,
                Clock.fixed(Instant.ofEpochSecond(1700000000), ZoneOffset.UTC), fixed);
        Map<String,String> query = new HashMap<>(); query.put("x", "1"); query.put("name", "Alice Smith");
        client.invoke("post", "/api/user", query, "{\"name\":\"Alice\"}");
        assertEquals("8789b341bc02a61bcc5830fe2faec700a368159ad1dd96b20b4850a251895a3b", receive().sign);
    }
    @Test void getWithJsonAndPatchPreserveVerbAndBody() throws Exception {
        for (String method : new String[]{"GET", "PATCH"}) {
            client.invoke(method, "/api/user", null, "{}"); Received r = receive();
            assertEquals(method, r.method); assertArrayEquals(new byte[]{'{','}'}, r.body);
        }
    }
    @Test void rejectsAmbiguousPathsAndInvalidTimeouts() {
        for (String path : new String[]{"api/", "//api/", "/api/?x=1", "/api/#fragment", "/中文"})
            assertThrows(IllegalArgumentException.class, () -> client.invoke("GET", path, null, null));
        assertThrows(IllegalArgumentException.class, () -> client.setConnectTimeoutMillis(0));
        assertThrows(IllegalArgumentException.class, () -> client.setReadTimeoutMillis(-1));
    }
    @Test void unconfiguredBeanCanLoadButCannotInvoke() {
        Si1v3rApiClient unconfigured = new com.si1v3r.si1v3rapiclientsdk.si1v3rApiClientConfig().si1v3rApiClient();
        assertThrows(IllegalStateException.class, () -> unconfigured.invoke("GET", "/api/", null, null));
    }
    @Test void networkFailureDoesNotExposeSecret() throws Exception {
        server.stop(0);
        UncheckedIOException error = assertThrows(UncheckedIOException.class,
                () -> client.invoke("GET", "/api/", null, null));
        StringWriter trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace));
        assertFalse(trace.toString().contains("test-secret"));
    }
    private static final class Received {
        final String method, path, query, key, timestamp, nonce, sign, contentType, oldBody;
        final byte[] body;
        Received(HttpExchange e) throws IOException {
            method = e.getRequestMethod(); path = e.getRequestURI().getRawPath(); query = e.getRequestURI().getRawQuery();
            key = e.getRequestHeaders().getFirst("assessKey"); timestamp = e.getRequestHeaders().getFirst("timestamp");
            nonce = e.getRequestHeaders().getFirst("nonce"); sign = e.getRequestHeaders().getFirst("sign");
            contentType = e.getRequestHeaders().getFirst("Content-Type"); oldBody = e.getRequestHeaders().getFirst("body");
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[1024]; int n;
            try (InputStream in = e.getRequestBody()) { while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); }
            body = out.toByteArray();
        }
    }
}
