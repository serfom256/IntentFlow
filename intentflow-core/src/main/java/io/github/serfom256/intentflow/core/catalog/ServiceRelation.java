package io.github.serfom256.intentflow.core.catalog;

/**
 * A link from one service to another, e.g. {@code orders.customer → customers}.
 * Filters address the target's fields as {@code <name>.<field>}; a row matches
 * when
 * some target row with {@code target.to == this.from} satisfies the condition.
 */
public interface ServiceRelation {

    /**
     * Prefix used in filters, e.g. {@code customer} in
     * {@code customer.vip == true}.
     */
    String name();

    ServiceName target();

    /** Field of this service holding the link value, e.g. {@code customer_id}. */
    LogicalFieldName from();

    /** Field of the target service it equals, e.g. {@code id}. */
    LogicalFieldName to();
}
