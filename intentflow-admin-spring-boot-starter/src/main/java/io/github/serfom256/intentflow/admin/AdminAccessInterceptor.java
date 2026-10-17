package io.github.serfom256.intentflow.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

final class AdminAccessInterceptor implements HandlerInterceptor {

    static final String USER = AdminAccessInterceptor.class.getName() + ".user";

    private final AdminAccess access;
    private final String path;
    private final List<String> authorities;
    private final @Nullable String loginUrl;

    AdminAccessInterceptor(AdminAccess access, String path, List<String> authorities, @Nullable String loginUrl) {
        this.access = access;
        this.path = path;
        this.authorities = List.copyOf(authorities);
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
                request.setAttribute(USER, decision.user());
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
