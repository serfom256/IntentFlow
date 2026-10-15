import { ActionIcon, Badge, Code, CopyButton, Group, Stack, Text, Tooltip } from "@mantine/core";
import { IconCheck, IconCopy } from "@tabler/icons-react";
import type { Trace } from "../api/types";

function Block({ title, value }: { title: string; value: string }) {
  return (
    <div>
      <Group justify="space-between" mb={4}>
        <Text size="xs" c="dimmed" fw={700} tt="uppercase">
          {title}
        </Text>
        <CopyButton value={value} timeout={1500}>
          {({ copied, copy }) => (
            <Tooltip label={copied ? "Copied" : "Copy"} withArrow>
              <ActionIcon variant="subtle" color={copied ? "teal" : "gray"} size="sm" onClick={copy}>
                {copied ? <IconCheck size={14} /> : <IconCopy size={14} />}
              </ActionIcon>
            </Tooltip>
          )}
        </CopyButton>
      </Group>
      <Code block style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
        {value}
      </Code>
    </div>
  );
}

export function TracePanel({ trace, tookMs }: { trace: Trace; tookMs: number }) {
  return (
    <Stack gap="md">
      <Group gap="xs">
        <Badge variant="light" color={trace.cached ? "teal" : "indigo"}>
          {trace.cached ? "DSL from cache" : "DSL from LLM"}
        </Badge>
        <Badge variant="light" color="gray">
          {tookMs} ms total
        </Badge>
        <Badge variant="light" color="gray">
          {trace.parameters.length} bind parameter(s)
        </Badge>
      </Group>
      <Block title="DSL" value={trace.dsl} />
      <Block title="SQL" value={trace.sql} />
      {trace.parameters.length > 0 && (
        <Block
          title="Parameters"
          value={trace.parameters.map((p, i) => `?${i + 1} = ${JSON.stringify(p)}`).join("\n")}
        />
      )}
    </Stack>
  );
}
