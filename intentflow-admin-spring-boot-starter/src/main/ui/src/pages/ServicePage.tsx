import {
  ActionIcon,
  Badge,
  Button,
  Card,
  Group,
  Loader,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
  Title,
  Tooltip,
} from "@mantine/core";
import { useForm } from "@mantine/form";
import { modals } from "@mantine/modals";
import { notifications } from "@mantine/notifications";
import { IconArrowBackUp, IconDeviceFloppy, IconRestore } from "@tabler/icons-react";
import { useEffect } from "react";
import { useResetOverride, useSaveOverride, useService } from "../api/hooks";
import type { ServiceView } from "../api/types";
import { ErrorAlert } from "../components/ErrorAlert";
import { ExplainPanel } from "../components/ExplainPanel";

interface Values {
  description: string;
  uri: string;
  fields: Record<string, string>;
}

const MAX_DESCRIPTION = 1000;

function valuesOf(service: ServiceView): Values {
  return {
    description: service.overrideDescription ?? "",
    uri: service.overrideUri ?? "",
    fields: Object.fromEntries(service.fields.map((f) => [f.name, f.overrideDescription ?? ""])),
  };
}

function singleLine(label: string, value: string, max: number): string | null {
  if (value.length > max) return `${label} is longer than ${max} characters`;
  if (/[\r\n\t]/.test(value)) return `${label} must be a single line`;
  return null;
}

function ChangedBadge() {
  return (
    <Badge size="xs" variant="light" color="orange">
      changed
    </Badge>
  );
}

