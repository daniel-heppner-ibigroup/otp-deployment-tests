package com.arcadis.otp_deployment_tests.tests;

import static org.opentripplanner.client.model.RequestMode.TRANSIT;
import static org.opentripplanner.client.model.RequestMode.WALK;

import com.arcadis.otp_deployment_tests.SmokeTestItinerary;
import com.arcadis.otp_deployment_tests.SmokeTestRequest;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentripplanner.client.model.RequestMode;
import com.arcadis.otp_deployment_tests.BaseOtpSmokeTest;

@Tag("smoke-test")
@Tag("soundtransit")
@DisplayName("Sound Transit Smoke Tests")
public class SoundTransitSmokeTest extends BaseOtpSmokeTest {

  public SoundTransitSmokeTest() {
    super("SoundTransit", "https://sound-transit-otp.ibi-transit.com");
  }

  @Override
  protected void initializeCoordinates() {
    // GeocodingService geocoder = new GeocodingService(COORDS); // Removed, geocoder is now inherited

    // Keep existing coordinates
    geocoder.storeCoordinate("LYNNWOOD_STA", 47.8154272, -122.2940715);
    geocoder.storeCoordinate("SODO", 47.5811, -122.3290);
    geocoder.storeCoordinate("CLYDE_HILL", 47.6316, -122.2173);
    geocoder.storeCoordinate("RONALD_BOG_PARK", 47.75601664, -122.33141);
    geocoder.storeCoordinate("ESPERANCE", 47.797330, -122.351560592);
    geocoder.storeCoordinate("SHORELINE", 47.7568, -122.3483);
    geocoder.storeCoordinate("MOUNTAINLAKE_TERRACE", 47.7900, -122.30379581);
    geocoder.storeCoordinate("OLIVE_WAY", 47.61309420, -122.336314916);
    geocoder.storeCoordinate("CASINO_RD", 47.92175434762228, -122.23896905562611);
    geocoder.storeCoordinate("MARYSVILLE", 48.05523331013222, -122.17763080699298);

    // Example of how to add new locations using geocoding:
    // geocoder.geocodeAndStore("NewLocation", "123 Main St, Seattle, WA 98101");
    
    // Example of using geocoding with source filtering:
    try {
      geocoder.geocodeAndStore("NORTHGATE_MALL", "Northgate Mall, Seattle", "openstreetmap");
      geocoder.geocodeAndStore("UW_STATION", "University of Washington Station", "openstreetmap");
    } catch (IOException e) {
      throw new RuntimeException("Failed to geocode addresses", e);
    }
  }

  private final Set<RequestMode> defaultModes = Set.of(TRANSIT, WALK);

  @Test
  @DisplayName("Test E Line bus from Olive Way to Shoreline")
  public void testELineBus() throws IOException {
    var request = new SmokeTestRequest(
      COORDS.get("OLIVE_WAY"),
      COORDS.get("SHORELINE"),
      defaultModes,
      this.apiClient
    );
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
      this.apiClient
    );
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
      this.apiClient
    );
    var plan = SmokeTestRequest.basicTripPlan(request);

    SmokeTestItinerary
      .from(plan)
      .hasLeg()
      .withMode("BUS")
      .withRouteShortName("8")
      .withFarePrice(2.00f, "orca:regular", "orca:cash")
      .withFarePrice(2.00f, "orca:regular", "orca:electronic")
      .withFarePrice(1.00f, "orca:special", "orca:electronic")
      .hasLeg()
      .withMode("BUS")
      .withRouteShortName("201", "202")
      .withFarePrice(2.50f, "orca:regular", "orca:cash")
      .withFarePrice(0.50f, "orca:regular", "orca:electronic")
      .withFarePrice(0.00f, "orca:special", "orca:electronic")
      .assertMatches();
  }

  @Test
  @DisplayName("Verify all expected transit agencies are present")
  public void testAgencyList() throws IOException {
    var routes = this.apiClient.routes();
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
}
