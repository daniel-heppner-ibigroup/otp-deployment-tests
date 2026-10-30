import Ajv2020 from "ajv/dist/2020";
import {
  compareNumber,
  isLosslessNumber,
  isNumber,
  isSafeNumber,
  LosslessNumber,
  parse,
  splitNumber,
  stringify,
} from "lossless-json";
import schema from "../../resources/test-suites/schema/test-suite-v2.schema.json";

export type Json =
  string | number | LosslessNumber | boolean | null | Json[] | Obj;
export type Obj = { [key: string]: Json };
export type Coordinate = {
  type: "coordinate";
  name?: string;
  lat: number;
  lon: number;
};
export type Location = Coordinate | { type: "stop-id"; id: string };
export type TripTest = {
  type: "trip-plan";
  id: string;
  name: string;
  request: Obj;
  expectation: Obj;
};
export type AgencyTest = {
  type: "agency-list";
  id: string;
  name: string;
  agencies: string[];
};
export type SuiteTest = TripTest | AgencyTest;
export type Suite = {
  schemaVersion: 2;
  defaults?: { request?: Obj; transitLegMatching?: string };
  locations?: Record<string, Location>;
  tests: SuiteTest[];
};
export type Issue = { path: string; message: string };
export type Schema = {
  $ref?: string;
  type?: string;
  const?: Json;
  enum?: string[];
  properties?: Record<string, Schema>;
  required?: string[];
  items?: Schema;
  minimum?: number;
  maximum?: number;
  exclusiveMinimum?: number;
  minItems?: number;
  description?: string;
  oneOf?: Schema[];
};

const definitions: Record<string, Schema> = schema.$defs;
const validate = new Ajv2020({ allErrors: true, strict: false }).compile(
  schema,
);

export function definition(name: string): Schema {
  return resolveSchema(definitions[name]);
}

export function resolveSchema(value: Schema): Schema {
  if (!value.$ref) return value;
  return {
    ...resolveSchema(definitions[value.$ref.split("/").at(-1)!]),
    ...value,
    $ref: undefined,
  };
}

export function blankSuite(): Suite {
  return {
    schemaVersion: 2,
    defaults: { request: { modes: ["TRANSIT", "WALK"] } },
    locations: {},
    tests: [],
  };
}

export function nextKey(prefix: string, existing: string[]): string {
  let index = 1;
  while (existing.includes(`${prefix}-${index}`)) index++;
  return `${prefix}-${index}`;
}

export function newTest(type: SuiteTest["type"], suite: Suite): SuiteTest {
  const id = nextKey(
    type === "trip-plan" ? "trip" : "agencies",
    suite.tests.map((test) => test.id),
  );
  if (type === "agency-list")
    return { type, id, name: "Agency list", agencies: [] };
  const locations = Object.keys(suite.locations ?? {});
  return {
    type,
    id,
    name: "New trip plan",
    request: {
      from: locations[0] ?? "",
      to: locations[1] ?? locations[0] ?? "",
      serviceDay: "MONDAY",
      time: "08:00",
    },
    expectation: { itinerary: {} },
  };
}

export function replaceProperty(
  object: Obj,
  key: string,
  value: Json | undefined,
): Obj {
  const result = { ...object };
  if (value === undefined) delete result[key];
  else result[key] = value;
  return result;
}

export function serialize(suite: Suite): string {
  return formatJson(suite) + "\n";
}

export function formatJson(value: unknown): string {
  return stringify(value, null, 2)!;
}

function parseNumber(source: string): number | LosslessNumber {
  return isSafeNumber(source) ? Number(source) : new LosslessNumber(source);
}

function reviveCoordinate(key: string, value: unknown): unknown {
  return (key === "lat" || key === "lon") && isLosslessNumber(value)
    ? Number(value.value)
    : value;
}

export function numericInput(source: string): number | LosslessNumber | null {
  return isNumber(source) ? parseNumber(source) : null;
}

export function cloneValue<T>(value: T): T {
  return parse(stringify(value)!, reviveCoordinate, parseNumber) as T;
}

function durationIssues(value: unknown, path = ""): Issue[] {
  if (!value || typeof value !== "object" || isLosslessNumber(value)) return [];
  return Object.entries(value).flatMap(([key, child]) => {
    const childPath = `${path}/${key}`;
    const integerMaximums: Record<string, string> = {
      searchWindowSeconds: "9223372036854775807",
      maxDurationSeconds: "9223372036854775807",
      numItineraries: "2147483647",
      minimum: "2147483647",
      maximum: "2147483647",
    };
    if (!integerMaximums[key]) return durationIssues(child, childPath);
    if (typeof child !== "number" && !isLosslessNumber(child)) return [];
    const source = String(child);
    if (!isNumber(source)) return [];
    const split = splitNumber(source);
    if (
      split.exponent < split.digits.length - 1 ||
      compareNumber(source, integerMaximums[key]) > 0
    ) {
      const label =
        integerMaximums[key].length > 10 ? "whole seconds" : "a whole number";
      return [
        {
          path: childPath,
          message: `Use ${label} no greater than ${integerMaximums[key]} (Java ${label === "whole seconds" ? "long" : "int"} maximum).`,
        },
      ];
    }
    return [];
  });
}

