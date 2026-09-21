package com.arcadis.otpsmoketests.tests;

import com.arcadis.otpsmoketests.BaseTestSuite;
import com.arcadis.otpsmoketests.ExecutableTestCase;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.opentripplanner.assertions.ItineraryAssertions;
import org.opentripplanner.client.model.Coordinate;
import org.opentripplanner.client.model.RequestMode;
import org.opentripplanner.client.parameters.TripPlanParameters;

/**
 * Base class for suites whose independently reported test cases are declared in a JSON resource.
 *
 * <p>The JSON schema is intentionally limited to deterministic transit itinerary checks. Tests
 * requiring custom behavior should remain ordinary Java {@code @Test} methods.
 */
public abstract class JsonTestSuite extends BaseTestSuite {

  private static final String SCHEMA_RESOURCE_PATH =
    "test-suites/schema/test-suite-v1.schema.json";
  private static final Set<RequestMode> DEFAULT_MODES = Set.of(
    RequestMode.TRANSIT,
    RequestMode.WALK
  );
  private static final ObjectMapper MAPPER = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private static final Schema SCHEMA = loadSchema();

  private final Clock clock;
  private final String resourcePath;
  private final JsonSuiteDefinition definition;
  private final List<ExecutableTestCase> testCases;

  protected JsonTestSuite(
    String suiteName,
    String otpWebUrl,
    String peliasBaseUrl,
    double focusLat,
    double focusLon,
    ZoneId timeZone,
    String resourcePath
  ) {
    this(
      suiteName,
      otpWebUrl,
      peliasBaseUrl,
      focusLat,
      focusLon,
      timeZone,
      resourcePath,
      Clock.system(timeZone)
    );
  }

  protected JsonTestSuite(
    String suiteName,
    String otpWebUrl,
    String peliasBaseUrl,
    double focusLat,
    double focusLon,
    ZoneId timeZone,
    String resourcePath,
    Clock clock
  ) {
    super(suiteName, otpWebUrl, peliasBaseUrl, focusLat, focusLon, timeZone);
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.resourcePath = normalizeResourcePath(resourcePath);
    this.definition = loadDefinition(this.resourcePath);
    validateSemantics();
    this.testCases =
      definition
        .tests()
        .stream()
        .map(test ->
          new ExecutableTestCase(test.id(), test.name(), () -> execute(test))
        )
        .toList();
  }

  /** JSON suites use coordinates declared in their resource rather than runtime geocoding. */
  @Override
  protected final void initializeCoordinates() {}

  @Override
  public final Collection<ExecutableTestCase> testCases() {
    return testCases;
  }

  private void execute(JsonTestDefinition test) throws IOException {
    JsonTripRequest request = test.request();
    var parameters = TripPlanParameters
      .builder()
      .withFrom(coordinate(request.from()))
      .withTo(coordinate(request.to()))
      .withTime(nextServiceTime(request.serviceDay(), request.time(), clock))
      .withSearchDirection(
        request.searchDirection() == null
          ? TripPlanParameters.SearchDirection.DEPART_AT
          : request.searchDirection()
      )
      .withModes(requestModes())
      .build();

    var plan = apiClient.timedPlan(parameters, test.id());

    // Build up the assertions based on the JSON definitions
    var assertions = new ItineraryAssertions();
    if (strictTransitMatching()) {
      assertions.withStrictTransitMatching();
    }
    for (JsonExpectedTransitLeg leg : test.expect().transitLegs()) {
      assertions
        .hasLeg()
        .withMode(leg.mode())
        .withRouteShortName(leg.routes().toArray(String[]::new));
      if (leg.isInterlinedWithPrevious()) {
        assertions.interlinedWithPreviousLeg();
      }
    }
    assertions.assertMatches(plan);
  }

  private Coordinate coordinate(String locationId) {
    JsonLocation location = definition.locations().get(locationId);
    String name = location.name() == null || location.name().isBlank()
      ? locationId
      : location.name();
    return new Coordinate(location.lat(), location.lon(), name);
  }

