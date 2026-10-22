package io.github.serfom256.intentflow.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

final class SecurityContextAdminAccess implements AdminAccess {

    private final List<String> editors;
    private final List<String> viewers;

    SecurityContextAdminAccess(List<String> editors, List<String> viewers) {
        this.editors = List.copyOf(editors);
        this.viewers = List.copyOf(viewers);
    }

    @Override
    public Decision check(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Decision.unauthenticated(null);
        }
        List<String> granted = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        if (granted.stream().anyMatch(editors::contains)) {
            return Decision.allowed(authentication.getName());
        }
        if (granted.stream().anyMatch(viewers::contains)) {
            return Decision.readOnly(authentication.getName());
        }
        return Decision.forbidden(authentication.getName());
    }
}
