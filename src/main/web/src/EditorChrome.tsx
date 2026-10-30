import type { RefObject } from "react";
import {
  ArrowDownToLine,
  ArrowUpFromLine,
  Braces,
  Check,
  ChevronRight,
  CircleAlert,
  FileJson,
  MapPin,
  Plus,
  Route,
  Settings2,
  Undo2,
  X,
} from "lucide-react";
import type { Issue, Suite } from "./model";

export type View = "tests" | "locations" | "defaults" | "json";
export type Notice = { text: string; error: boolean };
const navigation = [
  { id: "tests", label: "Tests", icon: Route },
  { id: "locations", label: "Locations", icon: MapPin },
  { id: "defaults", label: "Suite defaults", icon: Settings2 },
  { id: "json", label: "JSON", icon: Braces },
] as const;
const descriptions: Record<View, string> = {
  tests: "Define requests and the results your deployment should return.",
  locations: "Build the named location index used by your trip requests.",
  defaults: "Set shared trip parameters once, then override them per test.",
  json: "Inspect, paste, and export your complete test suite.",
};

export function EditorHeader({
  fileInput,
  issues,
  jsonDirty,
  onImport,
  onExport,
  onHome,
}: {
  fileInput: RefObject<HTMLInputElement | null>;
  issues: Issue[];
  jsonDirty: boolean;
  onImport: (file?: File) => Promise<void>;
  onExport: () => void;
  onHome: () => void;
}) {
  const exportTitle = issues.length
    ? "Resolve validation issues before exporting"
    : "Apply pending JSON edits before exporting";
  return (
    <header className="app-header">
      <a
        className="brand"
        href="#"
        onClick={(event) => {
          event.preventDefault();
          onHome();
        }}
        aria-label="OTP suite builder home"
      >
        <span className="brand-mark">
          <Route size={25} strokeWidth={1.7} />
        </span>
        <span>
          <strong>OTP</strong>
          <span>Suite builder</span>
        </span>
      </a>
      <div className="header-actions">
        <button
          className="button secondary"
          onClick={() => fileInput.current?.click()}
        >
          <ArrowUpFromLine size={16} />
          <span>Import JSON</span>
        </button>
        <button
          className="button primary"
          onClick={onExport}
          disabled={issues.length > 0 || jsonDirty}
          title={
            issues.length || jsonDirty
              ? exportTitle
              : "Download a new JSON file"
          }
        >
          <ArrowDownToLine size={16} />
          <span>Export JSON</span>
        </button>
      </div>
      <input
        ref={fileInput}
        type="file"
        accept=".json,application/json"
        hidden
        onChange={(event) => {
          void onImport(event.target.files?.[0]);
          event.target.value = "";
        }}
      />
    </header>
  );
}

export function EditorSidebar({
  suite,
  view,
  filename,
  onFilename,
  onView,
  onNew,
}: {
  suite: Suite;
  view: View;
  filename: string;
  onFilename: (value: string) => void;
  onView: (view: View) => void;
  onNew: () => void;
}) {
  const counts: Partial<Record<View, number>> = {
    tests: suite.tests.length,
    locations: Object.keys(suite.locations ?? {}).length,
  };
  return (
    <aside className="sidebar">
      <div className="suite-file">
        <FileJson size={21} />
        <label>
          <span className="visually-hidden">Export filename</span>
          <input
            aria-label="Export filename"
            value={filename}
            onChange={(event) => onFilename(event.target.value)}
          />
        </label>
      </div>
      <nav aria-label="Suite sections">
        {navigation.map((item) => (
          <button
            key={item.id}
            className={`nav-item ${view === item.id ? "active" : ""}`}
            aria-current={view === item.id ? "page" : undefined}
            onClick={() => onView(item.id)}
          >
            <item.icon size={18} />
            <span>{item.label}</span>
            {counts[item.id] !== undefined && (
              <span className="nav-count">{counts[item.id]}</span>
            )}
          </button>
        ))}
      </nav>
      <div className="sidebar-bottom">
        <div className="local-note">
          <span className="local-dot" />
          <span>Files stay in your browser</span>
        </div>
        <p>
          Import, build, and download. Your suite is sent nowhere by this
          editor.
        </p>
        <small>
          Map tiles and optional address searches use external services.
        </small>
        <button className="button secondary" onClick={onNew}>
          <Plus size={16} /> New suite
        </button>
      </div>
    </aside>
  );
}

