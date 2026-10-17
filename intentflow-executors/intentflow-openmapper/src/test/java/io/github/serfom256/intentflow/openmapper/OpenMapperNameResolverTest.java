package io.github.serfom256.intentflow.openmapper;

import io.github.serfom256.annotations.entity.Field;
import io.github.serfom256.annotations.entity.Model;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenMapperNameResolverTest {

    @Model(primaryKey = "id")
    @IntentService(value = "users", table = "app_user", uri = "/users/{{id}}")
    static class User {
        @Field
        @IntentField
        Long id;
        @Field(name = "full_name")
        @IntentField
        String fullName;
        @Field
        @IntentField
        String nickName;
        @Field(name = "e_mail")
        @IntentField(column = "email_override")
        String email;
        @IntentField
        Integer loginCount;
    }

    @Test
    void columnsFollowOpenMapperRules() {
        List<String> columns = ServiceDescriptors.fromAnnotated(User.class, List.of(new OpenMapperNameResolver()))
                .fields().stream().map(FieldMapping::column).map(c -> c.value()).toList();

        assertThat(columns).containsExactly(
                "id",
                "full_name",
                "nickName",
                "email_override",
                "login_count");
    }
}
