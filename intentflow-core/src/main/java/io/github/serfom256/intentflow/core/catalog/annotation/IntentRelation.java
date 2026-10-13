package io.github.serfom256.intentflow.core.catalog.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets filters on an {@link IntentService} reach another service's fields, e.g.
 * {@code @IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")}
 * allows {@code orders | filter customer.vip == true}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(IntentRelations.class)
public @interface IntentRelation {

    /** Prefix used in filters. */
    String name();

    /** Target service name. */
    String service();

    /** Logical field of this service holding the link value. */
    String from();

    /** Logical field of the target service it equals. */
    String to();
}
