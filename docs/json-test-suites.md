# JSON test suites

Suites use schema version 2, validated by
`src/main/resources/test-suites/schema/test-suite-v2.schema.json` before execution.
The loader rejects unknown fields and expectation operators. Minneapolis is a
complete example.

```json
{
  "schemaVersion": 2,
  "defaults": {
    "request": { "modes": ["TRANSIT", "WALK"] },
    "transitLegMatching": "EXACT_ORDERED"
  },
  "locations": {
    "origin": { "type": "coordinate", "name": "Origin", "lat": 44.96, "lon": -93.29 },
    "destination": { "type": "stop-id", "id": "feed:123" }
  },
  "tests": [
    {
      "type": "trip-plan",
      "id": "route-2",
      "name": "Route 2 eastbound",
      "request": {
        "from": "origin",
        "to": "destination",
        "serviceDay": "MONDAY",
        "time": "11:21"
      },
      "expectation": {
        "allOf": [
          { "itineraryCountBetween": { "minimum": 1, "maximum": 5 } },
          { "itinerary": { "itineraryMatching": "ALL", "maxDurationSeconds": 3600 } },
          {
            "itinerary": {
              "transitLegs": [ { "mode": "BUS", "routeShortNames": ["2"] } ]
            }
          }
        ]
      }
    }
  ]
}
```

Each test is reported independently. IDs must be unique within the suite.
Location references must exist. A `coordinate` has `lat`, `lon`, and an optional
`name` that falls back to its location key. A `stop-id` has an `id` containing the
client's stop identifier, including its feed prefix where required. Its location
key is just a reusable name; naming a coordinate `stop-123` does not make it a
stop-ID request. The location variants cannot be mixed.

## Test types and expectation expressions

`trip-plan` makes one trip-planning request and evaluates its single
`expectation`. An expression object contains exactly one of these operators:

| Operator | Value | Library construction |
| --- | --- | --- |
| `itinerary` | Itinerary conditions | `new ItineraryExpectation()` with builder settings |
| `itineraryCountBetween` | `minimum` and `maximum` | `PlanExpectation.itineraryCountBetween(...)` |
| `anyOf` | Nonempty list of expressions | `PlanExpectation.anyOf(...)` |
| `allOf` | Nonempty list of expressions | `PlanExpectation.allOf(...)` |

`allOf` and `anyOf` may nest recursively. All alternatives are evaluated, and
assertion failures include diagnostics for each branch in the console logs.
Count bounds are inclusive and refer to the total number of itineraries returned.

An `itinerary` expression's optional `itineraryMatching` maps directly to
`withItineraryMatching(ItineraryMatching)`. All conditions inside that expression
must hold together on the same itinerary.

| `itineraryMatching` | Requirement | Empty response |
| --- | --- | --- |
| `ANY` (default) | At least one itinerary satisfies all conditions | Fails |
| `ALL` | Every itinerary satisfies all conditions | Fails |
| `NONE` | No itinerary satisfies all conditions | Passes |

`allOf` requires every child expectation to pass; different `ANY` children may
match different itineraries. It does not mean every itinerary must match. Use
`itineraryMatching: "ALL"` for that requirement.

`agency-list` is a separate workflow with `id`, `name`, and an `agencies` array.
It retrieves routes and requires the distinct agency names to equal that array.
It does not require locations, a trip request, or an expectation.

## Transit-leg matching

| `transitLegMatching` | Order | Additional transit legs |
| --- | --- | --- |
| `EXACT_ORDERED` | Declaration order | Prohibited |
| `ORDERED_SUBSEQUENCE` | Declaration order | Allowed before, between, and after |
| `UNORDERED_SUBSET` | Any order | Allowed |

All modes match distinct actual transit legs; walking and other non-transit
legs do not participate. Unordered matching finds a valid assignment even when
leg expectations overlap.

An itinerary can override `transitLegMatching`; otherwise an explicit
`transitLegs` list uses the suite default, falling back to `EXACT_ORDERED`.
Omitting both the list and the matching mode leaves transit legs unconstrained.
An empty list with `EXACT_ORDERED` requires zero transit legs. With `ANY`, this
accepts journeys without transit; it does not constrain walking, biking, or car
legs. `{ "itinerary": { "itineraryMatching": "NONE" } }` requires an empty
response, as does a count range with both bounds zero.

Each transit-leg expectation requires at least one condition. Supported fields:
`mode`, `routeShortNames`, `routeLongNames`, `boardingStopCodes`,
`boardingStopGtfsIds`, `alightingStopCodes`, `alightingStopGtfsIds`,
`interlinedWithPrevious`, `maxDurationSeconds`, and `fares`. Name/code/ID arrays
accept any listed value; different fields are AND. A fare condition has `amount`,
`riderCategoryId`, and `mediumId`; every listed fare condition must match.

Omitted interlining is unconstrained; explicit `true` requires it and explicit
`false` prohibits it. This describes continuation from the actual previous
transit leg, which may be an extra leg in subset modes.

