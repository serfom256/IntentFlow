package io.github.serfom256.intentflow.examples.shop.web;

import io.github.serfom256.intentflow.core.catalog.ServiceNotFoundException;
import io.github.serfom256.intentflow.core.pipeline.DslParseException;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import io.github.serfom256.intentflow.core.pipeline.ScanLimitExceededException;
import io.github.serfom256.intentflow.core.pipeline.UnsupportedRequestException;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;
import io.github.serfom256.intentflow.examples.shop.service.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getEntity() + " not found", e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException e) {
        log.debug("constraint violation", e);
        return problem(HttpStatus.CONFLICT, "Conflicts with existing data",
                "The change violates a database constraint (for example a duplicate email).");
    }

    @ExceptionHandler(UnsupportedRequestException.class)
    ProblemDetail unsupported(UnsupportedRequestException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Can't answer that", e.reason());
    }

    @ExceptionHandler({DslParseException.class, ValidationException.class, ServiceNotFoundException.class})
    ProblemDetail untranslatable(RuntimeException e) {
        log.warn("search translation rejected: {}", e.getMessage());
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Couldn't understand the question",
                "Try rephrasing it. (" + e.getMessage() + ")");
    }

    @ExceptionHandler(ScanLimitExceededException.class)
    ProblemDetail tooBroad(ScanLimitExceededException e) {
        log.warn("search too broad: {}", e.getMessage());
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Question too broad",
                "Answering it would read too many rows. Add a condition and try again.");
    }

    @ExceptionHandler(LlmException.class)
    ProblemDetail llmDown(LlmException e) {
        log.error("LLM call failed", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Search assistant unavailable",
                "The language model did not respond. Check that it is running and try again.");
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Some fields are invalid.");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Invalid request",
                ex.getParameterValidationResults().stream()
                        .flatMap(r -> r.getResolvableErrors().stream())
                        .map(err -> err.getDefaultMessage())
                        .findFirst().orElse("Invalid parameters."));
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
