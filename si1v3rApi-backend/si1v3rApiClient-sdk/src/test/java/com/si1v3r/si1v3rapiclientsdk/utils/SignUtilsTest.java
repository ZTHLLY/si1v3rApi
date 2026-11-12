package com.si1v3r.si1v3rapiclientsdk.utils;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class SignUtilsTest {
    // Independent vector, OpenSSL commands (no trailing newline in canonical):
    // printf '%s' '{"name":"Alice"}' | openssl dgst -sha256
    // printf 'POST\n/api/user\nname=Alice%%20Smith&x=1\n3cba1e3cf23c8ce24b7e08171d823fbd9a4929aafd9f27516e30699d3a42026a\ntest-access\n1700000000\n0123456789abcdef0123456789abcdef' | openssl dgst -sha256 -hmac 'test-secret'
    @Test void fixedVector() {
        assertEquals("8789b341bc02a61bcc5830fe2faec700a368159ad1dd96b20b4850a251895a3b",
                SignUtils.sign("POST", "/api/user", "name=Alice%20Smith&x=1",
                        "{\"name\":\"Alice\"}".getBytes(StandardCharsets.UTF_8), "test-access",
                        "1700000000", "0123456789abcdef0123456789abcdef", "test-secret"));
    }
    @Test void nullsMeanEmpty() {
        assertEquals(SignUtils.sign("GET", "/api/", "", new byte[0], "a", "1", "n", "s"),
                SignUtils.sign("GET", "/api/", null, null, "a", "1", "n", "s"));
        assertNotEquals(SignUtils.sign("GET", "/api/", "x=1", null, "a", "1", "n", "s"),
                SignUtils.sign("GET", "/api/", null, null, "a", "1", "n", "s"));
    }
}
