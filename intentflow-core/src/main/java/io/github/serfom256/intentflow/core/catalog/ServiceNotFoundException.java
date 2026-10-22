package io.github.serfom256.intentflow.core.catalog;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.IntentFlowException;

import java.util.Map;

public class ServiceNotFoundException extends IntentFlowException {

    private final ServiceName serviceName;

    public ServiceNotFoundException(ServiceName serviceName) {
        super(ErrorCode.SERVICE_NOT_FOUND, "Unknown service: " + serviceName.value(),
                Map.of("service", serviceName.value()));
        this.serviceName = serviceName;
    }

    public ServiceName serviceName() {
        return serviceName;
    }
}