`maxDurationSeconds` is an inclusive whole-second limit. Inside a leg it limits
that leg. Inside an itinerary it limits total elapsed journey time, including
walking and waiting.

## Requests and defaults

Every trip request requires `from`, `to`, `time`, and exactly one of:

- `serviceDay`: next occurrence of the weekday in the deployment's timezone;
  today is always excluded, even if the requested time is still in the future.
- `date`: explicit ISO local date, useful for a specific service calendar.

The Java suite supplies the deployment timezone and endpoint. These settings can
be set on each request or in `defaults.request`:

| Fields | Representation |
| --- | --- |
| `modes` | Nonempty set of client request modes |
| `numItineraries` | Positive Java `int` |
| `wheelchair` | Boolean |
| `searchDirection` | `DEPART_AT` or `ARRIVE_BY` |
| `searchWindowSeconds` | Nonnegative whole seconds |
| `walkReluctance`, `carReluctance`, `bikeReluctance`, `bikeWalkingReluctance` | Nonnegative finite numbers |
| `walkSpeed` | Positive finite number, in metres per second |
| `optimize` | `FLAT`, `GREENWAYS`, `QUICK`, `SAFE`, or `TRIANGLE` |
| `triangle` | Object with `safetyFactor`, `slopeFactor`, and `timeFactor`, each in 0..1 |
| `banned` | Object with optional `routes`, `agencies`, `trips`, `stops`, and `stopsHard` ID arrays |
| `pageCursor` | Nonblank cursor from an earlier response |

Triangle factors are sent as supplied; OTP requires their sum to be 1. The loader
does not make additional assumptions about OTP's routing policies or deployment
capabilities. A cursor can be supplied literally; the format does not execute a
multi-request pagination workflow.

Precedence is explicit request value, suite default, then runner/client default.
Arrays and nested objects replace rather than merge. Explicit `false` and zero
(where allowed) override defaults. `banned: {}` clears inherited bans. Runner
default modes are `TRANSIT` + `WALK`; client defaults include `DEPART_AT`, five
itineraries, `wheelchair: false`, and `optimize: "QUICK"`.

## Validation contract

Validation completes during suite construction, before sending requests. There
are three stages; passing the schema alone does not guarantee a loadable suite.

| Stage | Responsibilities |
| --- | --- |
| JSON Schema | Structure, required and unknown fields, exactly one expression operator, nonempty compositions, enums, coordinate ranges, date/time shape, exactly one of date/serviceDay, and numeric representation limits |
| Typed deserialization | Strict `LocalDate`/`LocalTime` parsing, including impossible calendar dates such as `2026-02-30` |
| Loader semantics and expectation compilation | Unique test IDs, existing named locations, and `itineraryCountBetween.minimum <= maximum`, including inside nested alternatives |

Count bounds are 0..2,147,483,647; requested itinerary counts are 1..2,147,483,647.
Durations are 0..9,223,372,036,854,775,807 seconds (Java `long`). Fare amounts are
bounded by the largest finite Java `float`; other floating request settings are
bounded by the largest finite Java `double`.

The date schema deliberately checks the `YYYY-MM-DD` shape. A direct schema
validator can accept an impossible calendar date or a reversed count range;
the loader rejects both. Cross-document references and uniqueness by test ID
also require the loader. Errors identify the resource; expectation compilation
errors also identify the test.

Shared `$defs` own request options, mode enums, matching enums, numeric bounds,
string sets, and test identity fields. `requestOptions` and `testIdentity` are
open building blocks; their enclosing definitions use `unevaluatedProperties:
false` so composition still rejects unknown fields.

## Extending and migrating the format

A new expression adds a validated operator under `$defs/expectation` and a
corresponding adapter that constructs a library `PlanExpectation`. New itinerary
and leg conditions add properties to their existing definitions and adapters.
A new workflow adds a branch under `$defs/test` and a test executor. Matching
algorithms stay in the assertion library. Existing documents need no changes
when optional fields or expression types are added; older runners reject
unsupported fields and operators at load time.

Version 1 documents and earlier version 2 drafts using `assertions` must be
migrated to this expression structure:

- Replace the `assertions` list with `expectation: { "allOf": [...] }`; a single
  expression can be used directly.
- Replace `itinerary-exists` / `match` with `{ "itinerary": { ... } }`.
- Replace `itinerary-absent` with an itinerary using `itineraryMatching: "NONE"`.
- Replace `itinerary-count` with `itineraryCountBetween`, retaining both bounds.
- Replace `all-of` / `any-of` with `allOf` / `anyOf` expression lists.
- Add `type: "coordinate"` to existing locations; stop-ID locations use
  `type: "stop-id"` and `id` instead of coordinates.

For version 1, also rename leg `routes` to `routeShortNames`, move request
defaults under `defaults.request`, and replace strictness with the matching enum.
Explicit `interlinedWithPrevious: false` asserts a negative condition. A future
breaking change to this format requires a new schema version.
