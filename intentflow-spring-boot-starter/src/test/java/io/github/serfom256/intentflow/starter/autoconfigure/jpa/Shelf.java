package io.github.serfom256.intentflow.starter.autoconfigure.jpa;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "library_shelf")
@IntentService(value = "shelves", uri = "/shelves/{{id}}")
public class Shelf {

    @Id
    @Column(name = "shelf_id")
    @IntentField
    Long id;

    @Column(name = "label_text")
    @IntentField
    String label;
}
