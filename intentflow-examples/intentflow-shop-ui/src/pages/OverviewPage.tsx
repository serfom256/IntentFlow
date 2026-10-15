import { Card, Group, SimpleGrid, Skeleton, Stack, Text, ThemeIcon, Title } from "@mantine/core";
import { IconBox, IconReceipt, IconUsers } from "@tabler/icons-react";
import { Link } from "@tanstack/react-router";
import { usePage, useTotal } from "../api/hooks";
import type { Order, ServiceName } from "../api/types";
import { EntityTable } from "../components/EntityTable";
import { orderEntity } from "../entities";

const stats: { service: ServiceName; label: string; icon: typeof IconBox; color: string }[] = [
  { service: "products", label: "Products", icon: IconBox, color: "indigo" },
  { service: "customers", label: "Customers", icon: IconUsers, color: "teal" },
  { service: "orders", label: "Orders", icon: IconReceipt, color: "violet" },
];

function StatCard({ service, label, icon: Icon, color }: (typeof stats)[number]) {
  const total = useTotal(service);
  return (
    <Card withBorder radius="md" padding="lg" component={Link} to={`/${service}`}>
      <Group justify="space-between">
        <div>
          <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
            {label}
          </Text>
          {total.data === undefined ? (
            <Skeleton h={32} w={80} mt={6} />
          ) : (
            <Title order={2}>{total.data.toLocaleString("en-US")}</Title>
          )}
        </div>
        <ThemeIcon size={48} radius="md" variant="light" color={color}>
          <Icon size={26} />
        </ThemeIcon>
      </Group>
    </Card>
  );
}

export function OverviewPage() {
  const recent = usePage<Order>("orders", 0, 8);
  return (
    <Stack gap="lg" maw={1280} mx="auto">
      <Title order={2}>Overview</Title>
      <SimpleGrid cols={{ base: 1, sm: 3 }}>
        {stats.map((s) => (
          <StatCard key={s.service} {...s} />
        ))}
      </SimpleGrid>
      <Card withBorder radius="md" padding={0}>
        <Text fw={600} p="md">
          Latest orders
        </Text>
        <EntityTable columns={orderEntity.columns} rows={recent.data?.items ?? []} loading={recent.isLoading} skeletonRows={8} />
      </Card>
    </Stack>
  );
}
