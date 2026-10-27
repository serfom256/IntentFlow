package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.IntentContext;

/**
 * The caller of a request that does not carry an {@link IntentContext} itself.
 * With Spring Security on the classpath the starter reads the current
 * {@code Authentication}: its name becomes the principal and its authorities
 * the roles. Define a bean of this type to add a tenant or attributes, such as
 * the caller's customer id.
 */
@FunctionalInterface
public interface IntentContextProvider {

    IntentContext current();
}
