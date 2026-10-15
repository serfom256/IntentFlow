package io.github.serfom256.intentflow.jimmer;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.testing.GadgetKind;
import org.babyfish.jimmer.sql.Column;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.Table;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gadget")
@IntentService(value = "gadgets", uri = "/gadgets/{{id}}", maxTake = 500, defaultSort = "id")
public interface JimmerGadget {

    @Id
    @IntentField
    long id();

    @IntentField
    @Nullable String name();

    @IntentField(caseSensitive = true)
    @Nullable GadgetKind kind();

    @IntentField
    @Nullable BigDecimal price();

    @IntentField
    @Nullable Integer stock();

    @IntentField
    @Nullable Boolean active();

    @Column(name = "created_at")
    @IntentField
    @Nullable LocalDateTime createdAt();
}
