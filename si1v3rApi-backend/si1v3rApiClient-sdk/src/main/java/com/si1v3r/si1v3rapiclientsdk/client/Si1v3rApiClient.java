package com.si1v3r.si1v3rapiclientsdk.client;

import cn.hutool.json.JSONUtil;
import com.si1v3r.si1v3rapiclientsdk.model.ApiResponse;
import com.si1v3r.si1v3rapiclientsdk.model.User;
import com.si1v3r.si1v3rapiclientsdk.utils.SignUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class Si1v3rApiClient {
    private final String assessKey;
    private final String secretKey;
    private final String gatewayHost;
    private final Clock clock;
    private final SecureRandom random;
    private int connectTimeoutMillis = 5000;
    private int readTimeoutMillis = 10000;

    public Si1v3rApiClient(String assessKey, String secretKey) {
        this(assessKey, secretKey, "http://localhost:8090");
    }

    public Si1v3rApiClient(String assessKey, String secretKey, String gatewayHost) {
        this(assessKey, secretKey, gatewayHost, Clock.systemUTC(), new SecureRandom());
    }

    // Package-private deterministic sources for protocol-vector tests.
    Si1v3rApiClient(String assessKey, String secretKey, String gatewayHost, Clock clock, SecureRandom random) {
        this.assessKey = assessKey;
        this.secretKey = secretKey;
        this.gatewayHost = Objects.requireNonNull(gatewayHost, "gatewayHost").replaceAll("/+$", "");
        URI host = URI.create(this.gatewayHost);
        if (!("http".equalsIgnoreCase(host.getScheme()) || "https".equalsIgnoreCase(host.getScheme()))
                || host.getHost() == null || host.getRawUserInfo() != null
                || !host.getRawPath().isEmpty() || host.getRawQuery() != null || host.getRawFragment() != null) {
            throw new IllegalArgumentException("gatewayHost must be an HTTP(S) origin");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public void setConnectTimeoutMillis(int timeout) {
        if (timeout <= 0) throw new IllegalArgumentException("Connection timeout must be positive");
        this.connectTimeoutMillis = timeout;
    }

    public void setReadTimeoutMillis(int timeout) {
        if (timeout <= 0) throw new IllegalArgumentException("Read timeout must be positive");
        this.readTimeoutMillis = timeout;
    }

    /**
     * path is a raw, already percent-encoded path, without query or fragment.
     * Query entries with null values are skipped: they are neither sent nor signed.
     */
    public ApiResponse invoke(String method, String path, Map<String, String> query, String jsonBody) {
        if (assessKey == null || secretKey == null || secretKey.isEmpty()) {
            throw new IllegalStateException("SDK credentials are not configured");
        }
        String verb = Objects.requireNonNull(method, "method").toUpperCase(Locale.ROOT);
        if (!verb.matches("[!#$%&'*+.^_`|~0-9A-Z-]+")) {
            throw new IllegalArgumentException("Invalid HTTP method");
        }
        if (path == null || !path.startsWith("/") || path.startsWith("//")) {
            throw new IllegalArgumentException("path must start with a single slash");
        }
        URI rawPath = URI.create(path);
        if (!path.equals(rawPath.getRawPath()) || !path.equals(rawPath.toASCIIString())) {
            throw new IllegalArgumentException("path must be an ASCII raw path without query or fragment");
        }
        StringBuilder queryString = new StringBuilder();
        if (query != null) {
            for (Map.Entry<String, String> entry : new TreeMap<>(query).entrySet()) {
                if (entry.getValue() == null) continue;
                if (queryString.length() > 0) queryString.append('&');
                queryString.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
            }
        }
        String rawQuery = queryString.toString();
        byte[] body = jsonBody == null ? new byte[0] : jsonBody.getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        byte[] nonceBytes = new byte[16];
        random.nextBytes(nonceBytes);
        StringBuilder nonceBuilder = new StringBuilder(32);
        for (byte b : nonceBytes) nonceBuilder.append(String.format(Locale.ROOT, "%02x", b & 255));
        String nonce = nonceBuilder.toString();
        String sign = SignUtils.sign(verb, path, rawQuery, body, assessKey, timestamp, nonce, secretKey);
        HttpEntityEnclosingRequestBase request = new HttpEntityEnclosingRequestBase() {
            @Override public String getMethod() { return verb; }
        };
        request.setURI(URI.create(gatewayHost + path + (rawQuery.isEmpty() ? "" : "?" + rawQuery)));
        request.setConfig(RequestConfig.custom().setConnectTimeout(connectTimeoutMillis)
                .setSocketTimeout(readTimeoutMillis).setConnectionRequestTimeout(connectTimeoutMillis)
                .setNormalizeUri(false).setRedirectsEnabled(false).build());
        request.setHeader("assessKey", assessKey);
        request.setHeader("timestamp", timestamp);
        request.setHeader("nonce", nonce);
        request.setHeader("sign", sign);
        if (jsonBody != null) {
            request.setHeader("Content-Type", "application/json");
            request.setEntity(new ByteArrayEntity(body));
        }
        // No automatic retry: reusing a nonce would be rejected as a replay.
        try (CloseableHttpClient client = HttpClients.custom().disableAutomaticRetries()
                .disableRedirectHandling().disableCookieManagement().build();
             CloseableHttpResponse response = client.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            if (response.getEntity() == null) return new ApiResponse(status, "");
            try (InputStream input = response.getEntity().getContent();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                return new ApiResponse(status, new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // Preserve the transport cause for diagnostics, but do not copy its message.
            throw new UncheckedIOException("SDK network request failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private static String encode(String value) {
        byte[] bytes = Objects.requireNonNull(value, "query key/value").getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder();
        final char[] hex = "0123456789ABCDEF".toCharArray();
        for (byte b : bytes) {
            int c = b & 255;
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || c == '-' || c == '.' || c == '_' || c == '~') encoded.append((char) c);
            else encoded.append('%').append(hex[c >>> 4]).append(hex[c & 15]);
        }
        return encoded.toString();
    }

    public String getNameByGet(String name) {
        return invoke("GET", "/api/", Collections.singletonMap("name", name), null).getBody();
    }

    public String getNameByPost(String name) {
        return invoke("POST", "/api/name", Collections.singletonMap("name", name), null).getBody();
    }

    public String getUsernameByPost(User user) {
        return invoke("POST", "/api/user", null, JSONUtil.toJsonStr(user)).getBody();
    }
}
