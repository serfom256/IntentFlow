import {
  Accordion,
  Alert,
  Badge,
  Button,
  Card,
  Code,
  CopyButton,
  Group,
  Select,
  Stack,
  Text,
  TextInput,
  Tooltip,
} from "@mantine/core";
import { IconBolt, IconCheck, IconCopy, IconInfoCircle, IconPlayerPlay } from "@tabler/icons-react";
import { useState } from "react";
import { useExplain } from "../api/hooks";
import type { IndexAdvice } from "../api/types";
import { ErrorAlert } from "./ErrorAlert";

function Snippet({ label, value }: { label: string; value: string }) {
  return (
    <Stack gap={4}>
      <Group justify="space-between">
        <Text size="xs" fw={600} c="dimmed" tt="uppercase">
          {label}
        </Text>
        <CopyButton value={value}>
          {({ copied, copy }) => (
            <Tooltip label={copied ? "Copied" : "Copy"}>
              <Button size="compact-xs" variant="subtle" onClick={copy} leftSection={copied ? <IconCheck size={14} /> : <IconCopy size={14} />}>
                {copied ? "Copied" : "Copy"}
              </Button>
            </Tooltip>
          )}
        </CopyButton>
      </Group>
      <Code block>{value}</Code>
    </Stack>
  );
}

function prettyPlan(plan: string) {
  try {
    return JSON.stringify(JSON.parse(plan), null, 2);
  } catch {
    return plan;
  }
}

function AdvicePanel({ advice }: { advice: IndexAdvice }) {
  if (advice.error) {
    return (
      <Alert variant="light" color="gray" icon={<IconInfoCircle size={16} />} title="No query plan">
        {advice.error}
      </Alert>
    );
  }
  return (
    <Stack gap="sm">
      <Text size="xs" fw={600} c="dimmed" tt="uppercase">
        Index suggestions
      </Text>
      {advice.suggestions.length === 0 ? (
        <Text size="sm" c="dimmed">
          The plan uses indexes where it can, or the existing ones already cover it.
        </Text>
      ) : (
        advice.suggestions.map((suggestion) => (
          <Stack key={suggestion.ddl} gap={4}>
            <Group gap="xs">
              <IconBolt size={14} />
              <Text size="sm">
                <b>{suggestion.table}</b>: {suggestion.reason}
              </Text>
            </Group>
            <Snippet label="DDL" value={suggestion.ddl} />
          </Stack>
        ))
      )}
      <Text size="xs" c="dimmed">
        Suggestions come from the plan of the database you are connected to. On small tables the planner may prefer a
        full scan even with an index, so check with production-sized data.
      </Text>
      {advice.plan && (
        <Accordion variant="contained">
          <Accordion.Item value="plan">
            <Accordion.Control>Database plan</Accordion.Control>
            <Accordion.Panel>
              <Snippet label="EXPLAIN" value={prettyPlan(advice.plan)} />
            </Accordion.Panel>
          </Accordion.Item>
        </Accordion>
      )}
    </Stack>
  );
}

export function ExplainPanel({ service, services }: { service?: string; services?: string[] }) {
  const [question, setQuestion] = useState("");
  const [scope, setScope] = useState<string | null>(service ?? null);
  const explain = useExplain();

  const run = () => {
    if (question.trim()) {
      explain.mutate({ question: question.trim(), service: scope ?? undefined });
    }
  };

  return (
    <Card withBorder padding="lg">
      <Stack gap="md">
        <div>
          <Text fw={600}>Try a question</Text>
          <Text size="sm" c="dimmed">
            Translates and compiles the question with the current descriptions, without running the query, and asks
            the database for its plan
          </Text>
        </div>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            run();
          }}
        >
          <Group align="flex-end" wrap="nowrap">
            <TextInput
              flex={1}
              label="Question"
              placeholder="e.g. the best rated product in each category"
              value={question}
              onChange={(event) => setQuestion(event.currentTarget.value)}
            />
            {services && (
              <Select
                w={180}
                label="Scope"
                placeholder="All services"
                clearable
                data={services}
                value={scope}
                onChange={setScope}
              />
            )}
            <Button type="submit" loading={explain.isPending} leftSection={<IconPlayerPlay size={16} />} disabled={!question.trim()}>
              Explain
            </Button>
          </Group>
        </form>
        <ErrorAlert error={explain.error} />
        {explain.data && (
          <Stack gap="sm">
            <Group gap="xs">
              <Badge variant="light" color={explain.data.cached ? "gray" : "indigo"}>
                {explain.data.cached ? "from cache" : "translated by the LLM"}
              </Badge>
              {explain.data.repairs > 0 && (
                <Badge variant="light" color="orange">
                  corrected {explain.data.repairs}× after a rejected query
                </Badge>
              )}
              {explain.data.optimizations.map((optimization) => (
                <Badge key={optimization} variant="outline" color="teal">
                  {optimization}
                </Badge>
              ))}
            </Group>
            <Snippet label="DSL" value={explain.data.dsl} />
            <Snippet label="SQL" value={explain.data.sql} />
            {explain.data.parameters.length > 0 && (
              <Snippet label="Parameters" value={JSON.stringify(explain.data.parameters)} />
            )}
            {explain.data.advice && <AdvicePanel advice={explain.data.advice} />}
          </Stack>
        )}
      </Stack>
    </Card>
  );
}
