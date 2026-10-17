package io.github.serfom256.intentflow.core.catalog.annotation;

import io.github.serfom256.intentflow.core.catalog.FieldType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exposes a field (or record component) of an {@link IntentService} class, or a
 * property
 * method of an {@link IntentService} interface such as a Jimmer entity.
 * Fields without this annotation are invisible to the LLM.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
public @interface IntentField {

    /** Logical name used in the DSL; defaults to the snake_case Java field name. */
    String name() default "";

    /** Column name; defaults to the snake_case Java field name. */
    String column() default "";

    /** Hint for the LLM: unit, meaning, format. */
    String description() default "";

    /**
     * {@code false} lists the field in the catalog (so URI templates can use
     * it) but forbids the LLM from filtering or sorting on it.
     */
    boolean queryable() default true;

    /** Overrides the type inferred from the Java type. */
    FieldType type() default FieldType.ANY;

    /** Overrides allowed values (enums default to their constant names). */
    String[] values() default {};

    /**
     * Compare text with {@code ==}, {@code !=} and {@code in} exactly, e.g. for
     * codes and keys.
     */
    boolean caseSensitive() default false;
}
