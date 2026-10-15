import { Spotlight, type SpotlightActionData } from "@mantine/spotlight";
import { IconBox, IconLayoutDashboard, IconReceipt, IconSearch, IconSparkles, IconUsers } from "@tabler/icons-react";
import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";

export function CommandPalette() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");

  const ask = (q: string) => navigate({ to: "/", search: { q } });

  const pages: SpotlightActionData[] = [
    { id: "overview", label: "Overview", description: "Totals and recent activity", leftSection: <IconLayoutDashboard size={20} />, onClick: () => navigate({ to: "/overview" }) },
    { id: "products", label: "Products", description: "Browse and edit the catalog", leftSection: <IconBox size={20} />, onClick: () => navigate({ to: "/products" }) },
    { id: "customers", label: "Customers", description: "Browse and edit customers", leftSection: <IconUsers size={20} />, onClick: () => navigate({ to: "/customers" }) },
    { id: "orders", label: "Orders", description: "Browse and edit orders", leftSection: <IconReceipt size={20} />, onClick: () => navigate({ to: "/orders" }) },
  ];

  const actions: SpotlightActionData[] = query.trim()
    ? [
        {
          id: "ask",
          label: `Ask: ${query.trim()}`,
          description: "Translate the question with the LLM and run it",
          leftSection: <IconSparkles size={20} />,
          onClick: () => ask(query.trim()),
        },
        ...pages.filter((p) => String(p.label).toLowerCase().includes(query.trim().toLowerCase())),
      ]
    : pages;

  return (
    <Spotlight
      actions={actions}
      query={query}
      onQueryChange={setQuery}
      filter={(_, a) => a}
      shortcut={["mod + K", "/"]}
      nothingFound="Type a question"
      highlightQuery
      searchProps={{ leftSection: <IconSearch size={18} />, placeholder: "Ask a question or jump to a page…" }}
    />
  );
}
