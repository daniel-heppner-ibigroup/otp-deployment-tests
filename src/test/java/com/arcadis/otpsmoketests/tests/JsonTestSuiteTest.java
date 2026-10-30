package com.arcadis.otpsmoketests.tests;

import static org.junit.jupiter.api.Assertions.*;

import com.arcadis.otpsmoketests.runner.CustomTestRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.assertions.ItineraryAssertionError;

class JsonTestSuiteTest {

  private HttpServer server;
  private String baseUrl;
  private final List<String> queries = new ArrayList<>();
  private String response =
    """
    {"data":{"plan":{"itineraries":[{"legs":[{
      "from":{"name":"A","lat":44.9,"lon":-93.2},
      "to":{"name":"B","lat":44.95,"lon":-93.25},
      "startTime":1793637000000,"endTime":1793637300000,
      "mode":"WALK","duration":300,"distance":400,"fareProducts":[],
      "legGeometry":{"points":"_p~iF~ps|U_ulLnnqC_mqNvxq`@"}
    }]}]},"routes":[{"id":"r","shortName":"2","agency":{"name":"Metro Transit"}}]}}
    """;

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
      "/otp/gtfs/v1",
      exchange -> {
        queries.add(
          new ObjectMapper()
            .readTree(exchange.getRequestBody())
            .path("query")
            .asText()
        );
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
          output.write(bytes);
        }
      }
    );
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void typedAssertionsExecuteWithRequestOverrides() {
    var run = CustomTestRunner.runTestSuite(
      RunnerSuite.class,
      "test",
      baseUrl,
      "test"
    );
    assertEquals(3, run.getTestsSucceededCount());
    assertEquals(0, run.getTestsFailedCount());
    assertEquals(3, queries.size());
    assertModes(queries.getFirst(), "WALK");
    assertArguments(
      queries.getFirst(),
      Map.of(
        "numItineraries",
        "2",
        "wheelchair",
        "false",
        "arriveBy",
        "false",
        "date",
        "\"2026-11-02\"",
        "time",
        "\"08:30\""
      )
    );
    assertArguments(
      queries.getFirst(),
      Map.of(
        "searchWindow",
        "0",
        "walkReluctance",
        "1.2",
        "carReluctance",
        "1.3",
        "bikeReluctance",
        "1.4",
        "bikeWalkingReluctance",
        "1.5",
        "walkSpeed",
        "1.6",
        "optimize",
        "TRIANGLE",
        "pageCursor",
        "\"explicit-cursor\"",
        "fromPlace",
        "\"feed:123\""
      )
    );
    assertArguments(
      queries.getFirst(),
      Map.of("safetyFactor", "0.2", "slopeFactor", "0.3", "timeFactor", "0.5")
    );
    assertTrue(queries.getFirst().matches("(?s).*banned\\s*:\\s*\\{\\s*}.*"));
    assertModes(queries.get(1), "BUS", "WALK");
    assertArguments(
      queries.get(1),
      Map.of(
        "numItineraries",
        "5",
        "wheelchair",
        "true",
        "arriveBy",
        "true",
        "date",
        "\"2026-10-29\"",
        "time",
        "\"09:00\""
      )
    );
    assertArguments(
      queries.get(1),
      Map.of(
        "searchWindow",
        "1800",
        "walkReluctance",
        "2.0",
        "carReluctance",
        "3.0",
        "bikeReluctance",
        "4.0",
        "bikeWalkingReluctance",
        "5.0",
        "walkSpeed",
        "1.1",
        "optimize",
        "SAFE",
        "pageCursor",
        "\"default-cursor\"",
        "toPlace",
        "\"b::44.95,-93.25\""
      )
    );
    assertArguments(
      queries.get(1),
      Map.of("safetyFactor", "0.5", "slopeFactor", "0.2", "timeFactor", "0.3")
    );
    assertArguments(
      queries.get(1),
      Map.of(
        "routes",
        "\"feed:r\"",
        "agencies",
        "\"feed:a\"",
        "trips",
        "\"feed:t\"",
        "stops",
        "\"feed:s\"",
        "stopsHard",
        "\"feed:h\""
      )
    );
  }

  @Test
  void runnerContinuesAfterFailuresAndRetainsCountDiagnostics()
    throws Exception {
    var document = new ObjectMapper().readTree(response);
    ((ObjectNode) document.path("data").path("plan")).putArray("itineraries");
    response = document.toString();
    var run = CustomTestRunner.runTestSuite(
      RunnerSuite.class,
      "test",
      baseUrl,
      "test"
    );
    assertEquals(3, run.getTestsFoundCount());
    assertEquals(2, run.getTestsFailedCount());
    assertEquals(1, run.getTestsSucceededCount());
    assertEquals(3, queries.size());
    var failure = assertInstanceOf(
      ItineraryAssertionError.class,
      run.testResults().getFirst().exception()
    );
    assertFalse(failure.result().matches());
    assertTrue(
      failure
        .result()
        .children()
        .getFirst()
        .description()
        .contains("Itinerary count 0")
    );
  }

  @ParameterizedTest
  @MethodSource("invalidDefinitions")
  void invalidDefinitionsFailBeforeSendingRequests(
    String resource,
    boolean schemaAccepts,
    String message
  ) throws Exception {
    var schema = SchemaRegistry
      .withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
      .getSchema(
        resourceText("test-suites/schema/test-suite-v2.schema.json"),
        InputFormat.JSON
      );
    assertEquals(
      schemaAccepts,
      schema
        .validate(
          resourceText("test-suites/" + resource + ".json"),
          InputFormat.JSON
        )
        .isEmpty()
    );
    var failure = assertThrows(
      IllegalArgumentException.class,
      () -> new Suite(baseUrl, resource)
    );
    assertTrue(failure.getMessage().contains(resource), failure.getMessage());
    assertTrue(failure.getMessage().contains(message), failure.getMessage());
    assertTrue(queries.isEmpty());
  }

  static Stream<Arguments> invalidDefinitions() {
    return Stream.of(
      Arguments.of("unknown-assertion", false, "Invalid JSON test suite"),
      Arguments.of("misspelled-field", false, "Invalid JSON test suite"),
      Arguments.of("duplicate-id", true, "duplicate test ID"),
      Arguments.of("unknown-location", true, "unknown location"),
      Arguments.of("invalid-count-range", true, "minimum must be <= maximum"),
      Arguments.of("invalid-date", true, "Invalid date"),
      Arguments.of("conflicting-date", false, "Invalid JSON test suite"),
      Arguments.of("int-overflow", false, "Invalid JSON test suite"),
      Arguments.of("request-int-overflow", false, "Invalid JSON test suite"),
      Arguments.of("duration-overflow", false, "Invalid JSON test suite"),
      Arguments.of("multiple-expressions", false, "Invalid JSON test suite"),
      Arguments.of("empty-composition", false, "Invalid JSON test suite"),
      Arguments.of("unknown-default", false, "Invalid JSON test suite"),
      Arguments.of("invalid-location", false, "Invalid JSON test suite")
    );
  }

  @ParameterizedTest
  @MethodSource("itineraryMatchingCases")
  void jsonMatchingModesUseLibrarySemantics(
    int count,
    int duration,
    boolean any,
    boolean all,
    boolean none
  ) throws Throwable {
    var mapper = new ObjectMapper();
    var document = mapper.readTree(response);
    var itineraries = (com.fasterxml.jackson.databind.node.ArrayNode) document
      .path("data")
      .path("plan")
      .path("itineraries");
    var itinerary = itineraries.get(0).deepCopy();
    itineraries.removeAll();
    for (int i = 0; i < count; i++) {
      var copy = itinerary.deepCopy();
      var leg = (ObjectNode) copy.path("legs").get(0);
      leg.put("duration", i == 0 ? 300 : duration);
      leg.put("endTime", 1793637000000L + (i == 0 ? 300 : duration) * 1000L);
      itineraries.add(copy);
    }
    response = mapper.writeValueAsString(document);
    var failure = assertThrows(
      ItineraryAssertionError.class,
      () ->
        new Suite(baseUrl, "matching-modes")
          .testCases()
          .iterator()
          .next()
          .execute()
    );
    var children = failure.result().children();
    assertEquals(
      List.of(any, all, none),
      children.stream().map(result -> result.matches()).toList()
    );
    assertTrue(
      children
        .stream()
        .allMatch(result -> result.itineraryResults().size() == count)
    );
  }

  static Stream<Arguments> itineraryMatchingCases() {
    return Stream.of(
      Arguments.of(1, 300, true, true, false),
      Arguments.of(2, 900, true, false, false),
      Arguments.of(0, 0, false, false, true)
    );
  }

  private static void assertArguments(
    String query,
    Map<String, String> arguments
  ) {
    arguments.forEach((name, value) ->
      assertTrue(
        query.matches(
          "(?s).*\\b" +
          name +
          "\\s*:\\s*" +
          Pattern.quote(value) +
          "(?![\\w.]).*"
        ),
        name + " must be " + value + " in " + query
      )
    );
  }

  private static void assertModes(String query, String... expected) {
    var modes = Pattern
      .compile("\\bmode\\s*:\\s*([A-Z_]+)\\b")
      .matcher(query)
      .results()
      .map(match -> match.group(1))
      .sorted()
      .toList();
    assertEquals(List.of(expected), modes);
  }

  private static String resourceText(String path) throws Exception {
    try (
      var input = JsonTestSuiteTest.class.getClassLoader()
        .getResourceAsStream(path)
    ) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void migratedMinneapolisSuiteLoadsWithoutNetworkRequests() {
    assertFalse(
      new MetroTransitMinneapolisTestSuite(baseUrl, "test")
        .testCases()
        .isEmpty()
    );
    assertTrue(queries.isEmpty());
  }

  @Test
  void emptyResultsCanBeRequiredExplicitly() {
    response = "{\"data\":{\"plan\":{\"itineraries\":[]}}}";
    assertDoesNotThrow(() ->
      new Suite(baseUrl, "empty-results")
        .testCases()
        .iterator()
        .next()
        .execute()
    );
  }

  public static final class RunnerSuite extends JsonTestSuite {

    public RunnerSuite(String url, String deploymentName) {
      super(
        deploymentName,
        url,
        "http://127.0.0.1",
        44.9,
        -93.2,
        ZoneId.of("America/Chicago"),
        "test-suites/extensions.json",
        Clock.fixed(
          Instant.parse("2026-10-22T15:00:00Z"),
          ZoneId.of("America/Chicago")
        )
      );
    }
  }

  private static final class Suite extends JsonTestSuite {

    Suite(String url, String resource) {
      super(
        "test",
        url,
        "http://127.0.0.1",
        44.9,
        -93.2,
        ZoneId.of("America/Chicago"),
        "test-suites/" + resource + ".json",
        Clock.fixed(
          Instant.parse("2026-10-22T15:00:00Z"),
          ZoneId.of("America/Chicago")
        )
      );
    }
  }
}
