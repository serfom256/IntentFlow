package io.github.serfom256.intentflow.admin;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class OidcRoles {

    private OidcRoles() {
    }

    static GrantedAuthoritiesMapper mapper(String claim) {
        return authorities -> {
            Set<GrantedAuthority> mapped = new HashSet<>(authorities);
            for (GrantedAuthority authority : authorities) {
                if (authority instanceof OidcUserAuthority oidc) {
                    List<String> roles = oidc.getIdToken().getClaimAsStringList(claim);
                    if (roles != null) {
                        roles.forEach(role -> mapped.add(new SimpleGrantedAuthority("ROLE_" + role)));
                    }
                }
            }
            return mapped;
        };
    }
}
