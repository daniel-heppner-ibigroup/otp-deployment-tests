package com.arcadis.otpsmoketests.tests;

import com.arcadis.otpsmoketests.BaseTestSuite;
import com.arcadis.otpsmoketests.ExecutableTestCase;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.opentripplanner.api.types.OptimizeType;
import org.opentripplanner.assertions.ItineraryExpectation;
import org.opentripplanner.assertions.ItineraryMatching;
import org.opentripplanner.assertions.PlanExpectation;
import org.opentripplanner.assertions.TransitLegExpectation;
import org.opentripplanner.assertions.TransitLegMatching;
import org.opentripplanner.client.model.Coordinate;
import org.opentripplanner.client.model.PlaceParameter;
import org.opentripplanner.client.model.RequestMode;
import org.opentripplanner.client.model.StopId;
import org.opentripplanner.client.parameters.InputBanned;
import org.opentripplanner.client.parameters.InputTriangle;
import org.opentripplanner.client.parameters.TripPlanParameters;
import org.opentripplanner.client.parameters.TripPlanParametersBuilder;

/**
 * Base class for suites whose independently reported test cases are declared in a JSON resource.
 *
 * <p>Test types define workflows; typed assertions delegate matching to the client library.
 */
public abstract class JsonTestSuite extends BaseTestSuite {

