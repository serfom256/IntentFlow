package io.github.serfom256.intentflow.examples.shop.store;

/**
 * Turns what a search returns, the entities of the active ORM, into the shop's
 * domain objects, so search results
 * have the same JSON shape whichever ORM runs.
 */
public interface EntityMapper {

    Object toDomain(Object entity);
}
