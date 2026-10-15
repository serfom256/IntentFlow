import { createRootRoute, createRoute, createRouter } from "@tanstack/react-router";
import type { Scope } from "./api/hooks";
import { CommandPalette } from "./components/CommandPalette";
import { Layout } from "./components/Layout";
import { customerEntity, orderEntity, productEntity } from "./entities";
import { EntityPage } from "./pages/EntityPage";
import { OverviewPage } from "./pages/OverviewPage";
import { SearchPage } from "./pages/SearchPage";

const scopes: Scope[] = ["all", "products", "customers", "orders"];

const rootRoute = createRootRoute({
  component: function Root() {
    return (
      <>
        <CommandPalette />
        <Layout />
      </>
    );
  },
});

const searchRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  validateSearch: (search: Record<string, unknown>): { q?: string; scope?: Scope } => ({
    q: typeof search.q === "string" && search.q !== "" ? search.q : undefined,
    scope: scopes.includes(search.scope as Scope) && search.scope !== "all" ? (search.scope as Scope) : undefined,
  }),
  component: SearchPage,
});

const overviewRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/overview",
  component: OverviewPage,
});

const pageSearch = (search: Record<string, unknown>): { page?: number; size?: number } => {
  const page = Number(search.page);
  const size = Number(search.size);
  return {
    page: Number.isInteger(page) && page > 1 ? page : undefined,
    size: [10, 50, 100].includes(size) ? size : undefined,
  };
};

const productsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/products",
  validateSearch: pageSearch,
  component: function Products() {
    const { page = 1, size = 20 } = productsRoute.useSearch();
    return <EntityPage entity={productEntity} page={page} size={size} />;
  },
});

const customersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/customers",
  validateSearch: pageSearch,
  component: function Customers() {
    const { page = 1, size = 20 } = customersRoute.useSearch();
    return <EntityPage entity={customerEntity} page={page} size={size} />;
  },
});

const ordersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/orders",
  validateSearch: pageSearch,
  component: function Orders() {
    const { page = 1, size = 20 } = ordersRoute.useSearch();
    return <EntityPage entity={orderEntity} page={page} size={size} />;
  },
});

export const router = createRouter({
  routeTree: rootRoute.addChildren([searchRoute, overviewRoute, productsRoute, customersRoute, ordersRoute]),
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
