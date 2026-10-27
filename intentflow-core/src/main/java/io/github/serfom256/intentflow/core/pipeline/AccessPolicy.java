package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;

import java.util.List;
import java.util.Optional;

/**
 * Decides what a caller may ask about. The LLM is shown only the services and
 * fields the caller may query, the validator rejects anything else, and every
 * query of a service, including relation subqueries, gets the service's
 * restrictions. Every method has a permissive default, so a policy overrides
 * only what it limits.
 *
 * <pre>{@code
 * @Bean
 * AccessPolicy accessPolicy() {
 *     return new AccessPolicy() {
 *         public boolean canQuery(ServiceDescriptor service, IntentContext caller) {
 *             return !service.name().value().equals("suppliers") || caller.hasRole("ROLE_BUYER");
 *         }
 *
 *         public List<RuntimeRestriction> restrictions(ServiceDescriptor service, IntentContext caller) {
 *             return service.name().value().equals("orders")
 *                     ? List.of(RuntimeRestriction.equalTo("customer_id", caller.attribute("customerId")))
 *                     : List.of();
 *         }
 *
 *         public Optional<String> scope(ServiceDescriptor service, IntentContext caller) {
 *             return service.name().value().equals("orders") ? Optional.of("only the current customer's own orders")
 *                     : Optional.empty();
 *         }
 *     };
 * }
 * }</pre>
 */
public interface AccessPolicy {

    AccessPolicy ALLOW_ALL = new AccessPolicy() {
    };

    /**
     * Whether the caller may query the service at all; a hidden service is never
     * shown to the LLM.
     */
    default boolean canQuery(ServiceDescriptor service, IntentContext caller) {
        return true;
    }

    /** Whether the caller may filter, sort or group by the field. */
    default boolean canSee(ServiceDescriptor service, FieldMapping field, IntentContext caller) {
        return true;
    }

    /**
     * Conditions every row of the service must meet for this caller, such as
     * {@code RuntimeRestriction.equalTo("tenant_id", caller.tenant())}. Columns are
     * written unqualified.
     */
    default List<RuntimeRestriction> restrictions(ServiceDescriptor service, IntentContext caller) {
        return List.of();
    }

    /**
     * Which rows the caller sees, in words for the LLM, such as "only the current
     * user's own orders". With it, "my orders" is answered as "orders"; without it,
     * a question about the caller's own records is declined.
     */
    default Optional<String> scope(ServiceDescriptor service, IntentContext caller) {
        return Optional.empty();
    }
}
