import { lazy, Suspense, useEffect, useMemo, useRef, useState } from "react";
import example from "../../resources/test-suites/metro-transit-minneapolis.json";
import {
  EditorHeader,
  EditorHeading,
  EditorNotice,
  EditorSidebar,
  EditorStatus,
  JsonEditor,
} from "./EditorChrome";
import type { Notice, View } from "./EditorChrome";

const LocationsEditor = lazy(() =>
  import("./LocationsEditor").then((module) => ({
    default: module.LocationsEditor,
  })),
);
import { RequestOptions } from "./SchemaFields";
import { TestsEditor } from "./TestsEditor";
import {
  blankSuite,
  definition,
  parseSuite,
  serialize,
  validateSuite,
} from "./model";
import type { Issue, Suite } from "./model";

function DefaultsEditor({
  suite,
  onChange,
}: {
  suite: Suite;
  onChange: (suite: Suite) => void;
}) {
  const defaults = suite.defaults ?? {};
  return (
    <article className="defaults-workspace panel">
      <section className="form-section">
        <h3>Trip plan parameters</h3>
        <p className="section-description">
          These settings apply to every trip test unless the request supplies
          its own value. Lists and nested objects replace inherited values.
        </p>
        <RequestOptions
          value={defaults.request ?? {}}
          onChange={(request) =>
            onChange({ ...suite, defaults: { ...defaults, request } })
          }
        />
      </section>
      <section className="form-section">
        <h3>Transit-leg matching</h3>
        <p className="section-description">
          Controls how declared transit legs match an actual itinerary.
          Individual itinerary expectations can override this setting.
        </p>
        <label className="field">
          <span className="field-label">Default matching strategy</span>
          <select
            value={defaults.transitLegMatching ?? ""}
            onChange={(event) => {
              const next = { ...defaults };
              if (event.target.value)
                next.transitLegMatching = event.target.value;
              else delete next.transitLegMatching;
              onChange({ ...suite, defaults: next });
            }}
          >
            <option value="">Runner default (EXACT ORDERED)</option>
            {definition("transitLegMatching").enum!.map((mode) => (
              <option key={mode} value={mode}>
                {mode.replaceAll("_", " ")}
              </option>
            ))}
          </select>
        </label>
        <dl className="strategy-list">
          <div>
            <dt>Exact ordered</dt>
            <dd>Same legs, same order. No additional transit legs.</dd>
          </div>
          <div>
            <dt>Ordered subsequence</dt>
            <dd>Same order, with additional transit legs allowed.</dd>
          </div>
          <div>
            <dt>Unordered subset</dt>
            <dd>All declared legs, in any order. Additional legs allowed.</dd>
          </div>
        </dl>
      </section>
    </article>
  );
}

