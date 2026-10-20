package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class SpringSecurityContextProvider implements IntentContextProvider {

    @Override
    public IntentContext current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return IntentContexts.anonymous();
        }
        Set<String> roles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return IntentContexts.of(authentication.getName(), null, roles);
    }
}
