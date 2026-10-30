import { Plus, Trash2 } from "lucide-react";
import { definition } from "./model";
import type { Obj } from "./model";
import { SchemaFields } from "./SchemaFields";

const expressions: Record<
  string,
  { label: string; description: string; initial: () => Obj }
> = {
  itinerary: {
    label: "Match itinerary",
    description: "All conditions here must hold on the same itinerary.",
    initial: () => ({ itinerary: {} }),
  },
  itineraryCountBetween: {
    label: "Itinerary count",
    description:
      "An inclusive range for the total number of returned itineraries.",
    initial: () => ({ itineraryCountBetween: { minimum: 1, maximum: 5 } }),
  },
  allOf: {
    label: "All of these",
    description:
      "Every child expectation must pass; children may match different itineraries.",
    initial: () => ({ allOf: [{ itinerary: {} }] }),
  },
  anyOf: {
    label: "Any of these",
    description: "At least one child expectation must pass.",
    initial: () => ({ anyOf: [{ itinerary: {} }] }),
  },
};

export function ExpectationEditor({
  value,
  onChange,
  depth = 0,
}: {
  value: Obj;
  onChange: (value: Obj) => void;
  depth?: number;
}) {
  const kind = Object.keys(value)[0];
  const group = kind === "allOf" || kind === "anyOf";
  const children = group ? (value[kind] as Obj[]) : [];
  return (
    <div className="expectation-editor">
      <label className="field">
        <span className="field-label">Expectation type</span>
        <select
          value={kind}
          onChange={(event) => {
            const next = event.target.value;
            if (group && (next === "allOf" || next === "anyOf"))
              onChange({ [next]: children });
            else if (next === "allOf" || next === "anyOf")
              onChange({ [next]: [value] });
            else onChange(expressions[next].initial());
          }}
        >
          {Object.entries(expressions).map(([key, expression]) => (
            <option value={key} key={key}>
              {expression.label}
            </option>
          ))}
        </select>
        <small>{expressions[kind]?.description}</small>
      </label>
      {group ? (
        <div className="expression-children">
          {children.map((child, index) => (
            <div className="expression-child" key={index}>
              <div className="row spread">
                <span className="branch-label">
                  {kind === "allOf" ? "Condition" : "Alternative"} {index + 1}
                </span>
                <button
                  className="icon-button"
                  aria-label={`Remove expectation ${index + 1}`}
                  onClick={() =>
                    onChange({
                      [kind]: children.filter(
                        (_, itemIndex) => itemIndex !== index,
                      ),
                    })
                  }
                >
                  <Trash2 size={15} />
                </button>
              </div>
              <ExpectationEditor
                value={child}
                depth={depth + 1}
                onChange={(next) =>
                  onChange({
                    [kind]: children.map((old, itemIndex) =>
                      index === itemIndex ? next : old,
                    ),
                  })
                }
              />
            </div>
          ))}
          <button
            className="button secondary compact"
            onClick={() =>
              onChange({ [kind]: [...children, { itinerary: {} }] })
            }
          >
            <Plus size={15} /> Add expectation
          </button>
        </div>
      ) : (
        <SchemaFields
          schema={definition(kind)}
          value={value[kind] as Obj}
          onChange={(next) => onChange({ [kind]: next })}
        />
      )}
    </div>
  );
}
