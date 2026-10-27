package io.github.serfom256.intentflow.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class AdminAccessInterceptor implements HandlerInterceptor {

    static final String USER = AdminAccessInterceptor.class.getName() + ".user";
    static final String EDITOR = AdminAccessInterceptor.class.getName() + ".editor";

    private final AdminAccess access;
    private final String path;
    private final List<String> authorities;
    private final @Nullable String loginUrl;

    AdminAccessInterceptor(AdminAccess access, String path, List<String> editors, List<String> viewers,
            @Nullable String loginUrl) {
        this.access = access;
        this.path = path;
        this.authorities = Stream.concat(editors.stream(), viewers.stream()).distinct().toList();
        this.loginUrl = loginUrl;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        boolean api = uri.startsWith(path + "/api/");
        if (!api && !access.protectsPages()) {
            return true;
        }
        AdminAccess.Decision decision = access.check(request);
        switch (decision.outcome()) {
        case ALLOWED -> {
            if (!decision.editor() && api && writes(request, uri)) {
                problem(response, 403, "read-only",
                        decision.user() + " may read the catalog and explain " + "questions, but not change overrides",
                        true);
                return false;
            }
            request.setAttribute(USER, decision.user());
            request.setAttribute(EDITOR, decision.editor());
            return true;
        }
        case UNAUTHENTICATED -> {
            if (decision.challenge() != null) {
                response.setHeader("WWW-Authenticate", decision.challenge());
            }
            problem(response, 401, "unauthenticated", "Sign in to use the IntentFlow admin", api);
        }
        case FORBIDDEN -> problem(response, 403, "forbidden", "The IntentFlow admin needs one of the authorities "
                + authorities + "; " + decision.user() + " has none of them", api);
        }
        return false;
    }

    private boolean writes(HttpServletRequest request, String uri) {
        String method = request.getMethod();
        boolean reads = method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS");
        return !reads && !(method.equals("POST") && uri.equals(path + "/api/explain"));
    }

    private void problem(HttpServletResponse response, int status, String kind, String detail, boolean api)
            throws IOException {
        response.setStatus(status);
        if (!api) {
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(detail);
            return;
        }
        response.setContentType("application/problem+json;charset=UTF-8");
        String login = loginUrl == null ? "null" : json(loginUrl);
        response.getWriter().write("{\"status\":" + status + ",\"kind\":" + json(kind) + ",\"detail\":" + json(detail)
                + ",\"login\":" + login + ",\"authorities\":"
                + authorities.stream().map(AdminAccessInterceptor::json).collect(Collectors.joining(",", "[", "]"))
                + "}");
    }

    private static String json(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
            case '"' -> sb.append("\\\"");
            case '\\' -> sb.append("\\\\");
            case '\n' -> sb.append("\\n");
            case '\r' -> sb.append("\\r");
            case '\t' -> sb.append("\\t");
            default -> {
                if (c < 0x20) {
                    sb.append(String.format("\\u%04x", (int) c));
                } else {
                    sb.append(c);
                }
            }
            }
        }
        return sb.append('"').toString();
    }
}
