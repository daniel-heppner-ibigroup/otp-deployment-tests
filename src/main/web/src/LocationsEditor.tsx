import { useState } from "react";
import { MapPin, Plus, Search, Trash2 } from "lucide-react";
import { LocationMap } from "./LocationMap";
import { locationUses, nextKey, renameLocation } from "./model";
import type { Coordinate, Location, Suite } from "./model";

type Props = { suite: Suite; onChange: (suite: Suite) => void };

function PeliasSearch({ onAdd }: { onAdd: (coordinate: Coordinate) => void }) {
  const [endpoint, setEndpoint] = useState("");
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [results, setResults] = useState<Coordinate[]>([]);
  async function search(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setResults([]);
    try {
      const url = new URL(endpoint);
      if (!["http:", "https:"].includes(url.protocol))
        throw new Error("Use an HTTP or HTTPS Pelias endpoint.");
      url.searchParams.set("text", query);
      const response = await fetch(url, { signal: AbortSignal.timeout(15000) });
      if (!response.ok)
        throw new Error(`Geocoder returned HTTP ${response.status}.`);
      const data = await response.json();
      const coordinates: Coordinate[] = (data.features ?? []).flatMap(
        (feature: {
          geometry?: { coordinates?: number[] };
          properties?: { label?: string };
        }) => {
          const point = feature.geometry?.coordinates;
          if (
            !point ||
            !Number.isFinite(point[0]) ||
            !Number.isFinite(point[1]) ||
            Math.abs(point[1]) > 90 ||
            Math.abs(point[0]) > 180
          )
            return [];
          return [
            {
              type: "coordinate",
              lat: point[1],
              lon: point[0],
              name: feature.properties?.label ?? query,
            },
          ];
        },
      );
      setResults(coordinates);
      if (!coordinates.length)
        setError(
          "No locations found. Try another address or choose a point on the map.",
        );
    } catch (cause) {
      setError(
        `${cause instanceof Error ? cause.message : "Search failed."} Check the endpoint and its browser CORS settings.`,
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <details className="geocoder-search">
      <summary>
        <Search size={15} /> Find an address with Pelias
      </summary>
      <form onSubmit={search}>
        <label className="field">
          <span className="field-label">Pelias search endpoint</span>
          <input
            type="url"
            required
            value={endpoint}
            onChange={(event) => setEndpoint(event.target.value)}
            placeholder="https://your-geocoder/v1/search"
          />
        </label>
        <label className="field">
          <span className="field-label">Address or place</span>
          <input
            required
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Street address, station, or landmark"
          />
        </label>
        <button className="button secondary compact" disabled={busy}>
          <Search size={15} />
          {busy ? "Searching…" : "Search addresses"}
        </button>
      </form>
      <small>
        The endpoint must allow browser requests (CORS). Geocoder settings are
        not part of the exported suite.
      </small>
      {error && (
        <p className="inline-error" role="alert">
          {error}
        </p>
      )}
      <ul className="search-results">
        {results.map((coordinate, index) => (
          <li key={index}>
            <button
              className="result-button"
              onClick={() => {
                onAdd(coordinate);
                setResults([]);
              }}
            >
              <MapPin size={16} />
              <span>
                {coordinate.name}
                <small>
                  {coordinate.lat}, {coordinate.lon}
                </small>
              </span>
              <Plus size={16} />
            </button>
          </li>
        ))}
      </ul>
    </details>
  );
}

function LocationDetails({
  locationKey,
  location,
  suite,
  onChange,
  onRename,
  onDelete,
}: {
  locationKey: string;
  location: Location;
  suite: Suite;
  onChange: (location: Location) => void;
  onRename: (key: string) => void;
  onDelete: () => void;
}) {
  const [keyDraft, setKeyDraft] = useState(locationKey);
  const [error, setError] = useState("");
  const uses = locationUses(suite, locationKey);
  return (
    <div className="location-details">
      <div className="row spread">
        <h3>Location details</h3>
        <button
          className="button danger compact"
          disabled={uses > 0}
          title={
            uses
              ? "Update the tests using this location before deleting it."
              : undefined
          }
          onClick={onDelete}
        >
          <Trash2 size={14} />
          Delete
        </button>
      </div>
      <div className="field-grid">
        <div className="field">
          <label className="field-label" htmlFor="location-key">
            Reference key
          </label>
          <div className="row">
            <input
              id="location-key"
              value={keyDraft}
              onChange={(event) => setKeyDraft(event.target.value)}
            />
            {keyDraft !== locationKey && (
              <button
                className="button secondary compact"
                onClick={() => {
                  try {
                    onRename(keyDraft);
                    setError("");
                  } catch (cause) {
                    setError((cause as Error).message);
                  }
                }}
              >
                Rename
              </button>
            )}
          </div>
          <small>
            Used by {uses} {uses === 1 ? "test" : "tests"}. Renaming updates all
            references.
          </small>
          {error && (
            <span className="inline-error" role="alert">
              {error}
            </span>
          )}
        </div>
        <label className="field">
          <span className="field-label">Location type</span>
          <select
            value={location.type}
            onChange={(event) =>
              onChange(
                event.target.value === "coordinate"
                  ? { type: "coordinate", lat: 0, lon: 0 }
                  : { type: "stop-id", id: "" },
              )
            }
          >
            <option value="coordinate">Coordinate</option>
            <option value="stop-id">Stop ID</option>
          </select>
        </label>
        {location.type === "coordinate" ? (
          <>
            <label className="field wide">
              <span className="field-label">Display name (optional)</span>
              <input
                value={location.name ?? ""}
                onChange={(event) => {
                  const next = { ...location };
                  if (event.target.value) next.name = event.target.value;
                  else delete next.name;
                  onChange(next);
                }}
              />
            </label>
            <label className="field">
              <span className="field-label">Latitude</span>
              <input
                type="number"
                min={-90}
                max={90}
                step="any"
                value={Number.isFinite(location.lat) ? location.lat : ""}
                onChange={(event) =>
                  onChange({ ...location, lat: event.target.valueAsNumber })
                }
              />
            </label>
            <label className="field">
              <span className="field-label">Longitude</span>
              <input
                type="number"
                min={-180}
                max={180}
                step="any"
                value={Number.isFinite(location.lon) ? location.lon : ""}
                onChange={(event) =>
                  onChange({ ...location, lon: event.target.valueAsNumber })
                }
              />
            </label>
          </>
        ) : (
          <label className="field wide">
            <span className="field-label">Stop ID, including feed prefix</span>
            <input
              value={location.id}
              onChange={(event) =>
                onChange({ ...location, id: event.target.value })
              }
              placeholder="feed:stop-id"
            />
            <small>
              Stop IDs are sent directly to OTP and have no map coordinates.
            </small>
          </label>
        )}
      </div>
    </div>
  );
}

export function LocationsEditor({ suite, onChange }: Props) {
  const locations = suite.locations ?? {};
  const [selected, setSelected] = useState(Object.keys(locations)[0] ?? "");
  const [filter, setFilter] = useState("");
  const [picking, setPicking] = useState(false);
  const current = locations[selected];
  function add(coordinate?: Coordinate) {
    const key = nextKey(coordinate ? "place" : "stop", Object.keys(locations));
    const value: Location = coordinate ?? { type: "stop-id", id: "" };
    onChange({ ...suite, locations: { ...locations, [key]: value } });
    setSelected(key);
    setPicking(false);
    setFilter("");
  }
  function update(location: Location) {
    onChange({ ...suite, locations: { ...locations, [selected]: location } });
  }
  return (
    <div className="locations-layout">
      <div className="directory panel">
        <label className="search-input">
          <Search size={16} />
          <input
            aria-label="Filter locations"
            value={filter}
            placeholder="Find a location…"
            onChange={(event) => setFilter(event.target.value)}
          />
        </label>
        <div className="directory-list">
          {Object.entries(locations)
            .filter(([key, location]) =>
              `${key} ${location.type === "coordinate" ? location.name : location.id}`
                .toLowerCase()
                .includes(filter.toLowerCase()),
            )
            .map(([key, location]) => (
              <button
                className={`directory-item ${selected === key ? "selected" : ""}`}
                key={key}
                onClick={() => {
                  setSelected(key);
                  setPicking(false);
                }}
              >
                <MapPin size={17} />
                <span>
                  <strong>
                    {location.type === "coordinate"
                      ? location.name || key
                      : location.id || key}
                  </strong>
                  <small>{key}</small>
                  <span className="location-kind">
                    {location.type === "coordinate"
                      ? `${location.lat.toFixed(5)}, ${location.lon.toFixed(5)}`
                      : "Stop ID"}
                  </span>
                </span>
              </button>
            ))}
          {!Object.keys(locations).length && (
            <p className="directory-empty">
              Save coordinates or stop IDs here, then reuse them in your trip
              requests.
            </p>
          )}
        </div>
        <div className="directory-actions">
          <button
            className="button secondary compact"
            onClick={() => {
              setSelected("");
              setPicking(true);
            }}
          >
            <Plus size={15} /> Pick on map
          </button>
          <button className="button secondary compact" onClick={() => add()}>
            <Plus size={15} /> Stop ID
          </button>
          <button
            className="text-button"
            onClick={() => add({ type: "coordinate", lat: 0, lon: 0 })}
          >
            Enter coordinates manually
          </button>
        </div>
      </div>
      <div className="location-workspace">
        <div className="row spread map-toolbar">
          <span>
            {picking
              ? "Click anywhere on the map to save a point."
              : "Coordinates & reusable stop IDs"}
          </span>
          {current?.type === "coordinate" && (
            <label className="choice">
              <input
                type="checkbox"
                checked={picking}
                onChange={(event) => setPicking(event.target.checked)}
              />{" "}
              Move selected point on map
            </label>
          )}
        </div>
        <LocationMap
          locations={locations}
          selected={selected}
          picking={picking}
          onSelect={(key) => {
            setSelected(key);
            setPicking(false);
          }}
          onPick={(coordinate) => {
            if (current?.type === "coordinate") {
              update({
                ...coordinate,
                ...(current.name !== undefined ? { name: current.name } : {}),
              });
              setPicking(false);
            } else add(coordinate);
          }}
        />
        {current ? (
          <LocationDetails
            key={selected}
            locationKey={selected}
            location={current}
            suite={suite}
            onChange={update}
            onDelete={() => {
              const next = { ...locations };
              delete next[selected];
              onChange({ ...suite, locations: next });
              setSelected(Object.keys(next)[0] ?? "");
            }}
            onRename={(key) => {
              const next = renameLocation(suite, selected, key);
              onChange(next);
              setSelected(key);
            }}
          />
        ) : (
          <div className="location-details empty-location">
            <MapPin size={22} />
            <p>
              {picking
                ? "Choose a point on the map, or enter coordinates manually."
                : "Select a saved location or add a new point."}
            </p>
          </div>
        )}
        <PeliasSearch onAdd={add} />
      </div>
    </div>
  );
}
