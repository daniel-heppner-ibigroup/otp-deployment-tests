# OTP suite builder

A browser-only React + TypeScript SPA built with Vite. Its forms and AJV 2020
validation use the runner's actual `test-suite-v2.schema.json`, imported directly
from `src/main/resources/test-suites/schema` rather than a copied schema.

Use Node.js 24 and npm:

```sh
cd src/main/web
npm ci
npm run dev
```

Open the printed `/suite-builder/` URL. To check and build:

```sh
npm test
npm run build
```

The bundle goes to `target/generated-resources/web/suite-builder/`. Maven copies
that directory to the classpath, and `OtpMonitoringApplication` serves it at
`http://localhost:8080/suite-builder/`. The app root redirects there. To build
both the UI and the runner from a clean checkout (Node/npm must be installed):

```sh
mvn -Pweb-ui clean package
```

The Dockerfile builds the UI in a Node stage and includes the bundle in the Java
image. Ordinary Java-only Maven builds do not require Node; build the UI first,
or use `-Pweb-ui`, to include it. `mvn clean` removes the generated bundle.

## Editing

- Start from scratch, import a v2 JSON file, paste JSON in the JSON view, or load
  the included Minneapolis example. Import validates structure before replacing
  the working suite. The original file is never modified.
- Add trip-plan and agency-list tests. Request settings and condition fields are
  available through **Add a setting**, including modes, bans, triangle factors,
  fares, transit-leg lists, and recursively composed `allOf` / `anyOf` expressions.
- Omitted values inherit defaults. Explicit false, zero, empty objects, and empty
  arrays retain their distinct meanings. Optional fields have a remove control.
- Store coordinates and stop IDs in **Locations**. Pick a point on the map or
  enter coordinates manually. Enable **Move selected point on map** to reposition
  a saved coordinate. Renaming a key updates every trip reference. A referenced
  location cannot be deleted until the referencing tests are updated.
- Optionally search a Pelias `/v1/search` endpoint. It must permit cross-origin
  browser requests; HTTPS pages require an HTTPS endpoint. Search settings are
  temporary and are never added to the suite JSON.
- Export is enabled when schema and loader semantic checks pass. Pending JSON
  edits must be applied first. The file name is editable in the suite navigation.
  The Undo button swaps with the previous suite state, including after deletion.

Changes live in browser memory. Export before closing the page; the browser warns
when leaving with unexported changes. Files are not uploaded, suites are not saved
to the server, and the editor does not execute deployment tests. OpenStreetMap
supplies map tiles over the network; manual coordinates and stop IDs work without
tiles. Pelias searches are sent only when explicitly submitted.

Large integer JSON numbers are preserved with lossless parsing and serialization,
including the full Java `long` range for durations. Exact duration checks guard
against overflow and fractional values that native JavaScript rounding could
otherwise conceal. Fractional local times remain strings, including nanosecond
precision.
