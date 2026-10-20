package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;

import java.util.function.UnaryOperator;

interface ExecutorWrapper extends UnaryOperator<QueryExecutor> {
}
