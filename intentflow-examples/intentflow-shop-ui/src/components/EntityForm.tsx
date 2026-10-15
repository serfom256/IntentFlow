import { Button, Group, Modal, NumberInput, Select, SimpleGrid, Stack, Switch, TextInput } from "@mantine/core";
import { useForm } from "@mantine/form";
import { useEffect } from "react";
import { ApiError } from "../api/client";
import type { FormField, FormValues } from "../entities";
import { ErrorAlert } from "./ErrorAlert";

interface Props {
  title: string;
  opened: boolean;
  fields: FormField[];
  initial: FormValues;
  saving: boolean;
  error: unknown;
  onClose: () => void;
  onSubmit: (values: FormValues) => void;
}

function rule(field: FormField) {
  return (value: unknown): string | null => {
    switch (field.kind) {
      case "text":
      case "email": {
        const text = String(value ?? "").trim();
        if (!text) return `${field.label} is required`;
        if (field.maxLength && text.length > field.maxLength) return `At most ${field.maxLength} characters`;
        if (field.kind === "email" && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text)) return "Enter a valid email";
        if (field.pattern && !field.pattern.test(text)) return field.patternMessage ?? "Invalid format";
        return null;
      }
      case "number": {
        const number = Number(value);
        if (value === "" || Number.isNaN(number)) return `${field.label} is required`;
        if (field.min !== undefined && number < field.min) return `Must be at least ${field.min}`;
        if (field.max !== undefined && number > field.max) return `Must be at most ${field.max}`;
        return null;
      }
      default:
        return null;
    }
  };
}

export function EntityForm({ title, opened, fields, initial, saving, error, onClose, onSubmit }: Props) {
  const form = useForm<FormValues>({
    mode: "controlled",
    initialValues: initial,
    validate: Object.fromEntries(fields.map((f) => [f.name, rule(f)])),
  });

  useEffect(() => {
    if (opened) {
      form.setValues(initial);
      form.resetDirty(initial);
      form.clearErrors();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [opened, initial]);

  useEffect(() => {
    if (error instanceof ApiError && error.problem.errors) {
      form.setErrors(error.problem.errors);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [error]);

  const generalError = error && !(error instanceof ApiError && error.problem.errors) ? error : null;

  return (
    <Modal opened={opened} onClose={onClose} title={title} size="lg" radius="md" centered>
      <form onSubmit={form.onSubmit(onSubmit)}>
        <Stack gap="md">
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            {fields.map((field) => {
              switch (field.kind) {
                case "text":
                case "email":
                  return (
                    <TextInput
                      key={field.name}
                      label={field.label}
                      type={field.kind}
                      withAsterisk
                      {...form.getInputProps(field.name)}
                    />
                  );
                case "number":
                  return (
                    <NumberInput
                      key={field.name}
                      label={field.label}
                      min={field.min}
                      max={field.max}
                      step={field.step}
                      decimalScale={field.decimals}
                      thousandSeparator=","
                      withAsterisk
                      {...form.getInputProps(field.name)}
                    />
                  );
                case "select":
                  return (
                    <Select
                      key={field.name}
                      label={field.label}
                      data={[...field.options]}
                      allowDeselect={false}
                      withAsterisk
                      {...form.getInputProps(field.name)}
                    />
                  );
                case "switch":
                  return (
                    <Switch
                      key={field.name}
                      label={field.label}
                      mt="xl"
                      {...form.getInputProps(field.name, { type: "checkbox" })}
                    />
                  );
              }
            })}
          </SimpleGrid>
          {generalError !== null && <ErrorAlert error={generalError} />}
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={saving}>
              Save
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
