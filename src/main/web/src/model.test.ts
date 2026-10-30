import { describe, expect, it } from "vitest";
import example from "../../resources/test-suites/metro-transit-minneapolis.json";
import {
  blankSuite,
  cloneValue,
  newTest,
  parseSuite,
  renameLocation,
  serialize,
  validateSuite,
  numericInput,
} from "./model";
import type { Suite, TripTest } from "./model";

function suiteWithTrip(): Suite {
  const suite = blankSuite();
  suite.locations = {
    origin: { type: "coordinate", lat: 44.96, lon: -93.29 },
    destination: { type: "stop-id", id: "feed:123" },
  };
  suite.tests = [newTest("trip-plan", suite)];
  return suite;
}

describe("suite document boundary", () => {
  it("round-trips the production Minneapolis suite without dropping or rewriting fields", () => {
    const imported = parseSuite(JSON.stringify(example));
    expect(validateSuite(imported)).toEqual([]);
    expect(JSON.parse(serialize(imported))).toEqual(example);
  });

  it("preserves explicit overrides, nested expectations, fares, and fractional local times", () => {
    const suite = suiteWithTrip();
    const trip = suite.tests[0] as TripTest;
    trip.request = {
      ...trip.request,
      wheelchair: false,
      searchWindowSeconds: 0,
      banned: {},
      time: "08:00:00.123456789",
    };
    trip.expectation = {
      allOf: [
        { itineraryCountBetween: { minimum: 0, maximum: 5 } },
        {
          anyOf: [
            { itinerary: { transitLegs: [] } },
            {
              itinerary: {
                itineraryMatching: "NONE",
                transitLegMatching: "UNORDERED_SUBSET",
                transitLegs: [
                  {
                    mode: "BUS",
                    interlinedWithPrevious: false,
                    routeShortNames: ["2", "3"],
                    fares: [
                      { amount: 0, riderCategoryId: "adult", mediumId: "cash" },
                    ],
                  },
                ],
              },
            },
          ],
        },
      ],
    };
    const imported = parseSuite(serialize(suite));
    expect(validateSuite(imported)).toEqual([]);
    expect(imported).toEqual(suite);
  });

  it("rejects unknown fields before replacing the current suite", () => {
    expect(() =>
      parseSuite(JSON.stringify({ ...suiteWithTrip(), unsupported: true })),
    ).toThrow("schema v2");
  });

  it("preserves the full Java long range through import, duplication, and export", () => {
    const json = serialize(suiteWithTrip()).replace(
      '"time": "08:00"',
      '"time": "08:00", "searchWindowSeconds": 9223372036854775807',
    );
    const suite = parseSuite(json);
    expect(validateSuite(suite)).toEqual([]);
    expect(serialize(suite)).toContain(
      '"searchWindowSeconds": 9223372036854775807',
    );
    expect(serialize(cloneValue(suite))).toContain(
      '"searchWindowSeconds": 9223372036854775807',
    );
    expect(() =>
      parseSuite(json.replace("9223372036854775807", "9223372036854775808")),
    ).toThrow("Java long maximum");
    expect(() =>
      parseSuite(json.replace("9223372036854775807", "9007199254740991.5")),
    ).toThrow("whole seconds");
  });

  it("allows incomplete drafts while blocking export until required data is supplied", () => {
    expect(validateSuite(blankSuite()).length).toBeGreaterThan(0);
    const suite = blankSuite();
    suite.tests = [newTest("trip-plan", suite)];
    expect(
      validateSuite(suite).some((issue) => issue.message.includes("missing")),
    ).toBe(true);
    expect(validateSuite(suiteWithTrip())).toEqual([]);
  });

  it("reports invalid numeric drafts without crashing the editor", () => {
    const suite = suiteWithTrip();
    const trip = suite.tests[0] as TripTest;
    trip.expectation = {
      itineraryCountBetween: { minimum: numericInput("1e999"), maximum: 5 },
    };
    expect(validateSuite(suite).length).toBeGreaterThan(0);
    suite.locations!.origin = {
      type: "coordinate",
      lat: Number.NaN,
      lon: -93.29,
    };
    expect(validateSuite(suite).length).toBeGreaterThan(0);
  });

  it("validates agency-only suites without locations or a trip request", () => {
    const suite: Suite = {
      schemaVersion: 2,
      tests: [
        {
          type: "agency-list",
          id: "agencies",
          name: "No agencies",
          agencies: [],
        },
      ],
    };
    expect(validateSuite(parseSuite(serialize(suite)))).toEqual([]);
  });
});

describe("loader semantics in the editor", () => {
  it("reports duplicate IDs, missing locations, impossible dates, and deeply nested reversed bounds", () => {
    const suite = suiteWithTrip();
    const trip = suite.tests[0] as TripTest;
    delete trip.request.serviceDay;
    trip.request.date = "2026-02-30";
    trip.request.to = "missing";
    trip.expectation = {
      allOf: [
        { anyOf: [{ itineraryCountBetween: { minimum: 3, maximum: 1 } }] },
      ],
    };
    suite.tests.push({
      type: "agency-list",
      id: trip.id,
      name: "Duplicate",
      agencies: [],
    });
    expect(validateSuite(suite)).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ path: "/tests/0/request/to" }),
        expect.objectContaining({ path: "/tests/0/request/date" }),
        expect.objectContaining({
          path: "/tests/0/expectation/allOf/0/anyOf/0/itineraryCountBetween",
        }),
        expect.objectContaining({ path: "/tests/1/id" }),
      ]),
    );
  });

  it("accepts leap dates and rejects rollover dates, including years below 100", () => {
    const suite = suiteWithTrip();
    const trip = suite.tests[0] as TripTest;
    delete trip.request.serviceDay;
    for (const date of ["2024-02-29", "0004-02-29", "0099-12-31"]) {
      trip.request.date = date;
      expect(validateSuite(suite)).toEqual([]);
    }
    trip.request.date = "2025-02-29";
    expect(validateSuite(suite)).toContainEqual(
      expect.objectContaining({ path: "/tests/0/request/date" }),
    );
  });

  it("renames both trip endpoints atomically and prevents collisions without modifying the original suite", () => {
    const suite = suiteWithTrip();
    const trip = suite.tests[0] as TripTest;
    trip.request.to = "origin";
    const renamed = renameLocation(suite, "origin", "central-station");
    expect((renamed.tests[0] as TripTest).request).toMatchObject({
      from: "central-station",
      to: "central-station",
    });
    expect(renamed.locations?.["central-station"]).toEqual({
      type: "coordinate",
      lat: 44.96,
      lon: -93.29,
    });
    expect(suite.locations?.origin).toBeDefined();
    expect(validateSuite(renamed)).toEqual([]);
    expect(() => renameLocation(suite, "origin", "destination")).toThrow(
      "already in use",
    );
  });
});
