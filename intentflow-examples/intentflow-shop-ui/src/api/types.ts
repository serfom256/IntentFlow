export const categories = ["LAPTOP", "PHONE", "TABLET", "HEADPHONES", "MONITOR", "ACCESSORY"] as const;
export const orderStatuses = ["NEW", "PAID", "SHIPPED", "DELIVERED", "CANCELLED"] as const;

export type Category = (typeof categories)[number];
export type OrderStatus = (typeof orderStatuses)[number];

export interface Product {
  id: number;
  name: string;
  brand: string;
  category: Category;
  price: number;
  rating: number;
  stock: number;
  active: boolean;
  supplierCost: number;
  createdAt: string;
}

export interface Customer {
  id: number;
  fullName: string;
  email: string;
  city: string;
  country: string;
  vip: boolean;
  createdAt: string;
}

export interface Order {
  id: number;
  customerId: number;
  status: OrderStatus;
  total: number;
  itemsCount: number;
  createdAt: string;
}

export interface ProductRequest {
  name: string;
  brand: string;
  category: Category;
  price: number;
  rating: number;
  stock: number;
  active: boolean;
  supplierCost: number;
}

export interface CustomerRequest {
  name: string;
  email: string;
  city: string;
  country: string;
  vip: boolean;
}

export interface OrderRequest {
  customerId: number;
  status: OrderStatus;
  total: number;
  itemsCount: number;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface AggregateRow {
  group: unknown[];
  value: unknown;
}

export interface Aggregation {
  function: string;
  field: string | null;
  groupBy: string[];
  rows: AggregateRow[];
}

export interface Trace {
  dsl: string;
  cached: boolean;
  sql: string;
  parameters: unknown[];
}

export type ServiceName = "products" | "customers" | "orders";

export interface SearchResult {
  query: string;
  service: ServiceName;
  uri: string;
  items: Array<Product | Customer | Order>;
  aggregation?: Aggregation;
  trace?: Trace;
  tookMs: number;
}

export interface ProblemDetail {
  status: number;
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
  kind?: "unsupported";
}
