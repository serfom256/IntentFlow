import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, setCsrf } from "./client";
import type { ExplainView, OverrideRequest, ServiceView, SessionView } from "./types";

const keys = {
  services: ["services"] as const,
  service: (name: string) => ["services", name] as const,
};

export function useSession() {
  return useQuery({
    queryKey: ["session"],
    queryFn: async () => {
      const session = await api<SessionView>("/session");
      setCsrf(session.csrf);
      return session;
    },
    retry: false,
  });
}

export function useServices() {
  return useQuery({ queryKey: keys.services, queryFn: () => api<ServiceView[]>("/services") });
}

export function useService(name: string) {
  return useQuery({
    queryKey: keys.service(name),
    queryFn: () => api<ServiceView>(`/services/${encodeURIComponent(name)}`),
  });
}

function useServiceMutation<V>(name: string, request: (variables: V) => Promise<ServiceView>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (service) => {
      client.setQueryData(keys.service(name), service);
      client.invalidateQueries({ queryKey: keys.services, exact: true });
    },
  });
}

export function useSaveOverride(name: string) {
  return useServiceMutation(name, (request: OverrideRequest) =>
    api<ServiceView>(`/services/${encodeURIComponent(name)}`, { method: "PUT", body: JSON.stringify(request) }),
  );
}

export function useResetOverride(name: string) {
  return useServiceMutation(name, () =>
    api<ServiceView>(`/services/${encodeURIComponent(name)}`, { method: "DELETE" }),
  );
}

export function useExplain() {
  return useMutation({
    mutationFn: (request: { question: string; service?: string }) =>
      api<ExplainView>("/explain", { method: "POST", body: JSON.stringify(request) }),
  });
}
