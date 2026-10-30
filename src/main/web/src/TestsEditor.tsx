import { useState } from "react";
import {
  ArrowRight,
  Building2,
  Copy,
  Plus,
  Route,
  Search,
  Trash2,
} from "lucide-react";
import { ExpectationEditor } from "./ExpectationEditor";
import { RequestOptions } from "./SchemaFields";
import { cloneValue, definition, formatJson, newTest, nextKey } from "./model";
import type { Obj, Suite, SuiteTest, TripTest } from "./model";

function TripRequest({
  test,
  suite,
  onChange,
  onLocations,
}: {
  test: TripTest;
  suite: Suite;
  onChange: (test: TripTest) => void;
  onLocations: () => void;
}) {
  const request = test.request;
  const locations = Object.entries(suite.locations ?? {});
  const update = (key: string, value: string) =>
    onChange({ ...test, request: { ...request, [key]: value } });
  const basicKeys = ["from", "to", "date", "serviceDay", "time"];
  const options = Object.fromEntries(
    Object.entries(request).filter(([key]) => !basicKeys.includes(key)),
  );
  const basic = Object.fromEntries(
    Object.entries(request).filter(([key]) => basicKeys.includes(key)),
  );
  return (
    <section className="form-section">
      <div className="row spread">
        <h3>Trip request</h3>
        <button className="text-button" onClick={onLocations}>
          Manage locations <ArrowRight size={14} />
        </button>
      </div>
      {!locations.length && (
        <p className="help-banner">
          Add coordinates or stop IDs in Locations, then choose your origin and
          destination.
        </p>
      )}
      <div className="field-grid">
        {["from", "to"].map((end) => (
          <label className="field" key={end}>
            <span className="field-label">
              {end === "from" ? "Origin" : "Destination"}
            </span>
            <select
              value={String(request[end])}
              onChange={(event) => update(end, event.target.value)}
            >
              <option value="">Choose a location</option>
              {request[end] &&
                !Object.hasOwn(suite.locations ?? {}, String(request[end])) && (
                  <option value={String(request[end])}>
                    Missing: {String(request[end])}
                  </option>
                )}
              {locations.map(([key, location]) => (
                <option key={key} value={key}>
                  {location.type === "coordinate"
                    ? location.name || key
                    : `${key} · ${location.id}`}
                </option>
              ))}
            </select>
          </label>
        ))}
        <label className="field">
          <span className="field-label">Schedule by</span>
          <select
            value={Object.hasOwn(request, "date") ? "date" : "serviceDay"}
            onChange={(event) => {
              const next = { ...request };
              delete next.date;
              delete next.serviceDay;
              next[event.target.value] =
                event.target.value === "date"
                  ? new Date().toLocaleDateString("en-CA")
                  : "MONDAY";
              onChange({ ...test, request: next });
            }}
          >
            <option value="serviceDay">Next service weekday</option>
            <option value="date">Specific calendar date</option>
          </select>
        </label>
        {Object.hasOwn(request, "date") ? (
          <label className="field">
            <span className="field-label">Date</span>
            <input
              type="date"
              value={String(request.date)}
              onChange={(event) => update("date", event.target.value)}
            />
          </label>
        ) : (
          <label className="field">
            <span className="field-label">Service day</span>
            <select
              value={String(request.serviceDay)}
              onChange={(event) => update("serviceDay", event.target.value)}
            >
              {definition("serviceDay").enum!.map((day) => (
                <option key={day}>{day}</option>
              ))}
            </select>
          </label>
        )}
        <label className="field">
          <span className="field-label">Local time</span>
          <input
            value={String(request.time)}
            placeholder="08:00 or 08:00:00.000"
            onChange={(event) => update("time", event.target.value)}
          />
          <small>
            Deployment timezone. Seconds and up to 9 fractional digits are
            supported.
          </small>
        </label>
        {!Object.hasOwn(request, "date") && (
          <p className="field-note">
            The runner chooses the next occurrence of this weekday, excluding
            today.
          </p>
        )}
      </div>
      <div className="subsection-heading">
        <h4>Request overrides</h4>
        <p>Omitted settings inherit suite defaults, then runner defaults.</p>
      </div>
      <RequestOptions
        value={options}
        onChange={(next) =>
          onChange({ ...test, request: { ...basic, ...next } })
        }
      />
      <details className="effective-request">
        <summary>Inspect request with suite defaults</summary>
        <pre>{formatJson({ ...suite.defaults?.request, ...request })}</pre>
        <small>Runner defaults apply to any remaining omitted settings.</small>
      </details>
    </section>
  );
}

