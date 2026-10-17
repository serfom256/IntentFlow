package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.BoundQuery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Builds the statements an executor runs to load a relation of the entities it
 * just read, for persistence layers
 * that do not load associations themselves.
 */
public interface RelatedRows {

    RelatedRows NONE = (type, relation, keys) -> Optional.empty();

    /**
     * Statements selecting every row of {@code relation} of the service mapped to
     * {@code type} whose link value is
     * one of {@code keys}, with the target service's restrictions applied. Long key
     * lists are split so each
     * statement stays within the database's limits. Empty when {@code relation} is
     * not a relation of that service.
     */
    Optional<List<BoundQuery>> queries(Class<?> type, String relation, Collection<?> keys);
}
