import {
  ActionIcon,
  Badge,
  Button,
  Card,
  CloseButton,
  Group,
  Paper,
  SegmentedControl,
  SimpleGrid,
  Skeleton,
  Stack,
  Tabs,
  Text,
  TextInput,
  ThemeIcon,
  Title,
  UnstyledButton,
} from "@mantine/core";
import { useLocalStorage } from "@mantine/hooks";
import {
  IconArrowRight,
  IconBox,
  IconChartBar,
  IconCode,
  IconHistory,
  IconReceipt,
  IconSparkles,
  IconTable,
  IconUsers,
} from "@tabler/icons-react";
import { getRouteApi } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { useSearch, type Scope } from "../api/hooks";
import type { SearchResult, ServiceName } from "../api/types";
import { AggregationView } from "../components/AggregationView";
import { EntityTable } from "../components/EntityTable";
import { ErrorAlert } from "../components/ErrorAlert";
import { TracePanel } from "../components/TracePanel";
import { entities } from "../entities";

const route = getRouteApi("/");

const scopes: { value: Scope; label: string }[] = [
  { value: "all", label: "All" },
  { value: "products", label: "Products" },
  { value: "customers", label: "Customers" },
  { value: "orders", label: "Orders" },
];

const serviceIcon: Record<ServiceName, typeof IconBox> = {
  products: IconBox,
  customers: IconUsers,
  orders: IconReceipt,
};

const examples: { service: ServiceName; question: string }[] = [
  { service: "products", question: "samsung or google phones under 800" },
  { service: "products", question: "average price per category" },
  { service: "orders", question: "delivered orders from vip customers" },
  { service: "orders", question: "how many orders are there per status?" },
  { service: "customers", question: "customers who have a cancelled order" },
  { service: "products", question: "products with ultra in the name" },
];

export function SearchPage() {
  const params = route.useSearch();
  const q = params.q ?? "";
  const scope = params.scope ?? "all";
  const navigate = route.useNavigate();
  const [input, setInput] = useState(q);
  const [recent, setRecent] = useLocalStorage<string[]>({ key: "intentflow-recent-searches", defaultValue: [] });
  useEffect(() => setInput(q), [q]);

  const search = useSearch(q, scope);

  const submit = (text: string) => {
    const value = text.trim();
    if (value) {
      setRecent((list) => [value, ...list.filter((r) => r !== value)].slice(0, 8));
    }
    navigate({ search: (prev) => ({ ...prev, q: value || undefined }) });
  };

  return (
    <Stack gap="xl" maw={1280} mx="auto">
      <Paper
        p="xl"
        radius="lg"
        style={{
          background:
            "linear-gradient(135deg, var(--mantine-color-indigo-light) 0%, var(--mantine-color-violet-light) 100%)",
        }}
      >
        <Stack gap="md">
          <Group gap="sm">
            <ThemeIcon size={38} radius="md" variant="gradient" gradient={{ from: "indigo", to: "violet" }}>
              <IconSparkles size={22} />
            </ThemeIcon>
            <div>
              <Title order={2}>Ask the shop</Title>
              <Text c="dimmed" size="sm">
                Questions in plain language are translated into the IntentFlow DSL, validated and run as SQL
              </Text>
            </div>
          </Group>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              submit(input);
            }}
          >
            <Group align="flex-start" wrap="nowrap" gap="sm">
              <TextInput
                flex={1}
                size="lg"
                radius="md"
                placeholder="e.g. apple laptops under 2000, best rated first"
                value={input}
                maxLength={500}
                onChange={(e) => setInput(e.currentTarget.value)}
                rightSection={input && <CloseButton onClick={() => setInput("")} aria-label="Clear" />}
              />
              <Button type="submit" size="lg" radius="md" loading={search.isFetching} disabled={!input.trim()}>
                Ask
              </Button>
            </Group>
          </form>
          <Group justify="space-between">
            <SegmentedControl
              value={scope}
              data={scopes}
              radius="md"
              onChange={(value) =>
                navigate({ search: (prev) => ({ ...prev, scope: value === "all" ? undefined : (value as Scope) }) })
              }
            />
            {recent.length > 0 && (
              <Group gap={6}>
                <IconHistory size={16} color="var(--mantine-color-dimmed)" />
                {recent.slice(0, 4).map((r) => (
                  <Badge
                    key={r}
                    variant="default"
                    tt="none"
                    fw={500}
                    style={{ cursor: "pointer" }}
                    onClick={() => submit(r)}
                  >
                    {r}
                  </Badge>
                ))}
              </Group>
            )}
          </Group>
        </Stack>
      </Paper>

      {!q && (
        <div>
          <Text fw={600} mb="sm">
            Try asking
          </Text>
          <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
            {examples.map((example) => {
              const Icon = serviceIcon[example.service];
              return (
                <UnstyledButton key={example.question} onClick={() => submit(example.question)}>
                  <Card withBorder radius="md" padding="md" h="100%">
                    <Group justify="space-between" wrap="nowrap">
                      <Group gap="sm" wrap="nowrap">
                        <ThemeIcon variant="light" radius="md" size="lg">
                          <Icon size={18} />
                        </ThemeIcon>
                        <Text size="sm">{example.question}</Text>
                      </Group>
                      <IconArrowRight size={16} color="var(--mantine-color-dimmed)" />
                    </Group>
                  </Card>
                </UnstyledButton>
              );
            })}
          </SimpleGrid>
        </div>
      )}

      {search.isLoading && (
        <Card withBorder radius="md" padding="lg">
          <Stack gap="sm">
            <Skeleton h={24} w={240} />
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} h={36} />
            ))}
          </Stack>
        </Card>
      )}
      {search.isError && <ErrorAlert error={search.error} />}
      {search.data && <Results result={search.data} />}
    </Stack>
  );
}

