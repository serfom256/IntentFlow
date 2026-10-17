import { createHashHistory, createRootRoute, createRoute, createRouter } from "@tanstack/react-router";
import { Layout } from "./components/Layout";
import { OverviewPage } from "./pages/OverviewPage";
import { ServicePage } from "./pages/ServicePage";

const rootRoute = createRootRoute({ component: Layout });

const overviewRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  component: OverviewPage,
});

export const serviceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/services/$name",
  component: function Service() {
    const { name } = serviceRoute.useParams();
    return <ServicePage key={name} name={name} />;
  },
});

export const router = createRouter({
  routeTree: rootRoute.addChildren([overviewRoute, serviceRoute]),
  history: createHashHistory(),
  defaultPreload: "intent",
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
