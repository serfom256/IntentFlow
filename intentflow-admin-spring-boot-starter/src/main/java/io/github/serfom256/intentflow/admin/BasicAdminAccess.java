package io.github.serfom256.intentflow.admin;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

final class BasicAdminAccess implements AdminAccess {

    private static final String CHALLENGE = "Basic realm=\"IntentFlow Admin\", charset=\"UTF-8\"";

    private final String username;
    private final String password;

    BasicAdminAccess(String username, String password) {
        this.username = username;
        this.password = password;
    }

    static String generatePassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String password() {
        return password;
    }

    @Override
    public Decision check(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return Decision.unauthenticated(CHALLENGE);
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(6).strip()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Decision.unauthenticated(CHALLENGE);
        }
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            return Decision.unauthenticated(CHALLENGE);
        }
        boolean user = same(decoded.substring(0, colon), username);
        boolean secret = same(decoded.substring(colon + 1), password);
        return user && secret ? Decision.allowed(username) : Decision.unauthenticated(CHALLENGE);
    }

    @Override
    public boolean protectsPages() {
        return true;
    }

    private static boolean same(String given, String expected) {
        return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