export default function App() {
  const [suite, setSuite] = useState<Suite>(blankSuite);
  const [previous, setPrevious] = useState<Suite | null>(null);
  const [view, setView] = useState<View>("tests");
  const [selected, setSelected] = useState(0);
  const [filename, setFilename] = useState("untitled-suite.json");
  const [dirty, setDirty] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [showIssues, setShowIssues] = useState(false);
  const [jsonText, setJsonText] = useState(() => serialize(suite));
  const [jsonDirty, setJsonDirty] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const issues = useMemo(() => validateSuite(suite), [suite]);

  useEffect(() => {
    function warn(event: BeforeUnloadEvent) {
      if (dirty || jsonDirty) event.preventDefault();
    }
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty, jsonDirty]);

  function change(next: Suite) {
    setPrevious(suite);
    setSuite(next);
    setDirty(true);
    setNotice(null);
    // Retain pending source edits until the user explicitly applies or discards them.
    if (!jsonDirty) setJsonText(serialize(next));
  }
  function canReplace() {
    return (
      !(dirty || jsonDirty) ||
      window.confirm(
        "Replace the current work? Export it first if you want to keep it.",
      )
    );
  }
  function load(next: Suite, name: string) {
    if (!canReplace()) return;
    setPrevious(suite);
    setSuite(next);
    setFilename(name);
    setSelected(0);
    setJsonText(serialize(next));
    setJsonDirty(false);
    setDirty(false);
    setView("tests");
    setNotice({
      text: `Opened ${name}. The original file stays unchanged.`,
      error: false,
    });
  }
  async function importFile(file?: File) {
    if (!file) return;
    try {
      load(parseSuite(await file.text()), file.name);
    } catch (cause) {
      setNotice({ text: (cause as Error).message, error: true });
    }
  }
  function applyJson() {
    try {
      const next = parseSuite(jsonText);
      setPrevious(suite);
      setSuite(next);
      setSelected(0);
      setJsonText(serialize(next));
      setJsonDirty(false);
      setDirty(true);
      setNotice({ text: "JSON applied to the editor.", error: false });
    } catch (cause) {
      setNotice({ text: (cause as Error).message, error: true });
    }
  }
  function download() {
    if (issues.length || jsonDirty) {
      setShowIssues(true);
      return;
    }
    const blob = new Blob([serialize(suite)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename.toLowerCase().endsWith(".json")
      ? filename
      : `${filename || "test-suite"}.json`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    setDirty(false);
    setNotice({ text: `Exported ${link.download}.`, error: false });
  }
  function selectIssue(issue: Issue) {
    const match = issue.path.match(/^\/tests\/(\d+)/);
    if (match) {
      setSelected(Number(match[1]));
      setView("tests");
    } else if (issue.path.startsWith("/locations")) setView("locations");
    else if (issue.path.startsWith("/defaults")) setView("defaults");
    else setView("tests");
  }
  function navigate(next: View) {
    if (next !== "json" && jsonDirty) {
      if (
        !window.confirm(
          "Apply or discard the pending JSON edits before changing form fields. Discard these JSON edits?",
        )
      )
        return;
      setJsonText(serialize(suite));
      setJsonDirty(false);
    }
    setView(next);
  }

  function startNew() {
    if (!canReplace()) return;
    const next = blankSuite();
    setPrevious(suite);
    setSuite(next);
    setFilename("untitled-suite.json");
    setDirty(false);
    setJsonText(serialize(next));
    setJsonDirty(false);
    setSelected(0);
    setView("tests");
    setNotice(null);
  }
  function undo() {
    if (!previous) return;
    const next = previous;
    setPrevious(suite);
    setSuite(next);
    setSelected((index) => Math.min(index, Math.max(next.tests.length - 1, 0)));
    setDirty(true);
    setJsonText(serialize(next));
    setJsonDirty(false);
  }

  return (
    <div className="app-shell">
      <EditorHeader
        fileInput={fileInput}
        issues={issues}
        jsonDirty={jsonDirty}
        onImport={importFile}
        onExport={download}
        onHome={() => navigate("tests")}
      />
      <EditorSidebar
        suite={suite}
        view={view}
        filename={filename}
        onFilename={setFilename}
        onView={navigate}
        onNew={startNew}
      />
      <main>
        <EditorNotice notice={notice} onDismiss={() => setNotice(null)} />
        <EditorHeading view={view} canUndo={previous !== null} onUndo={undo} />
        <EditorStatus
          issues={issues}
          dirty={dirty}
          jsonDirty={jsonDirty}
          expanded={showIssues}
          onToggle={() => setShowIssues(!showIssues)}
          onSelect={selectIssue}
        />
        <Suspense
          fallback={
            <div className="panel directory-empty" role="status">
              Loading location editor…
            </div>
          }
        >
          {view === "tests" && (
            <TestsEditor
              suite={suite}
              selected={selected}
              onSelect={setSelected}
              onChange={change}
              onLocations={() => navigate("locations")}
              onExample={() =>
                load(
                  parseSuite(JSON.stringify(example)),
                  "metro-transit-minneapolis.json",
                )
              }
              onImport={() => fileInput.current?.click()}
            />
          )}
          {view === "locations" && (
            <LocationsEditor suite={suite} onChange={change} />
          )}
          {view === "defaults" && (
            <DefaultsEditor suite={suite} onChange={change} />
          )}
          {view === "json" && (
            <JsonEditor
              text={jsonText}
              dirty={jsonDirty}
              onChange={(text) => {
                setJsonText(text);
                setJsonDirty(true);
              }}
              onDiscard={() => {
                setJsonText(serialize(suite));
                setJsonDirty(false);
              }}
              onApply={applyJson}
            />
          )}
        </Suspense>
      </main>
    </div>
  );
}