function Results({ result }: { result: SearchResult }) {
  const entity = entities[result.service];
  const Icon = serviceIcon[result.service];
  const count = result.aggregation ? result.aggregation.rows.length : result.items.length;
  return (
    <Card withBorder radius="md" padding="lg">
      <Tabs defaultValue="results" keepMounted={false}>
        <Group justify="space-between" mb="md" wrap="wrap">
          <Group gap="sm">
            <ThemeIcon variant="light" radius="md" size="lg">
              <Icon size={18} />
            </ThemeIcon>
            <div>
              <Text fw={600} tt="capitalize">
                {result.service}
              </Text>
              <Text size="xs" c="dimmed">
                {result.aggregation ? `${count} group(s)` : `${count} result(s)`} · {result.tookMs} ms ·{" "}
                <Text span ff="monospace" size="xs">
                  {result.uri}
                </Text>
              </Text>
            </div>
          </Group>
          <Tabs.List>
            <Tabs.Tab value="results" leftSection={result.aggregation ? <IconChartBar size={16} /> : <IconTable size={16} />}>
              Results
            </Tabs.Tab>
            <Tabs.Tab value="trace" leftSection={<IconCode size={16} />}>
              Trace
            </Tabs.Tab>
          </Tabs.List>
        </Group>
        <Tabs.Panel value="results">
          {result.aggregation ? (
            <AggregationView aggregation={result.aggregation} />
          ) : (
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            <EntityTable columns={entity.columns as any} rows={result.items} />
          )}
        </Tabs.Panel>
        <Tabs.Panel value="trace">
          {result.trace ? <TracePanel trace={result.trace} tookMs={result.tookMs} /> : null}
        </Tabs.Panel>
      </Tabs>
      {count === 50 && !result.aggregation && (
        <Group mt="md" gap={6}>
          <ActionIcon variant="transparent" color="gray" size="xs">
            <IconTable size={14} />
          </ActionIcon>
          <Text size="xs" c="dimmed">
            Showing the first 50 matches; refine the question to narrow them down
          </Text>
        </Group>
      )}
    </Card>
  );
}
