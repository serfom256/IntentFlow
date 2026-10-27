package io.github.serfom256.intentflow.core.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Who is asking. An {@code AccessPolicy} reads it to decide which services and
 * fields the caller may query and which rows they may see, so questions like
 * "my orders" or a tenant's data are answered from the caller's records only.
 */
public interface IntentContext {

    /** The caller's id, such as a user name; empty when anonymous. */
    Optional<String> principal();

    /**
     * The tenant the caller belongs to; empty when the application has no tenants.
     */
    Optional<String> tenant();

    /** Roles or authorities, such as {@code ROLE_ADMIN}. */
    Set<String> roles();

    /** Anything else a policy needs, such as a customer id. */
    Map<String, Object> attributes();

    default boolean hasRole(String role) {
        return roles().contains(role);
    }

    default Optional<Object> attribute(String name) {
        return Optional.ofNullable(attributes().get(name));
    }
}
