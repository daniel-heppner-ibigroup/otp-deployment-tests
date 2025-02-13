package com.arcadis.otp_deployment_tests.tests;

import static org.opentripplanner.client.model.RequestMode.TRANSIT;
import static org.opentripplanner.client.model.RequestMode.WALK;

import com.arcadis.otp_deployment_tests.CoordinatesStore;
import com.arcadis.otp_deployment_tests.SmokeTestItinerary;
import com.arcadis.otp_deployment_tests.SmokeTestRequest;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentripplanner.client.OtpApiClient;
import org.opentripplanner.client.model.RequestMode;
import io.micrometer.core.instrument.Metrics;
import com.arcadis.otp_deployment_tests.TimedOtpApiClient;

@Tag("smoke-test")
@Tag("soundtransit")
@DisplayName("Sound Transit Smoke Tests")
public class SoundTransitSmokeTest {

  private static final String SUITE_NAME = "SoundTransit";
  private static final String OTP_WEB_URL =
    "https://sound-transit-otp.ibi-transit.com";
  public static final CoordinatesStore COORDS;

  static {
    COORDS = new CoordinatesStore();

    COORDS.add("LYNNWOOD_STA", 47.8154272, -122.2940715);
    COORDS.add("SODO", 47.5811, -122.3290);
    COORDS.add("CLYDE_HILL", 47.6316, -122.2173);
    COORDS.add("RONALD_BOG_PARK", 47.75601664, -122.33141);
    COORDS.add("ESPERANCE", 47.797330, -122.351560592);
    COORDS.add("SHORELINE", 47.7568, -122.3483);
    COORDS.add("MOUNTAINLAKE_TERRACE", 47.7900, -122.30379581);
    COORDS.add("OLIVE_WAY", 47.61309420, -122.336314916);
    COORDS.add("CASINO_RD", 47.92175434762228, -122.23896905562611);
    COORDS.add("MARYSVILLE", 48.05523331013222, -122.17763080699298);
  }

  private final TimedOtpApiClient apiClient = new TimedOtpApiClient(
    ZoneId.of("America/New_York"),
    OTP_WEB_URL,
    Metrics.globalRegistry,
    SUITE_NAME
  );
  private final Set<RequestMode> defaultModes = Set.of(TRANSIT, WALK);

  @Test
  @DisplayName("Test E Line bus from Olive Way to Shoreline")
  public void testELineBus() throws IOException {
    var request = new SmokeTestRequest(
      COORDS.get("OLIVE_WAY"),
      COORDS.get("SHORELINE"),
      defaultModes,
      apiClient
    );
    System.out.println("Trip Web Link: " + generateOtpWebLink(request));
    var plan = SmokeTestRequest.basicTripPlan(request);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withMode("BUS")
      .withRouteShortName("E Line")
      .withFarePrice(2.75f, "orca:regular", "orca:cash")
      .withFarePrice(1.0f, "orca:senior", "orca:cash")
      .withFarePrice(1.0f, "orca:special", "orca:electronic")
      .assertMatches();
  }

  @Test
  @DisplayName("Test 1 Line light rail from Ronald Bog Park to Olive Way")
  public void testLightRail() throws IOException {
    var request = new SmokeTestRequest(
      COORDS.get("RONALD_BOG_PARK"),
      COORDS.get("OLIVE_WAY"),
      defaultModes,
      apiClient
    );
    System.out.println("Trip Web Link: " + generateOtpWebLink(request));
    var plan = SmokeTestRequest.basicTripPlan(request);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withMode("TRAM")
      .withRouteShortName("1 Line")
      .withFarePrice(3.0f, "orca:regular", "orca:cash")
      .withFarePrice(1.0f, "orca:senior", "orca:cash")
      .withFarePrice(1.0f, "orca:special", "orca:electronic")
      .assertMatches();
  }

  @Test
  @DisplayName("Test multi-leg journey from Casino Road to Marysville")
  public void testMultiLegJourney() throws IOException {
    var request = new SmokeTestRequest(
      COORDS.get("CASINO_RD"),
      COORDS.get("MARYSVILLE"),
      defaultModes,
      apiClient
    );
    System.out.println("Trip Web Link: " + generateOtpWebLink(request));
    var plan = SmokeTestRequest.basicTripPlan(request);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withMode("BUS")
      .withRouteShortName("8")
      .withFarePrice(2.00f, "orca:regular", "orca:cash")
      .withFarePrice(1.00f, "orca:special", "orca:electronic")
      .hasLeg()
      .withMode("BUS")
      .withRouteShortName("201", "202")
      .withFarePrice(2.50f, "orca:regular", "orca:cash")
      .withFarePrice(0.25f, "orca:special", "orca:electronic")
      .assertMatches();
  }

  @Test
  @DisplayName("Verify all expected transit agencies are present")
  public void testAgencyList() throws IOException {
    var routes = apiClient.routes();
    var actualAgencies = routes
      .stream()
      .map(route -> route.agency().name())
      .distinct()
      .sorted()
      .toList();

    var expectedAgencies = Stream
      .of(
        "City of Seattle",
        "Community Transit",
        "Everett Transit",
        "Intercity Transit",
        "Kitsap Transit",
        "Metro Transit",
        "Pierce Transit",
        "Seattle Center Monorail",
        "Skagit Transit",
        "Sound Transit",
        "Washington State Ferries",
        "Whatcom Transportation Authority"
      )
      .sorted()
      .toList();

    Assertions.assertTrue(
      actualAgencies.containsAll(expectedAgencies) &&
      expectedAgencies.containsAll(actualAgencies),
      String.format(
        "Agency lists differ.%nExpected: %s%nActual: %s%nMissing from expected: %s%nMissing from actual: %s",
        expectedAgencies,
        actualAgencies,
        expectedAgencies
          .stream()
          .filter(a -> !actualAgencies.contains(a))
          .toList(),
        actualAgencies
          .stream()
          .filter(a -> !expectedAgencies.contains(a))
          .toList()
      )
    );
  }

  private static String generateOtpWebLink(SmokeTestRequest request) {
    // Create the variables object structure
    var variables = String.format(
      "{\"from\":{\"coordinates\":{\"latitude\":%.7f,\"longitude\":%.7f}}," +
      "\"to\":{\"coordinates\":{\"latitude\":%.7f,\"longitude\":%.7f}}," +
      "\"dateTime\":\"%s\"}",
      request.from().lat(),
      request.from().lon(),
      request.to().lat(),
      request.to().lon(),
      ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT)
    );

    // Double encode the variables to match the format
    String encodedVariables = URLEncoder.encode(
      URLEncoder.encode(variables, StandardCharsets.UTF_8),
      StandardCharsets.UTF_8
    );

    // Calculate center point for map view
    double centerLat = (request.from().lat() + request.to().lat()) / 2;
    double centerLon = (request.from().lon() + request.to().lon()) / 2;

    return String.format(
      "%s?variables=%s#10.03/%.4f/%.4f",
      OTP_WEB_URL,
      encodedVariables,
      centerLat,
      centerLon
    );
  }
}
