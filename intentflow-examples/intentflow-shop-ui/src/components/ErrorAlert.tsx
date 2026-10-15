import { Alert, List } from "@mantine/core";
import { IconAlertTriangle, IconInfoCircle } from "@tabler/icons-react";
import { ApiError } from "../api/client";

export function ErrorAlert({ error }: { error: unknown }) {
  const problem = error instanceof ApiError ? error.problem : undefined;
  const detail = problem?.detail ?? (error instanceof Error ? error.message : String(error));

  if (problem?.kind === "unsupported") {
    return (
      <Alert color="blue" variant="light" icon={<IconInfoCircle />} title="The assistant can't answer that">
        {detail}
      </Alert>
    );
  }

  return (
    <Alert
      color={problem && problem.status < 500 ? "orange" : "red"}
      variant="light"
      icon={<IconAlertTriangle />}
      title={problem?.title ?? "Request failed"}
    >
      {detail}
      {problem?.errors && (
        <List size="sm" mt="xs">
          {Object.entries(problem.errors).map(([field, message]) => (
            <List.Item key={field}>
              {field}: {message}
            </List.Item>
          ))}
        </List>
      )}
    </Alert>
  );
}
