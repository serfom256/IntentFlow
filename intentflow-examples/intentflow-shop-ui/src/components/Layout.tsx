import {
  ActionIcon,
  AppShell,
  Burger,
  Group,
  Image,
  Kbd,
  NavLink,
  Stack,
  Text,
  Tooltip,
  UnstyledButton,
  useMantineColorScheme,
} from "@mantine/core";
import { useDisclosure } from "@mantine/hooks";
import { spotlight } from "@mantine/spotlight";
import {
  IconAdjustments,
  IconBox,
  IconLayoutDashboard,
  IconMoon,
  IconReceipt,
  IconSearch,
  IconSparkles,
  IconSun,
  IconUsers,
} from "@tabler/icons-react";
import { Link, Outlet, useRouterState } from "@tanstack/react-router";
import logo from "../assets/intentflow.svg";

const links = [
  { to: "/", label: "Ask", icon: IconSparkles },
  { to: "/overview", label: "Overview", icon: IconLayoutDashboard },
  { to: "/products", label: "Products", icon: IconBox },
  { to: "/customers", label: "Customers", icon: IconUsers },
  { to: "/orders", label: "Orders", icon: IconReceipt },
] as const;

export function Layout() {
  const [opened, { toggle, close }] = useDisclosure();
  const { colorScheme, toggleColorScheme } = useMantineColorScheme();
  const pathname = useRouterState({ select: (s) => s.location.pathname });

  return (
    <AppShell
      header={{ height: 60 }}
      navbar={{ width: 230, breakpoint: "sm", collapsed: { mobile: !opened } }}
      padding="lg"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap">
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
            <Image src={logo} alt="" w={30} h={30} radius="sm" />
            <Text fw={700} size="lg">
              IntentFlow Shop
            </Text>
          </Group>
          <Group gap="sm" wrap="nowrap">
            <UnstyledButton onClick={() => spotlight.open()} visibleFrom="xs">
              <Group
                gap="xs"
                px="sm"
                h={34}
                w={260}
                style={{ border: "1px solid var(--mantine-color-default-border)", borderRadius: 8 }}
              >
                <IconSearch size={16} color="var(--mantine-color-dimmed)" />
                <Text size="sm" c="dimmed" flex={1}>
                  Ask anything…
                </Text>
                <Kbd size="xs">⌘ K</Kbd>
              </Group>
            </UnstyledButton>
            <Tooltip label="Catalog admin: descriptions and paths">
              <ActionIcon
                component="a"
                href="/intentflow-admin/"
                target="_blank"
                variant="default"
                size="lg"
                aria-label="Open the catalog admin"
              >
                <IconAdjustments size={18} />
              </ActionIcon>
            </Tooltip>
            <Tooltip label={colorScheme === "dark" ? "Light theme" : "Dark theme"}>
              <ActionIcon variant="default" size="lg" onClick={toggleColorScheme} aria-label="Toggle color scheme">
                {colorScheme === "dark" ? <IconSun size={18} /> : <IconMoon size={18} />}
              </ActionIcon>
            </Tooltip>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="sm">
        <Stack gap={4}>
          {links.map((link) => {
            const active = link.to === "/" ? pathname === "/" : pathname.startsWith(link.to);
            return (
              <NavLink
                key={link.to}
                component={Link}
                to={link.to}
                label={link.label}
                leftSection={<link.icon size={18} stroke={1.6} />}
                active={active}
                variant="light"
                onClick={close}
                style={{ borderRadius: 8 }}
              />
            );
          })}
        </Stack>
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
