import { Plus, Trash2, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import {
  definition,
  numericInput,
  replaceProperty,
  resolveSchema,
} from "./model";
import type { Json, Obj, Schema } from "./model";

const labels: Record<string, string> = {
  numItineraries: "Number of itineraries",
  wheelchair: "Wheelchair accessible",
  searchWindowSeconds: "Search window (seconds)",
  walkSpeed: "Walk speed (m/s)",
  maxDurationSeconds: "Maximum duration (seconds)",
  stopsHard: "Hard-banned stops",
  boardingStopGtfsIds: "Boarding stop GTFS IDs",
  alightingStopGtfsIds: "Alighting stop GTFS IDs",
  interlinedWithPrevious: "Interlined with previous leg",
  pageCursor: "Page cursor",
  transitLegs: "Transit legs",
  itineraryMatching: "Itinerary matching",
  transitLegMatching: "Transit-leg matching",
};
const hints: Record<string, string> = {
  modes: "An explicit list replaces inherited modes.",
  triangle: "OTP expects the three factors to add up to 1.",
  banned:
    "An empty object clears inherited bans. Lists replace rather than merge.",
  transitLegMatching:
    "Exact: no extra transit legs. Subsequence: ordered, with extras. Subset: any order, with extras.",
  transitLegs:
    "Walking and other non-transit legs do not participate. An empty exact list requires no transit legs.",
  itineraryMatching:
    "ANY: at least one. ALL: every itinerary, with a nonempty response. NONE: no matching itinerary.",
  interlinedWithPrevious:
    "False explicitly prohibits continuation from the preceding transit leg.",
};

export function labelFor(key: string): string {
  return (
    labels[key] ??
    key
      .replace(/([a-z])([A-Z])/g, "$1 $2")
      .replace(/^./, (char) => char.toUpperCase())
  );
}

export function initialValue(rawSchema: Schema): Json {
  const schema = resolveSchema(rawSchema);
  if (schema.const !== undefined) return schema.const;
  if (schema.enum) return schema.enum[0];
  const defaults: Record<string, () => Json> = {
    object: () =>
      Object.fromEntries(
        (schema.required ?? []).map((key) => [
          key,
          initialValue(schema.properties![key]),
        ]),
      ),
    array: () => [],
    boolean: () => false,
    integer: () => schema.minimum ?? 0,
    number: () => schema.minimum ?? 0,
    string: () => "",
  };
  return defaults[schema.type ?? "string"]();
}

type FieldProps = {
  schema: Schema;
  value: Json | undefined;
  onChange: (value: Json) => void;
  label: string;
};

function EnumField({ schema, value, onChange, label }: FieldProps) {
  return (
    <select
      aria-label={label}
      value={String(value ?? "")}
      onChange={(event) => onChange(event.target.value)}
    >
      <option value="" disabled>
        Choose a value
      </option>
      {schema.enum!.map((option) => (
        <option key={option} value={option}>
          {option.replaceAll("_", " ")}
        </option>
      ))}
    </select>
  );
}

function BooleanField({ value, onChange, label }: FieldProps) {
  return (
    <select
      aria-label={label}
      value={String(value)}
      onChange={(event) => onChange(event.target.value === "true")}
    >
      <option value="false">False</option>
      <option value="true">True</option>
    </select>
  );
}

function NumberField({ schema, value, onChange, label }: FieldProps) {
  const [text, setText] = useState(
    value === null || value === undefined ? "" : String(value),
  );
  const lastWritten = useRef(value);
  useEffect(() => {
    if (value === lastWritten.current) return;
    setText(value === null || value === undefined ? "" : String(value));
    lastWritten.current = value;
  }, [value]);
  return (
    <input
      aria-label={label}
      inputMode={schema.type === "integer" ? "numeric" : "decimal"}
      value={text}
      onChange={(event) => {
        setText(event.target.value);
        const next = numericInput(event.target.value);
        lastWritten.current = next;
        onChange(next);
      }}
    />
  );
}

function StringField({ value, onChange, label }: FieldProps) {
  return (
    <input
      aria-label={label}
      value={String(value ?? "")}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}

function ObjectField({ schema, value, onChange }: FieldProps) {
  return (
    <SchemaFields schema={schema} value={value as Obj} onChange={onChange} />
  );
}

function ArrayField({ schema, value, onChange, label }: FieldProps) {
  const items = (value ?? []) as Json[];
  const itemSchema = resolveSchema(schema.items!);
  if (itemSchema.enum)
    return (
      <div className="choices" role="group" aria-label={label}>
        {itemSchema.enum.map((mode) => (
          <label className="choice" key={mode}>
            <input
              type="checkbox"
              checked={items.includes(mode)}
              onChange={(event) =>
                onChange(
                  event.target.checked
                    ? [...items, mode]
                    : items.filter((item) => item !== mode),
                )
              }
            />
            <span>{mode.replaceAll("_", " ")}</span>
          </label>
        ))}
      </div>
    );
  if (itemSchema.type !== "object")
    return (
      <>
        <textarea
          aria-label={label}
          rows={Math.min(Math.max(items.length, 2), 6)}
          value={items.join("\n")}
          onChange={(event) =>
            onChange(event.target.value ? event.target.value.split("\n") : [])
          }
          onBlur={(event) =>
            onChange(
              event.target.value.split("\n").filter((line) => line.trim()),
            )
          }
        />
        <small>
          One value per line. Values within this list are alternatives.
        </small>
      </>
    );
  return (
    <div className="array-editor">
      {items.map((item, index) => (
        <div className="array-item" key={index}>
          <div className="row spread">
            <strong>
              {label === "Transit legs" ? "Transit leg" : "Item"} {index + 1}
            </strong>
            <button
              type="button"
              className="icon-button"
              aria-label={`Remove ${label} ${index + 1}`}
              onClick={() =>
                onChange(items.filter((_, itemIndex) => index !== itemIndex))
              }
            >
              <Trash2 size={15} />
            </button>
          </div>
          <SchemaFields
            schema={itemSchema}
            value={item as Obj}
            onChange={(next) =>
              onChange(
                items.map((old, itemIndex) =>
                  index === itemIndex ? next : old,
                ),
              )
            }
          />
          {index > 0 && (
            <button
              type="button"
              className="text-button"
              onClick={() => {
                const reordered = [...items];
                [reordered[index - 1], reordered[index]] = [
                  reordered[index],
                  reordered[index - 1],
                ];
                onChange(reordered);
              }}
            >
              Move up
            </button>
          )}
        </div>
      ))}
      <button
        type="button"
        className="button secondary compact"
        onClick={() => onChange([...items, initialValue(itemSchema)])}
      >
        <Plus size={15} /> Add{" "}
        {label === "Transit legs" ? "transit leg" : "item"}
      </button>
    </div>
  );
}

function ValueField(props: FieldProps) {
  if (props.schema.enum) return <EnumField {...props} />;
  const editors: Record<string, typeof StringField> = {
    object: ObjectField,
    array: ArrayField,
    boolean: BooleanField,
    integer: NumberField,
    number: NumberField,
    string: StringField,
  };
  const Editor = editors[props.schema.type ?? "string"];
  return <Editor {...props} />;
}

export function SchemaFields({
  schema: rawSchema,
  value,
  onChange,
}: {
  schema: Schema;
  value: Obj;
  onChange: (value: Obj) => void;
}) {
  const schema = resolveSchema(rawSchema);
  const properties = Object.entries(schema.properties ?? {});
  const required = schema.required ?? [];
  const visible = properties.filter(
    ([key]) => required.includes(key) || Object.hasOwn(value, key),
  );
  const available = properties.filter(
    ([key]) => !required.includes(key) && !Object.hasOwn(value, key),
  );
  return (
    <div className="schema-fields">
      {visible.map(([key, property]) => {
        const resolved = resolveSchema(property);
        return (
          <div
            className={`field ${["object", "array"].includes(resolved.type ?? "") ? "wide" : ""}`}
            key={key}
          >
            <div className="field-label">
              <span>{labelFor(key)}</span>
              {!required.includes(key) && (
                <button
                  type="button"
                  className="icon-button subtle"
                  aria-label={`Remove ${labelFor(key)}`}
                  title="Remove this override"
                  onClick={() =>
                    onChange(replaceProperty(value, key, undefined))
                  }
                >
                  <X size={14} />
                </button>
              )}
            </div>
            <ValueField
              schema={resolved}
              value={value[key]}
              label={labelFor(key)}
              onChange={(next) => onChange(replaceProperty(value, key, next))}
            />
            {(hints[key] ?? resolved.description) && (
              <small>{hints[key] ?? resolved.description}</small>
            )}
          </div>
        );
      })}
      {available.length > 0 && (
        <label className="add-option">
          <Plus size={15} />
          <span>Add a setting</span>
          <select
            aria-label="Add a setting"
            value=""
            onChange={(event) => {
              const key = event.target.value;
              if (key)
                onChange(
                  replaceProperty(
                    value,
                    key,
                    initialValue(schema.properties![key]),
                  ),
                );
            }}
          >
            <option value="">Choose setting…</option>
            {available.map(([key]) => (
              <option key={key} value={key}>
                {labelFor(key)}
              </option>
            ))}
          </select>
        </label>
      )}
    </div>
  );
}

export function RequestOptions({
  value,
  onChange,
}: {
  value: Obj;
  onChange: (value: Obj) => void;
}) {
  return (
    <SchemaFields
      schema={definition("requestOptions")}
      value={value}
      onChange={onChange}
    />
  );
}
