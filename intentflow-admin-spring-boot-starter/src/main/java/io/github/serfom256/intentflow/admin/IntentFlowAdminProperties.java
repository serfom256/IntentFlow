package io.github.serfom256.intentflow.admin;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "intentflow.admin")
public record IntentFlowAdminProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("/intentflow-admin") String path,
        @DefaultValue("auto") Storage storage,
        @DefaultValue("intentflow_catalog_override") String table,
        @DefaultValue("true") boolean createTable,
        @DefaultValue("30s") Duration refreshInterval,
        @DefaultValue("true") boolean indexAdvisor,
        @DefaultValue Security security) {

    private static final Pattern PATH = Pattern.compile("(/[A-Za-z0-9._~-]+)+");

    public IntentFlowAdminProperties {
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("intentflow.admin.path must look like /intentflow-admin "
                    + "(leading slash, no trailing slash), got '" + path + "'");
        }
    }

    public record Security(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("ROLE_ADMIN") List<String> authorities,
            @DefaultValue("admin") String username,
            @Nullable String password,
            @Nullable String loginUrl) {
    }

    public enum Storage {
        AUTO,
        MEMORY,
        JDBC
    }
}
