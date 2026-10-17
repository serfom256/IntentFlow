export interface FieldView {
  name: string;
  column: string;
  type: string;
  allowedValues: string[];
  declaredDescription: string | null;
  overrideDescription: string | null;
}

export interface RelationView {
  name: string;
  target: string;
}

export interface ServiceView {
  name: string;
  table: string;
  entity: string;
  declaredDescription: string | null;
  overrideDescription: string | null;
  declaredUri: string;
  overrideUri: string | null;
  fields: FieldView[];
  relations: RelationView[];
}

export interface OverrideRequest {
  description: string | null;
  uri: string | null;
  fields: Record<string, string>;
}

export interface ExplainView {
  dsl: string;
  cached: boolean;
  sql: string;
  parameters: unknown[];
}

export interface ProblemDetail {
  status: number;
  title?: string;
  detail?: string;
  kind?: string;
  errors?: string[];
  login?: string | null;
  authorities?: string[];
}

export interface SessionView {
  user: string | null;
  csrf: { header: string; token: string } | null;
}

export function overrideCount(service: ServiceView): number {
  return (
    (service.overrideDescription ? 1 : 0) +
    (service.overrideUri ? 1 : 0) +
    service.fields.filter((f) => f.overrideDescription).length
  );
}
