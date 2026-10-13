package io.github.serfom256.intentflow.core.catalog;

import io.github.serfom256.intentflow.core.IntentFlowException;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public class ServiceNotFoundException extends IntentFlowException {

    private final ServiceName serviceName;

    public ServiceNotFoundException(ServiceName serviceName) {
        super("Unknown service: " + serviceName.value());
        this.serviceName = serviceName;
    }
}
