package io.github.serfom256.intentflow.reactive;

import io.github.serfom256.intentflow.core.api.IntentContext;
import reactor.core.publisher.Mono;

/**
 * Finds the caller of a reactive request, usually from the subscriber's Reactor
 * context, where Spring Security's
 * {@code ReactiveSecurityContextHolder} keeps the authentication. An empty
 * result means an anonymous caller.
 */
@FunctionalInterface
public interface ReactiveIntentContextProvider {

    ReactiveIntentContextProvider NONE = Mono::empty;

    Mono<IntentContext> current();
}
