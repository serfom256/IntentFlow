import { Badge, Button, Card, Code, CopyButton, Group, Select, Stack, Text, TextInput, Tooltip } from "@mantine/core";
import { IconCheck, IconCopy, IconPlayerPlay } from "@tabler/icons-react";
import { useState } from "react";
import { useExplain } from "../api/hooks";
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
            Translates and compiles the question with the current descriptions, without running the query
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
            </Group>
            <Snippet label="DSL" value={explain.data.dsl} />
            <Snippet label="SQL" value={explain.data.sql} />
            {explain.data.parameters.length > 0 && (
              <Snippet label="Parameters" value={JSON.stringify(explain.data.parameters)} />
            )}
          </Stack>
        )}
      </Stack>
    </Card>
  );
}
