import { Badge, Group, Rating, Text } from "@mantine/core";
import type { ReactNode } from "react";
import { CustomerLink } from "./components/CustomerLink";
import {
  categories,
  orderStatuses,
  type Customer,
  type CustomerRequest,
  type Order,
  type OrderRequest,
  type OrderStatus,
  type Product,
  type ProductRequest,
  type ServiceName,
} from "./api/types";

export interface Column<T> {
  key: string;
  label: string;
  render: (row: T) => ReactNode;
  align?: "left" | "right";
}

export type FormField =
  | {
      name: string;
      label: string;
      kind: "text" | "email";
      maxLength?: number;
      pattern?: RegExp;
      patternMessage?: string;
    }
  | {
      name: string;
      label: string;
      kind: "number";
      min?: number;
      max?: number;
      step?: number;
      decimals?: number;
      optional?: boolean;
    }
  | { name: string; label: string; kind: "select"; options: readonly string[] }
  | { name: string; label: string; kind: "switch" };

export type FormValues = Record<string, string | number | boolean>;

export interface EntityDefinition<T extends { id: number }, R> {
  service: ServiceName;
  title: string;
  singular: string;
  columns: Column<T>[];
  form: FormField[];
  empty: FormValues;
  toValues: (entity: T) => FormValues;
  toRequest: (values: FormValues) => R;
  examples: string[];
}

const money = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });
const date = new Intl.DateTimeFormat("en-GB", { dateStyle: "medium" });

export const formatMoney = (value: number) => money.format(value);
export const formatDate = (value: string) => date.format(new Date(value));

const statusColor: Record<OrderStatus, string> = {
  NEW: "gray",
  PAID: "blue",
  SHIPPED: "violet",
  DELIVERED: "teal",
  CANCELLED: "red",
};

export const productEntity: EntityDefinition<Product, ProductRequest> = {
  service: "products",
  title: "Products",
  singular: "product",
  columns: [
    { key: "id", label: "ID", render: (p) => p.id },
    {
      key: "name",
      label: "Name",
      render: (p) => (
        <div>
          <Text size="sm" fw={500}>{p.name}</Text>
          <Text size="xs" c="dimmed">{p.brand}</Text>
        </div>
      ),
    },
    { key: "category", label: "Category", render: (p) => <Badge variant="light">{p.category}</Badge> },
    {
      key: "price",
      label: "Price",
      align: "right",
      render: (p) =>
        p.discount === null ? (
          formatMoney(p.price)
        ) : (
          <div>
            <Text size="sm">{formatMoney(p.price * (1 - p.discount / 100))}</Text>
            <Group gap={4} justify="flex-end" wrap="nowrap">
              <Text size="xs" c="dimmed" td="line-through">{formatMoney(p.price)}</Text>
              <Badge size="xs" color="orange" variant="light">-{p.discount}%</Badge>
            </Group>
          </div>
        ),
    },
    {
      key: "rating",
      label: "Rating",
      render: (p) => (
        <Group gap={6} wrap="nowrap">
          <Rating value={p.rating} fractions={10} readOnly size="xs" />
          <Text size="xs" c="dimmed">{p.rating.toFixed(1)}</Text>
        </Group>
      ),
    },
    {
      key: "stock",
      label: "Stock",
      align: "right",
      render: (p) => (p.stock === 0 ? <Badge color="red" variant="light">out</Badge> : p.stock),
    },
    {
      key: "active",
      label: "Status",
      render: (p) => (
        <Badge color={p.active ? "teal" : "gray"} variant="dot">{p.active ? "active" : "discontinued"}</Badge>
      ),
    },
  ],
  form: [
    { name: "name", label: "Name", kind: "text", maxLength: 120 },
    { name: "brand", label: "Brand", kind: "text", maxLength: 60 },
    { name: "category", label: "Category", kind: "select", options: categories },
    { name: "price", label: "Price, USD", kind: "number", min: 0, step: 1, decimals: 2 },
    { name: "discount", label: "Discount, %", kind: "number", min: 0, max: 90, step: 5, decimals: 1, optional: true },
    { name: "rating", label: "Rating", kind: "number", min: 0, max: 5, step: 0.1, decimals: 1 },
    { name: "stock", label: "Stock", kind: "number", min: 0, step: 1, decimals: 0 },
    { name: "supplierCost", label: "Supplier cost, USD", kind: "number", min: 0, step: 1, decimals: 2 },
    { name: "active", label: "Active", kind: "switch" },
  ],
  empty: { name: "", brand: "", category: "LAPTOP", price: 0, discount: "", rating: 0, stock: 0, supplierCost: 0, active: true },
  toValues: (p) => ({
    name: p.name,
    brand: p.brand,
    category: p.category,
    price: p.price,
    discount: p.discount ?? "",
    rating: p.rating,
    stock: p.stock,
    supplierCost: p.supplierCost,
    active: p.active,
  }),
  toRequest: (v) => ({
    name: String(v.name),
    brand: String(v.brand),
    category: v.category as ProductRequest["category"],
    price: Number(v.price),
    discount: v.discount === "" ? null : Number(v.discount),
    rating: Number(v.rating),
    stock: Number(v.stock),
    supplierCost: Number(v.supplierCost),
    active: Boolean(v.active),
  }),
  examples: [
    "apple laptops under 2000",
    "the 3 most expensive monitors",
    "headphones rated 4.5 or higher",
    "average price per category",
    "phones under 500 after discount",
    "the best rated product in each category",
  ],
};

