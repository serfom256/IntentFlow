package io.github.serfom256.intentflow.examples.shop;

import org.springframework.context.annotation.Import;

import java.util.List;

@Import(PostgresContainerConfig.class)
class MultilingualLlmEvaluationTest extends LlmEvaluationSupport {

    @Override
    List<Case> cases() {
        return new MultilingualQuestions(this).cases();
    }
}
