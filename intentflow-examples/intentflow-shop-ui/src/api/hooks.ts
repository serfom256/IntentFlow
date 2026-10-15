import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, query } from "./client";
import type { Page, SearchResult, ServiceName } from "./types";

export type Scope = "all" | ServiceName;

export function useSearch(q: string, scope: Scope) {
  const path = scope === "all" ? "/api/search" : `/api/${scope}/search`;
  return useQuery({
    queryKey: ["search", scope, q],
    queryFn: () => api<SearchResult>(`${path}?${query({ q, debug: true })}`),
    enabled: q.trim().length > 0,
    retry: false,
    staleTime: 30_000,
  });
}

export function usePage<T>(service: ServiceName, page: number, size: number) {
  return useQuery({
    queryKey: [service, "page", page, size],
    queryFn: () => api<Page<T>>(`/api/${service}?${query({ page, size })}`),
    placeholderData: keepPreviousData,
  });
}

export function useEntity<T>(service: ServiceName, id: number, enabled: boolean) {
  return useQuery({
    queryKey: [service, "one", id],
    queryFn: () => api<T>(`/api/${service}/${id}`),
    enabled,
    staleTime: 60_000,
  });
}

export function useTotal(service: ServiceName) {
  return useQuery({
    queryKey: [service, "total"],
    queryFn: () => api<Page<unknown>>(`/api/${service}?${query({ page: 0, size: 1 })}`).then((p) => p.totalItems),
  });
}

export function useSave<R>(service: ServiceName) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id?: number; request: R }) =>
      api(id === undefined ? `/api/${service}` : `/api/${service}/${id}`, {
        method: id === undefined ? "POST" : "PUT",
        body: JSON.stringify(request),
      }),
    onSuccess: () => invalidate(client, service),
  });
}

export function useDelete(service: ServiceName) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/${service}/${id}`, { method: "DELETE" }),
    onSuccess: () => invalidate(client, service),
  });
}

function invalidate(client: ReturnType<typeof useQueryClient>, service: ServiceName) {
  const affected: ServiceName[] = service === "customers" ? ["customers", "orders"] : [service];
  return Promise.all([
    ...affected.map((s) => client.invalidateQueries({ queryKey: [s] })),
    client.invalidateQueries({ queryKey: ["search"] }),
  ]);
}
