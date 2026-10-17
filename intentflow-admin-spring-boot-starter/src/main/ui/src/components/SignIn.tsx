import { Alert, Anchor, Button, Card, Center, Group, Image, PasswordInput, Stack, Text, Title } from "@mantine/core";
import { IconLock, IconLogin } from "@tabler/icons-react";
import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { ApiError, storeToken, storedToken } from "../api/client";
import logo from "../assets/intentflow.svg";

export function SignIn({ error }: { error: unknown }) {
  const client = useQueryClient();
  const [token, setToken] = useState("");
  const problem = error instanceof ApiError ? error.problem : undefined;
  const forbidden = problem?.status === 403;
  const login = problem?.login ?? null;

  const useToken = () => {
    storeToken(token.trim() || null);
    client.invalidateQueries();
  };

  return (
    <Center mih="100vh" p="md">
      <Card withBorder padding="xl" w={440} maw="100%">
        <Stack gap="md">
          <Group gap="sm">
            <Image src={logo} alt="" w={36} h={36} radius="sm" />
            <Title order={3}>IntentFlow Admin</Title>
          </Group>
          {forbidden ? (
            <Alert color="orange" icon={<IconLock size={18} />} title="No access">
              {problem?.detail}
            </Alert>
          ) : problem?.status === 401 ? (
            <Text size="sm" c="dimmed">
              Sign in with the application's account to change descriptions and paths.
            </Text>
          ) : (
            <Alert color="red" title="The admin API is not reachable">
              {(error as Error)?.message}
            </Alert>
          )}
          {login && (
            <Button component="a" href={login} leftSection={<IconLogin size={16} />}>
              Sign in
            </Button>
          )}
          {!forbidden && (
            <Stack gap="xs">
              <PasswordInput
                label={login ? "Or use an access token" : "Access token"}
                description="Sent as a bearer token, for applications that authenticate API calls with tokens"
                value={token}
                onChange={(event) => setToken(event.currentTarget.value)}
              />
              <Group justify="space-between">
                {storedToken() && (
                  <Anchor
                    size="sm"
                    component="button"
                    onClick={() => {
                      storeToken(null);
                      client.invalidateQueries();
                    }}
                  >
                    Forget the stored token
                  </Anchor>
                )}
                <Button variant="default" ml="auto" disabled={!token.trim()} onClick={useToken}>
                  Use token
                </Button>
              </Group>
            </Stack>
          )}
        </Stack>
      </Card>
    </Center>
  );
}
