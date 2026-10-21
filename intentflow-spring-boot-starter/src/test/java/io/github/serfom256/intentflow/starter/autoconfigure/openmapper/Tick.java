package io.github.serfom256.intentflow.starter.autoconfigure.openmapper;

import io.github.serfom256.annotations.entity.Field;
import io.github.serfom256.annotations.entity.Model;

@Model(primaryKey = "x")
public class Tick {

    @Field
    public Long x;
}
