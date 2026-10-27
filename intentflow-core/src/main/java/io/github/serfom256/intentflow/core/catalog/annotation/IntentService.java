package io.github.serfom256.intentflow.core.catalog.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a model class as a queryable IntentFlow service. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IntentService {

    /** Service name the LLM addresses, e.g. {@code "products"}. */
    String value();

    /** Table name; defaults to the snake_case simple class name. */
    String table() default "";

    /** What the service contains, for the LLM. */
    String description() default "";

    /**
     * URI template with {@code {{field}}} placeholders, e.g.
     * {@code "/products/{{id}}"}.
     */
    String uri();

    /** Upper bound for {@code take}; also the default LIMIT. */
    int maxTake() default 50;

    /**
     * Default ordering as {@code "field"} or {@code "field desc"}, by logical name.
     */
    String[] defaultSort() default {};

    /**
     * SQL condition every row of this service satisfies, e.g.
     * {@code deleted_at IS NULL}, ANDed into every query on the table. ORM
     * restrictions such as Hibernate's {@code @SQLRestriction} are picked up
     * without it.
     */
    String restriction() default "";
}
