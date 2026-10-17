package io.github.serfom256.intentflow.jimmer;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.OneToMany;
import org.babyfish.jimmer.sql.Table;

import java.util.List;

@Entity
@Table(name = "rel_customer")
@IntentService(value = "customers", uri = "/customers/{{id}}", defaultSort = "id")
public interface JCustomer {

    @Id
    @IntentField
    long id();

    @IntentField
    String name();

    @IntentField
    String country();

    @IntentField
    boolean vip();

    @OneToMany(mappedBy = "customer")
    List<JOrder> orders();
}