export function ServicePage({ name }: { name: string }) {
  const service = useService(name);
  const save = useSaveOverride(name);
  const reset = useResetOverride(name);
  const fieldNames = service.data?.fields.map((f) => f.name) ?? [];

  const form = useForm<Values>({
    mode: "controlled",
    initialValues: { description: "", uri: "", fields: {} },
    validate: {
      description: (value) => singleLine("Description", value, MAX_DESCRIPTION),
      uri: (value) => {
        if (!value) return null;
        if (!value.startsWith("/") && !value.includes("://")) return "Start with / or use an absolute URL";
        const unknown = [...value.matchAll(/\{\{(\w+)}}/g)].map((m) => m[1]).filter((p) => !fieldNames.includes(p));
        return unknown.length ? `Unknown placeholder: ${unknown.map((p) => `{{${p}}}`).join(", ")}` : null;
      },
      fields: (values) => {
        for (const [field, value] of Object.entries(values)) {
          const problem = singleLine(`Description of ${field}`, value, MAX_DESCRIPTION);
          if (problem) return problem;
        }
        return null;
      },
    },
  });

  useEffect(() => {
    if (service.data) {
      const values = valuesOf(service.data);
      form.setValues(values);
      form.resetDirty(values);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [service.data]);

  if (service.isPending) return <Loader />;
  if (service.error || !service.data) return <ErrorAlert error={service.error} />;
  const view = service.data;

  const submit = form.onSubmit((values) => {
    save.mutate(
      {
        description: values.description.trim() || null,
        uri: values.uri.trim() || null,
        fields: Object.fromEntries(
          Object.entries(values.fields)
            .map(([field, text]) => [field, text.trim()] as const)
            .filter(([, text]) => text !== ""),
        ),
      },
      { onSuccess: () => notifications.show({ color: "teal", message: `Saved ${view.name}` }) },
    );
  });

  const confirmReset = () =>
    modals.openConfirmModal({
      title: `Reset ${view.name}?`,
      children: <Text size="sm">All overrides of this service are removed and the declared values apply again.</Text>,
      labels: { confirm: "Reset", cancel: "Cancel" },
      confirmProps: { color: "red" },
      onConfirm: () =>
        reset.mutate(undefined, {
          onSuccess: () => notifications.show({ message: `${view.name} uses the declared values again` }),
        }),
    });

  const hasOverrides =
    view.overrideDescription !== null || view.overrideUri !== null || view.fields.some((f) => f.overrideDescription);

  return (
    <Stack gap="lg" maw={1100}>
      <Group justify="space-between" align="flex-end">
        <div>
          <Title order={2}>{view.name}</Title>
          <Text c="dimmed" size="sm">
            {view.entity} · table <Text span ff="monospace">{view.table}</Text>
            {view.relations.length > 0 && ` · relations: ${view.relations.map((r) => `${r.name} → ${r.target}`).join(", ")}`}
          </Text>
        </div>
        <Button
          variant="default"
          color="red"
          leftSection={<IconRestore size={16} />}
          disabled={!hasOverrides}
          loading={reset.isPending}
          onClick={confirmReset}
        >
          Reset to declared
        </Button>
      </Group>

      <form onSubmit={submit}>
        <Stack gap="lg">
          <Card withBorder padding="lg">
            <Stack gap="md">
              <Textarea
                label={
                  <Group gap={6}>
                    Service description {form.values.description && <ChangedBadge />}
                  </Group>
                }
                description={`Declared: ${view.declaredDescription ?? "none"}`}
                placeholder={view.declaredDescription ?? "What the service contains, in the words users ask in"}
                autosize
                minRows={1}
                maxRows={4}
                {...form.getInputProps("description")}
              />
              <TextInput
                label={
                  <Group gap={6}>
                    Path {form.values.uri && <ChangedBadge />}
                  </Group>
                }
                description={`Declared: ${view.declaredUri}. Placeholders take the value of the first result.`}
                placeholder={view.declaredUri}
                ff="monospace"
                {...form.getInputProps("uri")}
              />
              <Group gap={6}>
                <Text size="xs" c="dimmed">
                  Insert:
                </Text>
                {view.fields.map((f) => (
                  <Badge
                    key={f.name}
                    variant="outline"
                    style={{ cursor: "pointer", textTransform: "none" }}
                    onClick={() => form.setFieldValue("uri", `${form.values.uri || view.declaredUri.replace(/\{\{\w+}}/g, "")}{{${f.name}}}`)}
                  >
                    {`{{${f.name}}}`}
                  </Badge>
                ))}
              </Group>
            </Stack>
          </Card>

          <Card withBorder padding={0}>
            <Table.ScrollContainer minWidth={720}>
              <Table verticalSpacing="sm" horizontalSpacing="md">
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th w={260}>Field</Table.Th>
                    <Table.Th>Description for the LLM</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {view.fields.map((field) => {
                    const value = form.values.fields[field.name] ?? "";
                    return (
                      <Table.Tr key={field.name}>
                        <Table.Td valign="top">
                          <Group gap={6}>
                            <Text fw={600} size="sm">
                              {field.name}
                            </Text>
                            <Badge size="xs" variant="light">
                              {field.type.toLowerCase()}
                            </Badge>
                            {value && <ChangedBadge />}
                          </Group>
                          <Text size="xs" c="dimmed" ff="monospace">
                            {field.column}
                          </Text>
                          {field.allowedValues.length > 0 && (
                            <Text size="xs" c="dimmed" lineClamp={2}>
                              {field.allowedValues.join(", ")}
                            </Text>
                          )}
                        </Table.Td>
                        <Table.Td>
                          <Group gap="xs" wrap="nowrap" align="flex-start">
                            <Textarea
                              flex={1}
                              autosize
                              minRows={1}
                              maxRows={4}
                              placeholder={field.declaredDescription ?? "No description declared"}
                              description={field.declaredDescription ? `Declared: ${field.declaredDescription}` : undefined}
                              value={value}
                              onChange={(event) => form.setFieldValue(`fields.${field.name}`, event.currentTarget.value)}
                            />
                            <Tooltip label="Use the declared description">
                              <ActionIcon
                                variant="subtle"
                                color="gray"
                                mt={field.declaredDescription ? 22 : 4}
                                disabled={!value}
                                onClick={() => form.setFieldValue(`fields.${field.name}`, "")}
                                aria-label={`Clear the override of ${field.name}`}
                              >
                                <IconArrowBackUp size={16} />
                              </ActionIcon>
                            </Tooltip>
                          </Group>
                        </Table.Td>
                      </Table.Tr>
                    );
                  })}
                </Table.Tbody>
              </Table>
            </Table.ScrollContainer>
          </Card>

          {form.errors.fields && <Text c="red" size="sm">{form.errors.fields}</Text>}
          <ErrorAlert error={save.error} title="The changes were not saved" />
          <Group justify="flex-end">
            <Button variant="default" disabled={!form.isDirty()} onClick={() => form.reset()}>
              Discard
            </Button>
            <Button type="submit" leftSection={<IconDeviceFloppy size={16} />} disabled={!form.isDirty()} loading={save.isPending}>
              Save
            </Button>
          </Group>
        </Stack>
      </form>

      <ExplainPanel service={view.name} />
    </Stack>
  );
}