  private static final String SCHEMA_RESOURCE_PATH =
    "test-suites/schema/test-suite-v2.schema.json";
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
    this.testCases = definition.tests().stream().map(this::compile).toList();
  }

  /** JSON suites use coordinates declared in their resource rather than runtime geocoding. */
  @Override
  protected final void initializeCoordinates() {}

  @Override
  public final Collection<ExecutableTestCase> testCases() {
    return testCases;
  }

  private ExecutableTestCase compile(JsonTestDefinition test) {
    return switch (test) {
      case JsonTripPlanTest trip -> compileTrip(trip);
      case JsonAgencyListTest agencies -> new ExecutableTestCase(
        test.id(),
        test.name(),
        () -> {
          var actual = apiClient
            .routes()
            .stream()
            .map(route -> route.getAgency().getName())
            .collect(Collectors.toSet());
          var expected = Set.copyOf(agencies.agencies());
          if (!actual.equals(expected)) throw new AssertionError(
            "Expected agencies " + expected + "; actual " + actual
          );
        }
      );
    };
  }

  private ExecutableTestCase compileTrip(JsonTripPlanTest test) {
    final PlanExpectation expectation;
    try {
      expectation = test.expectation().toExpectation(defaultMatching());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
        "Invalid JSON test suite '" +
        resourcePath +
        "', test '" +
        test.id() +
        "': " +
        e.getMessage(),
        e
      );
    }
    return new ExecutableTestCase(
      test.id(),
      test.name(),
      () -> {
        var plan = apiClient.timedPlan(parameters(test.request()), test.id());
        assertPlan(plan, expectation);
      }
    );
  }

  private TripPlanParameters parameters(JsonTripRequest request) {
    var builder = TripPlanParameters
      .builder()
      .withFrom(location(request.from))
      .withTo(location(request.to))
      .withTime(
        request.date == null
          ? nextServiceTime(request.serviceDay, request.time, clock)
          : request.date.atTime(request.time)
      )
      .withModes(DEFAULT_MODES);
    if (definition.defaults() != null) configure(
      definition.defaults().request(),
      options -> options.applyTo(builder)
    );
    request.applyTo(builder);
    return builder.build();
  }

  private static <T> void configure(T value, Consumer<T> setter) {
    if (value != null) setter.accept(value);
  }

  private static <T> T or(T explicit, T suiteDefault, T fallback) {
    return explicit != null
      ? explicit
      : suiteDefault != null ? suiteDefault : fallback;
  }

  private TransitLegMatching defaultMatching() {
    return (
        definition.defaults() == null ||
        definition.defaults().transitLegMatching() == null
      )
      ? TransitLegMatching.EXACT_ORDERED
      : definition.defaults().transitLegMatching();
  }

  private PlaceParameter location(String locationId) {
    return definition.locations().get(locationId).toPlace(locationId);
  }

  private void validateSemantics() {
    Set<String> ids = new HashSet<>();
    for (var test : definition.tests()) {
      require(ids.add(test.id()), "duplicate test ID '" + test.id() + "'");
      if (test instanceof JsonTripPlanTest trip) {
        require(definition.locations() != null, "Trip tests require locations");
        require(
          definition.locations().containsKey(trip.request().from),
          test.id() + ".request.from references an unknown location"
        );
        require(
          definition.locations().containsKey(trip.request().to),
          test.id() + ".request.to references an unknown location"
        );
      }
    }
  }

  private void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(
      "Invalid JSON test suite '" + resourcePath + "': " + message
    );
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
        "Invalid JSON test suite '" + resourcePath + "': " + e.getMessage(),
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
    JsonRequestOptions request,
    TransitLegMatching transitLegMatching
  ) {}

  /** Flat Jackson input fields shared by suite defaults and individual requests. */
  private static class JsonRequestOptions {

    public Set<RequestMode> modes;
    public Integer numItineraries;
    public Boolean wheelchair;
    public TripPlanParameters.SearchDirection searchDirection;
    public Long searchWindowSeconds;
    public Double walkReluctance;
    public Double carReluctance;
    public Double bikeReluctance;
    public Double bikeWalkingReluctance;
    public Double walkSpeed;
    public OptimizeType optimize;
    public JsonTriangle triangle;
    public JsonBanned banned;
    public String pageCursor;

    void applyTo(TripPlanParametersBuilder builder) {
      configure(modes, builder::withModes);
      configure(numItineraries, builder::withNumberOfItineraries);
      configure(wheelchair, builder::withWheelchair);
      configure(searchDirection, builder::withSearchDirection);
      configure(
        searchWindowSeconds,
        seconds -> builder.withSearchWindow(Duration.ofSeconds(seconds))
      );
      configure(walkReluctance, builder::withWalkReluctance);
      configure(carReluctance, builder::withCarReluctance);
      configure(bikeReluctance, builder::withBikeReluctance);
      configure(bikeWalkingReluctance, builder::withBikeWalkingReluctance);
      configure(walkSpeed, builder::withWalkSpeed);
      configure(optimize, builder::withOptimize);
      configure(triangle, value -> builder.withTriangle(value.toInput()));
      configure(banned, value -> builder.withBanned(value.toInput()));
      configure(pageCursor, builder::withPageCursor);
    }
  }

  private record JsonTriangle(
    double safetyFactor,
    double slopeFactor,
    double timeFactor
  ) {
    InputTriangle toInput() {
      return new InputTriangle(safetyFactor, slopeFactor, timeFactor);
    }
  }

  private record JsonBanned(
    Set<String> routes,
    Set<String> agencies,
    Set<String> trips,
    Set<String> stops,
    Set<String> stopsHard
  ) {
    InputBanned toInput() {
      return new InputBanned(
        or(routes, null, Set.of()),
        or(agencies, null, Set.of()),
        or(trips, null, Set.of()),
        or(stops, null, Set.of()),
        or(stopsHard, null, Set.of())
      );
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(
    {
      @JsonSubTypes.Type(value = JsonCoordinate.class, name = "coordinate"),
      @JsonSubTypes.Type(value = JsonStopId.class, name = "stop-id"),
    }
  )
  private sealed interface JsonLocation permits JsonCoordinate, JsonStopId {
    PlaceParameter toPlace(String locationId);
  }

  private record JsonCoordinate(String name, double lat, double lon)
    implements JsonLocation {
    public PlaceParameter toPlace(String locationId) {
      return new Coordinate(
        lat,
        lon,
        name == null || name.isBlank() ? locationId : name
      );
    }
  }

  private record JsonStopId(String id) implements JsonLocation {
    public PlaceParameter toPlace(String locationId) {
      return new StopId(id);
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(
    {
      @JsonSubTypes.Type(value = JsonTripPlanTest.class, name = "trip-plan"),
      @JsonSubTypes.Type(
        value = JsonAgencyListTest.class,
        name = "agency-list"
      ),
    }
  )
  private sealed interface JsonTestDefinition
    permits JsonTripPlanTest, JsonAgencyListTest {
    String id();
    String name();
  }

  private record JsonTripPlanTest(
    String id,
    String name,
    JsonTripRequest request,
    JsonExpectation expectation
  )
    implements JsonTestDefinition {}

  private record JsonAgencyListTest(
    String id,
    String name,
    List<String> agencies
  )
    implements JsonTestDefinition {}

  private static final class JsonTripRequest extends JsonRequestOptions {

    public String from;
    public String to;
    public DayOfWeek serviceDay;
    public LocalDate date;
    public LocalTime time;
  }

  /** Schema validation guarantees exactly one non-null expression field. */
  private record JsonExpectation(
    JsonItineraryMatch itinerary,
    JsonItineraryCount itineraryCountBetween,
    List<JsonExpectation> allOf,
    List<JsonExpectation> anyOf
  ) {
    PlanExpectation toExpectation(TransitLegMatching mode) {
      if (itinerary != null) return itinerary.toExpectation(mode);
      if (
        itineraryCountBetween != null
      ) return itineraryCountBetween.toExpectation();
      if (allOf != null) return PlanExpectation.allOf(
        compileChildren(allOf, mode)
      );
      return PlanExpectation.anyOf(compileChildren(anyOf, mode));
    }

    private static PlanExpectation[] compileChildren(
      List<JsonExpectation> children,
      TransitLegMatching mode
    ) {
      return children
        .stream()
        .map(child -> child.toExpectation(mode))
        .toArray(PlanExpectation[]::new);
    }
  }

  private record JsonItineraryCount(int minimum, int maximum) {
    PlanExpectation toExpectation() {
      if (minimum > maximum) throw new IllegalArgumentException(
        "itineraryCountBetween.minimum must be <= maximum"
      );
      return PlanExpectation.itineraryCountBetween(minimum, maximum);
    }
  }

  private record JsonItineraryMatch(
    ItineraryMatching itineraryMatching,
    TransitLegMatching transitLegMatching,
    List<JsonTransitLeg> transitLegs,
    Long maxDurationSeconds
  ) {
    ItineraryExpectation toExpectation(TransitLegMatching defaultMatching) {
      var expectation = new ItineraryExpectation()
        .withItineraryMatching(
          or(itineraryMatching, null, ItineraryMatching.ANY)
        )
        .withTransitLegMatching(
          or(
            transitLegMatching,
            transitLegs == null
              ? TransitLegMatching.UNORDERED_SUBSET
              : defaultMatching,
            TransitLegMatching.EXACT_ORDERED
          )
        );
      if (transitLegs != null) transitLegs.forEach(leg ->
        expectation.withLeg(leg.toExpectation())
      );
      if (maxDurationSeconds != null) expectation.withMaxDuration(
        Duration.ofSeconds(maxDurationSeconds)
      );
      return expectation;
    }
  }

  private record JsonTransitLeg(
    String mode,
    List<String> routeShortNames,
    List<String> routeLongNames,
    Boolean interlinedWithPrevious,
    Long maxDurationSeconds,
    List<String> boardingStopCodes,
    List<String> boardingStopGtfsIds,
    List<String> alightingStopCodes,
    List<String> alightingStopGtfsIds,
    List<JsonFare> fares
  ) {
    TransitLegExpectation toExpectation() {
      var leg = new TransitLegExpectation();
      if (mode != null) leg.withMode(mode);
      apply(routeShortNames, leg::withRouteShortName);
      apply(routeLongNames, leg::withRouteLongName);
      apply(boardingStopCodes, leg::withBoardingStopCode);
      apply(boardingStopGtfsIds, leg::withBoardingStopGtfsId);
      apply(alightingStopCodes, leg::withAlightingStopCode);
      apply(alightingStopGtfsIds, leg::withAlightingStopGtfsId);
      if (interlinedWithPrevious != null) leg.withInterlinedWithPreviousLeg(
        interlinedWithPrevious
      );
      if (maxDurationSeconds != null) leg.withMaxDuration(
        Duration.ofSeconds(maxDurationSeconds)
      );
      if (fares != null) fares.forEach(f ->
        leg.withFarePrice(f.amount(), f.riderCategoryId(), f.mediumId())
      );
      return leg;
    }
  }

  private static void apply(
    List<String> values,
    java.util.function.Function<String[], TransitLegExpectation> setter
  ) {
    if (values != null) setter.apply(values.toArray(String[]::new));
  }

  private record JsonFare(
    float amount,
    String riderCategoryId,
    String mediumId
  ) {}
}