export function EditorNotice({
  notice,
  onDismiss,
}: {
  notice: Notice | null;
  onDismiss: () => void;
}) {
  if (!notice) return null;
  return (
    <div
      className={`notice ${notice.error ? "error" : ""}`}
      role={notice.error ? "alert" : "status"}
    >
      {notice.error ? <CircleAlert size={18} /> : <Check size={18} />}
      <span>{notice.text}</span>
      <button
        className="icon-button"
        aria-label="Dismiss message"
        onClick={onDismiss}
      >
        <X size={16} />
      </button>
    </div>
  );
}

export function EditorHeading({
  view,
  canUndo,
  onUndo,
}: {
  view: View;
  canUndo: boolean;
  onUndo: () => void;
}) {
  return (
    <div className="page-heading">
      <div>
        <h1>{navigation.find((item) => item.id === view)!.label}</h1>
        <p>{descriptions[view]}</p>
      </div>
      <button
        className="button secondary compact"
        disabled={!canUndo}
        onClick={onUndo}
      >
        <Undo2 size={15} /> Undo
      </button>
    </div>
  );
}

function ValidationDetails({
  issues,
  jsonDirty,
  onSelect,
}: {
  issues: Issue[];
  jsonDirty: boolean;
  onSelect: (issue: Issue) => void;
}) {
  return (
    <div className="validation-details panel">
      {jsonDirty && <p>Apply the pending JSON edits before exporting.</p>}
      {issues.length ? (
        <ul className="issue-list">
          {issues.map((issue, index) => (
            <li key={index}>
              <button onClick={() => onSelect(issue)}>
                <code>{issue.path}</code>
                <span>{issue.message}</span>
                <ChevronRight size={15} />
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p>
          <Check size={16} /> Schema and loader checks passed. Ready to export.
        </p>
      )}
    </div>
  );
}

export function EditorStatus({
  issues,
  dirty,
  jsonDirty,
  expanded,
  onToggle,
  onSelect,
}: {
  issues: Issue[];
  dirty: boolean;
  jsonDirty: boolean;
  expanded: boolean;
  onToggle: () => void;
  onSelect: (issue: Issue) => void;
}) {
  const message = issues.length
    ? `${issues.length} ${issues.length === 1 ? "issue" : "issues"} to resolve`
    : "Suite is valid";
  return (
    <>
      <div className="validation-bar">
        <button
          className={`validation-status ${issues.length ? "has-issues" : ""}`}
          onClick={onToggle}
          aria-expanded={expanded}
        >
          {issues.length ? <CircleAlert size={16} /> : <Check size={16} />}
          <span>{message}</span>
          <ChevronRight className={expanded ? "expanded" : ""} size={15} />
        </button>
        <span>
          {dirty || jsonDirty ? "Unexported changes" : "Browser session"}
        </span>
      </div>
      {expanded && (
        <ValidationDetails
          issues={issues}
          jsonDirty={jsonDirty}
          onSelect={onSelect}
        />
      )}
    </>
  );
}

export function JsonEditor({
  text,
  dirty,
  onChange,
  onDiscard,
  onApply,
}: {
  text: string;
  dirty: boolean;
  onChange: (text: string) => void;
  onDiscard: () => void;
  onApply: () => void;
}) {
  return (
    <section className="json-workspace panel">
      <div className="row spread">
        <span className="type-label">
          <Braces size={17} />
          {dirty ? "Pending JSON edits" : "Complete suite"}
        </span>
        <div className="row">
          <button
            className="button secondary compact"
            disabled={!dirty}
            onClick={onDiscard}
          >
            Discard edits
          </button>
          <button
            className="button primary compact"
            disabled={!dirty}
            onClick={onApply}
          >
            <Check size={15} /> Apply JSON
          </button>
        </div>
      </div>
      <label className="visually-hidden" htmlFor="suite-json">
        Test suite JSON
      </label>
      <textarea
        id="suite-json"
        className="json-source"
        spellCheck={false}
        value={text}
        onChange={(event) => onChange(event.target.value)}
      />
      <p className="json-footnote">
        Apply edits to validate the suite and update the forms. Export downloads
        a new file. This editor never runs tests against a deployment.
      </p>
    </section>
  );
}
