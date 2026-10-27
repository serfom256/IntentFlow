package io.github.serfom256.intentflow.starter.autoconfigure;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Matches when {@code intentflow.llm.provider} names this provider, or when it
 * is unset and this is the only provider module on the classpath;
 * {@code openai} counts only when no other provider brings it in.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Conditional(LlmProviders.Selected.class)
@interface ConditionalOnLlmProvider {

    String value();
}
