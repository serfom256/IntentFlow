import { Anchor, Badge, Group, HoverCard, Loader, Stack, Text } from "@mantine/core";
import { useState } from "react";
import { useEntity } from "../api/hooks";
import type { Customer } from "../api/types";

export function CustomerLink({ id }: { id: number }) {
  const [opened, setOpened] = useState(false);
  const customer = useEntity<Customer>("customers", id, opened);
  return (
    <HoverCard width={260} shadow="md" openDelay={250} onOpen={() => setOpened(true)} withArrow>
      <HoverCard.Target>
        <Anchor size="sm" component="span">
          #{id}
        </Anchor>
      </HoverCard.Target>
      <HoverCard.Dropdown>
        {customer.data ? (
          <Stack gap={4}>
            <Group justify="space-between">
              <Text fw={600} size="sm">
                {customer.data.fullName}
              </Text>
              {customer.data.vip && <Badge color="yellow" size="sm">VIP</Badge>}
            </Group>
            <Text size="xs" c="dimmed">
              {customer.data.email}
            </Text>
            <Text size="xs">
              {customer.data.city}, {customer.data.country}
            </Text>
          </Stack>
        ) : customer.isError ? (
          <Text size="sm" c="red">
            Customer not found
          </Text>
        ) : (
          <Loader size="sm" />
        )}
      </HoverCard.Dropdown>
    </HoverCard>
  );
}