export const customerEntity: EntityDefinition<Customer, CustomerRequest> = {
  service: "customers",
  title: "Customers",
  singular: "customer",
  columns: [
    { key: "id", label: "ID", render: (c) => c.id },
    {
      key: "fullName",
      label: "Name",
      render: (c) => (
        <div>
          <Text size="sm" fw={500}>{c.fullName}</Text>
          <Text size="xs" c="dimmed">{c.email}</Text>
        </div>
      ),
    },
    { key: "city", label: "City", render: (c) => `${c.city}, ${c.country}` },
    { key: "vip", label: "Tier", render: (c) => (c.vip ? <Badge color="yellow">VIP</Badge> : null) },
    { key: "createdAt", label: "Joined", render: (c) => formatDate(c.createdAt) },
  ],
  form: [
    { name: "name", label: "Full name", kind: "text", maxLength: 120 },
    { name: "email", label: "Email", kind: "email", maxLength: 160 },
    { name: "city", label: "City", kind: "text", maxLength: 80 },
    {
      name: "country",
      label: "Country code",
      kind: "text",
      maxLength: 2,
      pattern: /^[A-Za-z]{2}$/,
      patternMessage: "Two-letter code, e.g. DE",
    },
    { name: "vip", label: "VIP", kind: "switch" },
  ],
  empty: { name: "", email: "", city: "", country: "", vip: false },
  toValues: (c) => ({ name: c.fullName, email: c.email, city: c.city, country: c.country, vip: c.vip }),
  toRequest: (v) => ({
    name: String(v.name),
    email: String(v.email),
    city: String(v.city),
    country: String(v.country),
    vip: Boolean(v.vip),
  }),
  examples: [
    "vip customers who joined this year",
    "customers who have a cancelled order",
    "how many customers are vip",
    "customers with an order over 5000, biggest order first",
    "the newest customer in each country",
  ],
};

export const orderEntity: EntityDefinition<Order, OrderRequest> = {
  service: "orders",
  title: "Orders",
  singular: "order",
  columns: [
    { key: "id", label: "ID", render: (o) => o.id },
    { key: "customerId", label: "Customer", render: (o) => <CustomerLink id={o.customerId} /> },
    {
      key: "status",
      label: "Status",
      render: (o) => <Badge color={statusColor[o.status]} variant="light">{o.status}</Badge>,
    },
    { key: "itemsCount", label: "Items", align: "right", render: (o) => o.itemsCount },
    { key: "total", label: "Total", align: "right", render: (o) => formatMoney(o.total) },
    { key: "createdAt", label: "Placed", render: (o) => formatDate(o.createdAt) },
  ],
  form: [
    { name: "customerId", label: "Customer ID", kind: "number", min: 1, step: 1, decimals: 0 },
    { name: "status", label: "Status", kind: "select", options: orderStatuses },
    { name: "total", label: "Total, USD", kind: "number", min: 0, step: 1, decimals: 2 },
    { name: "itemsCount", label: "Items", kind: "number", min: 1, step: 1, decimals: 0 },
  ],
  empty: { customerId: 1, status: "NEW", total: 0, itemsCount: 1 },
  toValues: (o) => ({ customerId: o.customerId, status: o.status, total: o.total, itemsCount: o.itemsCount }),
  toRequest: (v) => ({
    customerId: Number(v.customerId),
    status: v.status as OrderStatus,
    total: Number(v.total),
    itemsCount: Number(v.itemsCount),
  }),
  examples: [
    "delivered orders from vip customers",
    "how many orders are there per status?",
    "orders placed in the last 3 months",
    "the latest order of every vip customer",
    "revenue per month in 2026",
    "paid orders sorted by customer name",
  ],
};

export const entities = {
  products: productEntity,
  customers: customerEntity,
  orders: orderEntity,
} as const;