export function TestsEditor({
  suite,
  selected,
  onSelect,
  onChange,
  onLocations,
  onExample,
  onImport,
}: {
  suite: Suite;
  selected: number;
  onSelect: (index: number) => void;
  onChange: (suite: Suite) => void;
  onLocations: () => void;
  onExample: () => void;
  onImport: () => void;
}) {
  const [query, setQuery] = useState("");
  const current = suite.tests[selected];
  function add(type: SuiteTest["type"]) {
    onChange({ ...suite, tests: [...suite.tests, newTest(type, suite)] });
    onSelect(suite.tests.length);
    setQuery("");
  }
  function update(test: SuiteTest) {
    onChange({
      ...suite,
      tests: suite.tests.map((old, index) => (index === selected ? test : old)),
    });
  }
  if (!suite.tests.length)
    return (
      <div className="empty-state panel">
        <div className="empty-symbol">
          <Route size={38} strokeWidth={1.4} />
        </div>
        <h2>
          Every reliable journey
          <br />
          starts with a test.
        </h2>
        <p>
          Build a trip request, describe the journey you expect, and save a
          suite ready for your deployment runner.
        </p>
        <div className="row center wrap">
          <button className="button primary" onClick={() => add("trip-plan")}>
            <Plus size={17} /> Add trip test
          </button>
          <button
            className="button secondary"
            onClick={() => add("agency-list")}
          >
            <Building2 size={17} /> Add agency test
          </button>
        </div>
        <div className="empty-import">
          <span>Have a suite already?</span>
          <button className="text-button" onClick={onImport}>
            Import JSON
          </button>
          <span>or</span>
          <button className="text-button" onClick={onExample}>
            explore the Minneapolis example
          </button>
        </div>
      </div>
    );
  return (
    <div className="tests-layout">
      <div className="directory panel">
        <label className="search-input">
          <Search size={16} />
          <input
            aria-label="Filter tests"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Find a test…"
          />
        </label>
        <div className="directory-list">
          {suite.tests
            .map((test, index) => ({ test, index }))
            .filter(({ test }) =>
              `${test.name} ${test.id}`
                .toLowerCase()
                .includes(query.toLowerCase()),
            )
            .map(({ test, index }) => (
              <button
                className={`directory-item ${selected === index ? "selected" : ""}`}
                key={index}
                onClick={() => onSelect(index)}
              >
                {test.type === "trip-plan" ? (
                  <Route size={17} />
                ) : (
                  <Building2 size={17} />
                )}
                <span>
                  <strong>{test.name || "Untitled test"}</strong>
                  <small>{test.id || "Missing test ID"}</small>
                  <span className="location-kind">
                    {test.type === "trip-plan" ? "Trip plan" : "Agency list"}
                  </span>
                </span>
              </button>
            ))}
        </div>
        <div className="directory-actions">
          <button
            className="button secondary compact"
            onClick={() => add("trip-plan")}
          >
            <Plus size={15} /> Trip test
          </button>
          <button
            className="button secondary compact"
            onClick={() => add("agency-list")}
          >
            <Plus size={15} /> Agency test
          </button>
        </div>
      </div>
      {current ? (
        <article className="test-workspace panel">
          <div className="test-toolbar row spread">
            <span className="type-label">
              {current.type === "trip-plan" ? (
                <Route size={16} />
              ) : (
                <Building2 size={16} />
              )}
              {current.type === "trip-plan"
                ? "Trip plan test"
                : "Agency list test"}
            </span>
            <div className="row">
              <button
                className="button secondary compact"
                onClick={() => {
                  const copy = cloneValue(current);
                  copy.id = nextKey(
                    `${copy.id}-copy`,
                    suite.tests.map((test) => test.id),
                  );
                  copy.name += " (copy)";
                  onChange({ ...suite, tests: [...suite.tests, copy] });
                  onSelect(suite.tests.length);
                  setQuery("");
                }}
              >
                <Copy size={14} /> Duplicate
              </button>
              <button
                className="icon-button danger"
                aria-label="Delete selected test"
                title="Delete test (can be undone)"
                onClick={() => {
                  onChange({
                    ...suite,
                    tests: suite.tests.filter((_, index) => index !== selected),
                  });
                  onSelect(Math.max(0, selected - 1));
                }}
              >
                <Trash2 size={16} />
              </button>
            </div>
          </div>
          <section className="form-section identity-section">
            <div className="field-grid">
              <label className="field wide">
                <span className="field-label">Test name</span>
                <input
                  className="name-input"
                  value={current.name}
                  onChange={(event) =>
                    update({ ...current, name: event.target.value })
                  }
                />
              </label>
              <label className="field wide">
                <span className="field-label">Unique test ID</span>
                <input
                  className="mono"
                  value={current.id}
                  onChange={(event) =>
                    update({ ...current, id: event.target.value })
                  }
                />
              </label>
            </div>
          </section>
          {current.type === "trip-plan" ? (
            <>
              <TripRequest
                test={current}
                suite={suite}
                onChange={update}
                onLocations={onLocations}
              />
              <section className="form-section">
                <h3>Expected result</h3>
                <ExpectationEditor
                  value={current.expectation}
                  onChange={(expectation) =>
                    update({ ...current, expectation })
                  }
                />
              </section>
            </>
          ) : (
            <section className="form-section">
              <h3>Expected agencies</h3>
              <p className="section-description">
                The distinct agency names returned by OTP must exactly equal
                this list. An empty list expects no agencies.
              </p>
              <label className="field">
                <span className="field-label">Agency names</span>
                <textarea
                  rows={7}
                  value={current.agencies.join("\n")}
                  onChange={(event) =>
                    update({
                      ...current,
                      agencies: event.target.value
                        ? event.target.value.split("\n")
                        : [],
                    })
                  }
                  onBlur={(event) =>
                    update({
                      ...current,
                      agencies: event.target.value
                        .split("\n")
                        .filter((line) => line.trim()),
                    })
                  }
                />
                <small>One agency name per line.</small>
              </label>
            </section>
          )}
        </article>
      ) : (
        <div className="panel directory-empty">
          Select a test to edit its request and expectations.
        </div>
      )}
    </div>
  );
}
