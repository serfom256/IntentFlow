package io.github.serfom256.intentflow.examples.shop;

import org.springframework.context.annotation.Import;

@Import(PostgresContainerConfig.class)
class PostgresLlmEvaluationTest extends LlmEvaluationSupport {
}
