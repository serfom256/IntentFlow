package io.github.serfom256.intentflow.core.pipeline;

/**
 * Records who asked what and how it was answered, and who changed the catalog,
 * for compliance. The pipeline calls
 * {@link #question} once per {@code resolve}, whatever the outcome; the admin
 * module calls {@link #catalogChange}
 * when an override is saved or reset. {@code AuditLogs.slf4j()} writes both to
 * the {@code intentflow.audit}
 * logger; an implementation can write them to a table or a message queue
 * instead. Methods run on the request
 * thread and must not throw.
 */
public interface AuditLog {

    AuditLog NONE = new AuditLog() {
    };

    default void question(QuestionAudit audit) {
    }

    default void catalogChange(CatalogChangeAudit audit) {
    }
}
