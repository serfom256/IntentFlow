import { Alert, Anchor, Badge, Card, Loader, Stack, Table, Text, Title } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import { Link } from "@tanstack/react-router";
import { useServices } from "../api/hooks";
import { overrideCount } from "../api/types";
import { ErrorAlert } from "../components/ErrorAlert";
import { ExplainPanel } from "../components/ExplainPanel";

export function OverviewPage() {
  const services = useServices();

  return (
    <Stack gap="lg" maw={1100}>
      <div>
        <Title order={2}>Catalog</Title>
        <Text c="dimmed">
          Descriptions and paths declared with annotations or builders, and the runtime overrides that replace them
        </Text>
      </div>
      <Alert variant="light" icon={<IconInfoCircle size={18} />}>
        Descriptions are sent to the LLM with every question, so a change takes effect on the next question.
        Translations cached before the change are not reused.
      </Alert>
      <ErrorAlert error={services.error} />
      {services.isPending ? (
        <Loader />
      ) : (
        <Card withBorder padding={0}>
          <Table.ScrollContainer minWidth={640}>
            <Table verticalSpacing="sm" horizontalSpacing="md" highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Service</Table.Th>
                  <Table.Th>Entity</Table.Th>
                  <Table.Th>Path</Table.Th>
                  <Table.Th ta="right">Fields</Table.Th>
                  <Table.Th>Overrides</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {services.data?.map((service) => {
                  const count = overrideCount(service);
                  return (
                    <Table.Tr key={service.name}>
                      <Table.Td>
                        <Anchor component={Link} to={`/services/${service.name}`} fw={600}>
                          {service.name}
                        </Anchor>
                        <Text size="xs" c="dimmed" lineClamp={1}>
                          {service.overrideDescription ?? service.declaredDescription ?? "No description"}
                        </Text>
                      </Table.Td>
                      <Table.Td>{service.entity}</Table.Td>
                      <Table.Td>
                        <Text size="sm" ff="monospace">
                          {service.overrideUri ?? service.declaredUri}
                        </Text>
                      </Table.Td>
                      <Table.Td ta="right">{service.fields.length}</Table.Td>
                      <Table.Td>
                        {count > 0 ? (
                          <Badge variant="light" color="orange">
                            {count} changed
                          </Badge>
                        ) : (
                          <Badge variant="light" color="gray">
                            as declared
                          </Badge>
                        )}
                      </Table.Td>
                    </Table.Tr>
                  );
                })}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        </Card>
      )}
      <ExplainPanel services={services.data?.map((s) => s.name)} />
    </Stack>
  );
}
