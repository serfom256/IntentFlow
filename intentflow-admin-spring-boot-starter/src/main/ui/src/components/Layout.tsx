import {
  ActionIcon,
  AppShell,
  Badge,
  Burger,
  Center,
  Group,
  Image,
  Loader,
  NavLink,
  ScrollArea,
  Text,
  Tooltip,
  useMantineColorScheme,
} from "@mantine/core";
import { useDisclosure } from "@mantine/hooks";
import { IconDatabase, IconLayoutDashboard, IconMoon, IconSun } from "@tabler/icons-react";
import { Link, Outlet, useRouterState } from "@tanstack/react-router";
import { useServices, useSession } from "../api/hooks";
import { overrideCount } from "../api/types";
import logo from "../assets/intentflow.svg";
import { SignIn } from "./SignIn";

export function Layout() {
  const [opened, { toggle, close }] = useDisclosure();
  const { colorScheme, toggleColorScheme } = useMantineColorScheme();
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const session = useSession();
  const services = useServices();

  if (session.isPending) {
    return (
      <Center mih="100vh">
        <Loader />
      </Center>
    );
  }
  if (session.error) {
    return <SignIn error={session.error} />;
  }

  return (
    <AppShell header={{ height: 60 }} navbar={{ width: 260, breakpoint: "sm", collapsed: { mobile: !opened } }} padding="lg">
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap">
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
            <Image src={logo} alt="" w={30} h={30} radius="sm" />
            <Text fw={700} size="lg">
              IntentFlow Admin
            </Text>
          </Group>
          <Group gap="sm" wrap="nowrap">
            {session.data.user && (
              <Text size="sm" c="dimmed" visibleFrom="xs">
                {session.data.user}
              </Text>
            )}
          <Tooltip label={colorScheme === "dark" ? "Light theme" : "Dark theme"}>
            <ActionIcon variant="default" size="lg" onClick={toggleColorScheme} aria-label="Toggle color scheme">
              {colorScheme === "dark" ? <IconSun size={18} /> : <IconMoon size={18} />}
            </ActionIcon>
          </Tooltip>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="sm">
        <NavLink
          component={Link}
          to="/"
          label="Overview"
          leftSection={<IconLayoutDashboard size={18} />}
          active={pathname === "/"}
          onClick={close}
        />
        <Text size="xs" fw={600} c="dimmed" tt="uppercase" mt="md" mb={4} px="sm">
          Services
        </Text>
        <ScrollArea flex={1}>
          {services.isPending && <Loader size="sm" m="sm" />}
          {services.data?.map((service) => {
            const count = overrideCount(service);
            return (
              <NavLink
                key={service.name}
                component={Link}
                to={`/services/${service.name}`}
                label={service.name}
                description={service.entity}
                leftSection={<IconDatabase size={18} />}
                rightSection={count > 0 ? <Badge size="xs" variant="light" color="orange">{count}</Badge> : null}
                active={pathname === `/services/${service.name}`}
                onClick={close}
              />
            );
          })}
        </ScrollArea>
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
