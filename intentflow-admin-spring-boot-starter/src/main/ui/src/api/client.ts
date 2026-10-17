import type { ProblemDetail } from "./types";

const BASE = "api";
const TOKEN_KEY = "intentflow-admin-token";

let csrf: { header: string; token: string } | null = null;

export class ApiError extends Error {
  readonly problem: ProblemDetail;

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${problem.status}`);
    this.problem = problem;
  }
}

export function setCsrf(value: { header: string; token: string } | null) {
  csrf = value;
}

export function storedToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function storeToken(token: string | null) {
  try {
    if (token) {
      sessionStorage.setItem(TOKEN_KEY, token);
    } else {
      sessionStorage.removeItem(TOKEN_KEY);
    }
  } catch {
    return;
  }
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json", Accept: "application/json" };
  const token = storedToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (csrf && init?.method && init.method !== "GET") {
    headers[csrf.header] = csrf.token;
  }
  const response = await fetch(BASE + path, { ...init, headers: { ...headers, ...init?.headers } });
  if (!response.ok) {
    const problem: ProblemDetail = await response
      .json()
      .catch(() => ({ status: response.status, title: response.statusText }));
    throw new ApiError({ ...problem, status: response.status });
  }
  return (await response.json()) as T;
}
