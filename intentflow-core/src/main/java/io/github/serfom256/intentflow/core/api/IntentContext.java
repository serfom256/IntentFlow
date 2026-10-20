package io.github.serfom256.intentflow.core.api;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Who is asking. An {@code AccessPolicy} reads it to decide which
 * services and fields the caller may query and which rows they may see, so
 * questions like "my orders" or a
 * tenant's data are answered from the caller's records only.
 */
public interface IntentContext {

    /** The caller's id, such as a user name; {@code null} when anonymous. */
    @Nullable
    String principal();

    /**
     * The tenant the caller belongs to; {@code null} when the application has no
     * tenants.
     */
    @Nullable
    String tenant();

    /** Roles or authorities, such as {@code ROLE_ADMIN}. */
    Set<String> roles();

    /** Anything else a policy needs, such as a customer id. */
    Map<String, Object> attributes();

    default boolean hasRole(String role) {
        return roles().contains(role);
    }

    default @Nullable Object attribute(String name) {
        return attributes().get(name);
    }
}
