package com.si1v3r.si1v3rapiclientsdk.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

public final class SignUtils {
    private SignUtils() { }

    public static String sign(String method, String path, String rawQuery, byte[] body,
                              String assessKey, String timestamp, String nonce, String secretKey) {
        try {
            String digest = hex(MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body));
            String canonical = method + "\n" + path + "\n" + (rawQuery == null ? "" : rawQuery)
                    + "\n" + digest + "\n" + assessKey + "\n" + timestamp + "\n" + nonce;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signing unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = digits[(bytes[i] & 255) >>> 4];
            result[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(result);
    }
}
