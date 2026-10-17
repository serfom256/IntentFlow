package io.github.serfom256.intentflow.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

final class SecurityContextAdminAccess implements AdminAccess {

    private final List<String> authorities;

    SecurityContextAdminAccess(List<String> authorities) {
        this.authorities = List.copyOf(authorities);
    }

    @Override
    public Decision check(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Decision.unauthenticated(null);
        }
        boolean admin = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authorities::contains);
        return admin ? Decision.allowed(authentication.getName()) : Decision.forbidden(authentication.getName());
    }
}
