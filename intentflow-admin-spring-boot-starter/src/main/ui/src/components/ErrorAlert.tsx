import { Alert, List } from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import { ApiError } from "../api/client";

export function ErrorAlert({ error, title }: { error: unknown; title?: string }) {
  if (!error) return null;
  const problem = error instanceof ApiError ? error.problem : undefined;
  const declined = problem?.kind === "unsupported";
  return (
    <Alert
      color={declined ? "blue" : "red"}
      icon={<IconAlertCircle size={18} />}
      title={title ?? (declined ? "The model declined the question" : "Something went wrong")}
    >
      {problem?.errors?.length ? (
        <List size="sm">
          {problem.errors.map((e) => (
            <List.Item key={e}>{e}</List.Item>
          ))}
        </List>
      ) : (
        (error as Error).message
      )}
    </Alert>
  );
}
