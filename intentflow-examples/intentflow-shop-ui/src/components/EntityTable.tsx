import { Center, ScrollArea, Skeleton, Stack, Table, Text, ThemeIcon } from "@mantine/core";
import { IconDatabaseOff } from "@tabler/icons-react";
import type { ReactNode } from "react";
import type { Column } from "../entities";

interface Props<T extends { id: number }> {
  columns: Column<T>[];
  rows: T[];
  loading?: boolean;
  skeletonRows?: number;
  actions?: (row: T) => ReactNode;
}

export function EntityTable<T extends { id: number }>({ columns, rows, loading, skeletonRows = 10, actions }: Props<T>) {
  if (!loading && rows.length === 0) {
    return (
      <Center py={48}>
        <Stack align="center" gap="xs">
          <ThemeIcon variant="light" color="gray" size={48} radius="xl">
            <IconDatabaseOff size={24} />
          </ThemeIcon>
          <Text c="dimmed">Nothing matched</Text>
        </Stack>
      </Center>
    );
  }
  return (
    <ScrollArea>
      <Table striped highlightOnHover verticalSpacing="sm" stickyHeader miw={720}>
        <Table.Thead>
          <Table.Tr>
            {columns.map((c) => (
              <Table.Th key={c.key} ta={c.align}>
                {c.label}
              </Table.Th>
            ))}
            {actions && <Table.Th w={90} />}
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {loading
            ? Array.from({ length: skeletonRows }, (_, i) => (
                <Table.Tr key={i}>
                  {columns.map((c) => (
                    <Table.Td key={c.key}>
                      <Skeleton h={18} />
                    </Table.Td>
                  ))}
                  {actions && <Table.Td />}
                </Table.Tr>
              ))
            : rows.map((row) => (
                <Table.Tr key={row.id}>
                  {columns.map((c) => (
                    <Table.Td key={c.key} ta={c.align}>
                      {c.render(row)}
                    </Table.Td>
                  ))}
                  {actions && <Table.Td ta="right">{actions(row)}</Table.Td>}
                </Table.Tr>
              ))}
        </Table.Tbody>
      </Table>
    </ScrollArea>
  );
}
