import { BarChart } from "@mantine/charts";
import { Group, Paper, SimpleGrid, Stack, Table, Text, Title } from "@mantine/core";
import type { Aggregation } from "../api/types";

const number = new Intl.NumberFormat("en-US", { maximumFractionDigits: 2 });

function format(value: unknown): string {
  if (value === null || value === undefined) return "—";
  if (typeof value === "number") return number.format(value);
  return String(value);
}

export function AggregationView({ aggregation }: { aggregation: Aggregation }) {
  const label = aggregation.field ? `${aggregation.function} of ${aggregation.field}` : aggregation.function;

  if (aggregation.groupBy.length === 0) {
    return (
      <Paper withBorder p="xl" radius="md" maw={360}>
        <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
          {label}
        </Text>
        <Title order={1} mt={4}>
          {format(aggregation.rows[0]?.value)}
        </Title>
      </Paper>
    );
  }

  const data = aggregation.rows.map((row) => ({
    group: row.group.map(format).join(" · "),
    value: typeof row.value === "number" ? row.value : 0,
  }));

  return (
    <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="xl">
      <Stack gap="xs">
        <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
          {label} by {aggregation.groupBy.join(", ")}
        </Text>
        <BarChart
          h={Math.max(220, data.length * 34)}
          data={data}
          dataKey="group"
          orientation="vertical"
          yAxisProps={{ width: 140 }}
          series={[{ name: "value", label, color: "indigo.6" }]}
          valueFormatter={(v) => number.format(v)}
          gridAxis="x"
          barProps={{ radius: 4 }}
        />
      </Stack>
      <Table verticalSpacing="xs" highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            {aggregation.groupBy.map((g) => (
              <Table.Th key={g}>{g}</Table.Th>
            ))}
            <Table.Th ta="right">{label}</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {aggregation.rows.map((row, i) => (
            <Table.Tr key={i}>
              {row.group.map((g, j) => (
                <Table.Td key={j}>{format(g)}</Table.Td>
              ))}
              <Table.Td ta="right">
                <Group justify="flex-end">
                  <Text fw={600} size="sm">
                    {format(row.value)}
                  </Text>
                </Group>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </SimpleGrid>
  );
}
