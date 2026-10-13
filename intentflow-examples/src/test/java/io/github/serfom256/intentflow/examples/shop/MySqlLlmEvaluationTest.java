package io.github.serfom256.intentflow.examples.shop;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("mysql")
@Import(MySqlContainerConfig.class)
class MySqlLlmEvaluationTest extends LlmEvaluationSupport {
}