  private Set<RequestMode> requestModes() {
    JsonTestDefaults defaults = definition.defaults();
    return defaults == null || defaults.modes() == null
      ? DEFAULT_MODES
      : Set.copyOf(defaults.modes());
  }

  private boolean strictTransitMatching() {
    JsonTestDefaults defaults = definition.defaults();
    return (
      defaults == null ||
      defaults.strictTransitMatching() == null ||
      defaults.strictTransitMatching()
    );
  }

  private void validateSemantics() {
    Set<String> ids = new HashSet<>();
    for (int index = 0; index < definition.tests().size(); index++) {
      JsonTestDefinition test = definition.tests().get(index);
      String path = "tests[" + index + "]";
      require(ids.add(test.id()), "duplicate test ID '" + test.id() + "'");
      require(
        definition.locations().containsKey(test.request().from()),
        path + ".request.from references an unknown location"
      );
      require(
        definition.locations().containsKey(test.request().to()),
        path + ".request.to references an unknown location"
      );
    }
  }

  private void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(
        "Invalid JSON test suite '" + resourcePath + "': " + message
      );
    }
  }

  private static JsonSuiteDefinition loadDefinition(String resourcePath) {
    String document = readResource(resourcePath, "JSON test suite");
    try {
      var errors = SCHEMA.validate(document, InputFormat.JSON);
      if (!errors.isEmpty()) {
        String details = errors
          .stream()
          .map(Object::toString)
          .sorted()
          .collect(Collectors.joining("; "));
        throw new IllegalArgumentException(
          "Invalid JSON test suite '" + resourcePath + "': " + details
        );
      }
      return MAPPER.readValue(document, JsonSuiteDefinition.class);
    } catch (IOException e) {
      throw new IllegalArgumentException(
        "Could not load JSON test suite resource: " + resourcePath,
        e
      );
    }
  }

  private static Schema loadSchema() {
    String schemaDocument = readResource(
      SCHEMA_RESOURCE_PATH,
      "JSON test suite schema"
    );
    try {
      SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
        SpecificationVersion.DRAFT_2020_12
      );
      return registry.getSchema(schemaDocument, InputFormat.JSON);
    } catch (RuntimeException e) {
      throw new IllegalStateException(
        "Could not load JSON test suite schema: " + SCHEMA_RESOURCE_PATH,
        e
      );
    }
  }

  private static String readResource(String resourcePath, String description) {
    ClassLoader classLoader = JsonTestSuite.class.getClassLoader();
    try (InputStream input = classLoader.getResourceAsStream(resourcePath)) {
      if (input == null) {
        throw new IllegalArgumentException(
          description + " resource not found: " + resourcePath
        );
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalArgumentException(
        "Could not load " + description + " resource: " + resourcePath,
        e
      );
    }
  }

  private static String normalizeResourcePath(String resourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath must not be null");
    String normalized = resourcePath.startsWith("/")
      ? resourcePath.substring(1)
      : resourcePath;
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("resourcePath must not be blank");
    }
    return normalized;
  }

  private record JsonSuiteDefinition(
    int schemaVersion,
    JsonTestDefaults defaults,
    Map<String, JsonLocation> locations,
    List<JsonTestDefinition> tests
  ) {}

  private record JsonTestDefaults(
    Set<RequestMode> modes,
    Boolean strictTransitMatching
  ) {}

  private record JsonLocation(String name, Double lat, Double lon) {}

  private record JsonTestDefinition(
    String id,
    String name,
    JsonTripRequest request,
    JsonExpectation expect
  ) {}

  private record JsonTripRequest(
    String from,
    String to,
    DayOfWeek serviceDay,
    LocalTime time,
    TripPlanParameters.SearchDirection searchDirection
  ) {}

  private record JsonExpectation(List<JsonExpectedTransitLeg> transitLegs) {}

  private record JsonExpectedTransitLeg(
    String mode,
    List<String> routes,
    Boolean interlinedWithPrevious
  ) {
    private boolean isInterlinedWithPrevious() {
      return interlinedWithPrevious;
    }
  }
}
