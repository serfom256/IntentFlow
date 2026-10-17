package io.github.serfom256.intentflow.admin;

import io.github.serfom256.intentflow.core.catalog.ServiceNotFoundException;
import io.github.serfom256.intentflow.core.catalog.ServiceOverride;
import io.github.serfom256.intentflow.core.pipeline.DslParseException;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import io.github.serfom256.intentflow.core.pipeline.UnsupportedRequestException;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${intentflow.admin.path:/intentflow-admin}/api")
public class CatalogAdminController {

    public record OverrideRequest(@Nullable String description, @Nullable String uri,
            @Nullable Map<String, String> fields) {
    }

    public record ExplainRequest(String question, @Nullable String service) {
    }

    public record Csrf(String header, String token) {
    }

    public record SessionView(@Nullable String user, @Nullable Csrf csrf) {
    }

    private final CatalogAdmin admin;

    public CatalogAdminController(CatalogAdmin admin) {
        this.admin = admin;
    }

    @GetMapping("/session")
    public SessionView session(HttpServletRequest request) {
        return new SessionView((String) request.getAttribute(AdminAccessInterceptor.USER), csrf(request));
    }

    @GetMapping("/services")
    public List<CatalogAdmin.ServiceView> services() {
        return admin.services();
    }

    @GetMapping("/services/{name}")
    public CatalogAdmin.ServiceView service(@PathVariable("name") String name) {
        return admin.service(name);
    }

    @PutMapping("/services/{name}")
    public CatalogAdmin.ServiceView update(@PathVariable("name") String name, @RequestBody OverrideRequest request) {
        return admin.update(name, new ServiceOverride(request.description(), request.uri(),
                request.fields() == null ? Map.of() : request.fields()));
    }

    @DeleteMapping("/services/{name}")
    public CatalogAdmin.ServiceView reset(@PathVariable("name") String name) {
        return admin.reset(name);
    }

    @PostMapping("/explain")
    public CatalogAdmin.ExplainView explain(@RequestBody ExplainRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        return admin.explain(request.question(), request.service());
    }

    @ExceptionHandler(CatalogAdmin.InvalidOverrideException.class)
    ProblemDetail invalid(CatalogAdmin.InvalidOverrideException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid", "The override was not saved");
        problem.setProperty("errors", e.problems());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid", e.getMessage());
    }

    @ExceptionHandler(ServiceNotFoundException.class)
    ProblemDetail notFound(ServiceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", e.getMessage());
    }

    @ExceptionHandler(UnsupportedRequestException.class)
    ProblemDetail declined(UnsupportedRequestException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "unsupported", e.getMessage());
    }

    @ExceptionHandler({ ValidationException.class, DslParseException.class })
    ProblemDetail rejected(RuntimeException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "rejected", e.getMessage());
    }

    @ExceptionHandler(LlmException.class)
    ProblemDetail llm(LlmException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "llm", e.getMessage());
    }

    private static @Nullable Csrf csrf(HttpServletRequest request) {
        Object token = request.getAttribute("_csrf");
        if (token == null) {
            return null;
        }
        try {
            String header = (String) token.getClass().getMethod("getHeaderName").invoke(token);
            String value = (String) token.getClass().getMethod("getToken").invoke(token);
            return header == null || value == null ? null : new Csrf(header, value);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static ProblemDetail problem(HttpStatus status, String kind, @Nullable String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                detail == null ? status.getReasonPhrase() : detail);
        problem.setProperty("kind", kind);
        return problem;
    }
}