export function parseSuite(text: string): Suite {
  // Preserve large Java longs as exact JSON numbers through edits and exports.
  const value: unknown = parse(text, reviveCoordinate, parseNumber);
  if (!validate(JSON.parse(stringify(value)!))) {
    const errors = (validate.errors ?? [])
      .map((error) => `${error.instancePath || "/"} ${error.message}`)
      .slice(0, 8);
    throw new Error(
      `This JSON does not match suite schema v2:\n${errors.join("\n")}`,
    );
  }
  const durations = durationIssues(value);
  if (durations.length)
    throw new Error(
      durations.map((issue) => `${issue.path}: ${issue.message}`).join("\n"),
    );
  return value as Suite;
}

function schemaIssues(suite: Suite): Issue[] {
  if (validate(JSON.parse(serialize(suite)))) return [];
  // Each test's matching oneOf branch gives actionable errors; omit irrelevant branches.
  return (validate.errors ?? [])
    .filter((error) => !["oneOf", "if"].includes(error.keyword))
    .filter((error) => {
      const match = error.schemaPath.match(
        /^#\/\$defs\/(tripPlanTest|agencyListTest)/,
      );
      if (!match) return true;
      const index = Number(error.instancePath.split("/")[2]);
      return (
        suite.tests[index]?.type ===
        (match[1] === "tripPlanTest" ? "trip-plan" : "agency-list")
      );
    })
    .map((error) => ({
      path: error.instancePath || "/",
      message:
        error.keyword === "required"
          ? `Add ${String(error.params.missingProperty)}.`
          : `${error.message}${error.params.allowedValues ? `: ${error.params.allowedValues.join(", ")}` : ""}`,
    }));
}

function countIssues(expression: Obj, path: string): Issue[] {
  const range = expression.itineraryCountBetween as Obj | undefined;
  const minimum = String(range?.minimum);
  const maximum = String(range?.maximum);
  if (
    isNumber(minimum) &&
    isNumber(maximum) &&
    compareNumber(minimum, maximum) > 0
  ) {
    return [
      {
        path: `${path}/itineraryCountBetween`,
        message: "Minimum itinerary count must not exceed maximum.",
      },
    ];
  }
  const group = expression.allOf ?? expression.anyOf;
  if (!Array.isArray(group)) return [];
  const key = expression.allOf ? "allOf" : "anyOf";
  return group.flatMap((child, index) =>
    countIssues(child as Obj, `${path}/${key}/${index}`),
  );
}

function validDate(value: string): boolean {
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(0);
  date.setUTCFullYear(year, month - 1, day);
  return (
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
  );
}

function tripIssues(test: TripTest, index: number, suite: Suite): Issue[] {
  const issues: Issue[] = [];
  for (const end of ["from", "to"]) {
    const location = String(test.request[end]);
    if (!Object.hasOwn(suite.locations ?? {}, location)) {
      issues.push({
        path: `/tests/${index}/request/${end}`,
        message: `Location “${location}” is missing. Choose a saved location.`,
      });
    }
  }
  if (typeof test.request.date === "string" && !validDate(test.request.date)) {
    issues.push({
      path: `/tests/${index}/request/date`,
      message: "Enter a real calendar date.",
    });
  }
  return [
    ...issues,
    ...countIssues(test.expectation, `/tests/${index}/expectation`),
  ];
}

export function validateSuite(suite: Suite): Issue[] {
  const issues = [...schemaIssues(suite), ...durationIssues(suite)];
  const ids = new Set<string>();
  suite.tests.forEach((test, index) => {
    if (ids.has(test.id))
      issues.push({
        path: `/tests/${index}/id`,
        message: `Test ID “${test.id}” is already used.`,
      });
    ids.add(test.id);
    if (test.type === "trip-plan")
      issues.push(...tripIssues(test, index, suite));
  });
  return issues;
}

export function locationUses(suite: Suite, key: string): number {
  return suite.tests.filter(
    (test) =>
      test.type === "trip-plan" &&
      (test.request.from === key || test.request.to === key),
  ).length;
}

export function renameLocation(
  suite: Suite,
  oldKey: string,
  newKey: string,
): Suite {
  if (!newKey.trim()) throw new Error("A location key cannot be blank.");
  if (newKey !== oldKey && Object.hasOwn(suite.locations ?? {}, newKey))
    throw new Error("That location key is already in use.");
  const locations = Object.fromEntries(
    Object.entries(suite.locations ?? {}).map(([key, location]) => [
      key === oldKey ? newKey : key,
      location,
    ]),
  );
  const tests = suite.tests.map((test) => {
    if (test.type !== "trip-plan") return test;
    return {
      ...test,
      request: {
        ...test.request,
        from: test.request.from === oldKey ? newKey : test.request.from,
        to: test.request.to === oldKey ? newKey : test.request.to,
      },
    };
  });
  return { ...suite, locations, tests };
}
