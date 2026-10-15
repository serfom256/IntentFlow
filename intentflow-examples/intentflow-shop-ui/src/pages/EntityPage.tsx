import { ActionIcon, Badge, Button, Card, Group, Pagination, Select, Stack, Text, Title, Tooltip } from "@mantine/core";
import { modals } from "@mantine/modals";
import { notifications } from "@mantine/notifications";
import { IconEdit, IconPlus, IconTrash } from "@tabler/icons-react";
import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { useDelete, usePage, useSave } from "../api/hooks";
import { EntityForm } from "../components/EntityForm";
import { EntityTable } from "../components/EntityTable";
import { ErrorAlert } from "../components/ErrorAlert";
import type { EntityDefinition, FormValues } from "../entities";

const sizes = ["10", "20", "50", "100"];

interface Props<T extends { id: number }, R> {
  entity: EntityDefinition<T, R>;
  page: number;
  size: number;
}

export function EntityPage<T extends { id: number }, R>({ entity, page, size }: Props<T, R>) {
  const navigate = useNavigate();
  const data = usePage<T>(entity.service, page - 1, size);
  const save = useSave<R>(entity.service);
  const remove = useDelete(entity.service);
  const [editing, setEditing] = useState<{ id?: number; values: FormValues } | null>(null);

  const go = (nextPage: number, nextSize = size) =>
    navigate({
      to: `/${entity.service}`,
      search: { page: nextPage > 1 ? nextPage : undefined, size: nextSize !== 20 ? nextSize : undefined },
    });

  const open = (row?: T) => {
    save.reset();
    setEditing(row ? { id: row.id, values: entity.toValues(row) } : { values: entity.empty });
  };

  const confirmDelete = (row: T) =>
    modals.openConfirmModal({
      title: `Delete ${entity.singular} #${row.id}?`,
      centered: true,
      children: (
        <Text size="sm">
          {entity.service === "customers" ? "The customer's orders are deleted as well." : "This cannot be undone."}
        </Text>
      ),
      labels: { confirm: "Delete", cancel: "Cancel" },
      confirmProps: { color: "red" },
      onConfirm: () =>
        remove.mutate(row.id, {
          onSuccess: () => notifications.show({ color: "teal", message: `Deleted ${entity.singular} #${row.id}` }),
          onError: (error) => notifications.show({ color: "red", title: "Delete failed", message: error.message }),
        }),
    });

  const total = data.data?.totalItems;
  const from = total ? (page - 1) * size + 1 : 0;
  const to = total ? Math.min(page * size, total) : 0;

  return (
    <Stack gap="lg" maw={1280} mx="auto">
      <Group justify="space-between">
        <Group gap="sm">
          <Title order={2}>{entity.title}</Title>
          {total !== undefined && (
            <Badge variant="light" size="lg">
              {total.toLocaleString("en-US")}
            </Badge>
          )}
        </Group>
        <Button leftSection={<IconPlus size={16} />} onClick={() => open()}>
          New {entity.singular}
        </Button>
      </Group>

      {data.isError && <ErrorAlert error={data.error} />}

      <Card withBorder radius="md" padding={0}>
        <EntityTable
          columns={entity.columns}
          rows={data.data?.items ?? []}
          loading={data.isLoading}
          skeletonRows={Math.min(size, 12)}
          actions={(row) => (
            <Group gap={4} justify="flex-end" wrap="nowrap">
              <Tooltip label="Edit" withArrow>
                <ActionIcon variant="subtle" color="gray" onClick={() => open(row)} aria-label="Edit">
                  <IconEdit size={16} />
                </ActionIcon>
              </Tooltip>
              <Tooltip label="Delete" withArrow>
                <ActionIcon variant="subtle" color="red" onClick={() => confirmDelete(row)} aria-label="Delete">
                  <IconTrash size={16} />
                </ActionIcon>
              </Tooltip>
            </Group>
          )}
        />
        <Group justify="space-between" p="md" style={{ borderTop: "1px solid var(--mantine-color-default-border)" }}>
          <Group gap="xs">
            <Text size="sm" c="dimmed">
              {total ? `${from}–${to} of ${total.toLocaleString("en-US")}` : ""}
            </Text>
            <Select
              size="xs"
              w={90}
              data={sizes}
              value={String(size)}
              allowDeselect={false}
              onChange={(value) => value && go(1, Number(value))}
              aria-label="Rows per page"
            />
          </Group>
          {data.data && data.data.totalPages > 1 && (
            <Pagination total={data.data.totalPages} value={page} onChange={(next) => go(next)} size="sm" siblings={1} />
          )}
        </Group>
      </Card>

      <EntityForm
        title={editing?.id === undefined ? `New ${entity.singular}` : `Edit ${entity.singular} #${editing.id}`}
        opened={editing !== null}
        fields={entity.form}
        initial={editing?.values ?? entity.empty}
        saving={save.isPending}
        error={save.error}
        onClose={() => setEditing(null)}
        onSubmit={(values) =>
          save.mutate(
            { id: editing?.id, request: entity.toRequest(values) },
            {
              onSuccess: () => {
                notifications.show({ color: "teal", message: `Saved ${entity.singular}` });
                setEditing(null);
              },
            },
          )
        }
      />
    </Stack>
  );
}
